package dev.chatmoderation.semantic.openai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.chatmoderation.core.model.ModerationReason;
import dev.chatmoderation.semantic.SemanticModerationProvider;
import dev.chatmoderation.semantic.SemanticModerationResult;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Synchronous Responses API provider using the frozen Phase 3.13 Luna policy prompt. */
public final class OpenAiLunaModerationProvider implements SemanticModerationProvider {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int MAX_OUTPUT_TOKENS = 128;

    private final OpenAiLunaProviderConfig config;
    private final Transport transport;

    public OpenAiLunaModerationProvider(OpenAiLunaProviderConfig config) {
        this(config, new JdkHttpTransport(config));
    }

    OpenAiLunaModerationProvider(OpenAiLunaProviderConfig config, Transport transport) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.transport = Objects.requireNonNull(transport, "transport must not be null");
    }

    @Override
    public SemanticModerationResult moderate(String message) {
        Objects.requireNonNull(message, "message must not be null");
        try {
            TransportResponse response = transport.send(createRequestBody(message));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return unavailable(SemanticModerationResult.Status.ERROR);
            }
            return parseResponse(response.body());
        } catch (HttpTimeoutException exception) {
            return unavailable(SemanticModerationResult.Status.TIMEOUT);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return unavailable(SemanticModerationResult.Status.ERROR);
        } catch (IOException | RuntimeException exception) {
            return unavailable(SemanticModerationResult.Status.ERROR);
        }
    }

    String createRequestBody(String message) throws JsonProcessingException {
        ObjectNode root = JSON.createObjectNode();
        root.put("model", config.model());
        root.put("store", false);
        root.put("max_output_tokens", MAX_OUTPUT_TOKENS);
        root.set("reasoning", JSON.createObjectNode().put("effort", "none"));

        ArrayNode input = root.putArray("input");
        input.addObject().put("role", "system").put("content", OpenAiLunaPolicyPrompt.INSTRUCTIONS);
        input.addObject().put("role", "user")
                .put("content", "다음 메시지를 판정하라:\n<message>\n" + message + "\n</message>");

        ObjectNode format = root.putObject("text").putObject("format");
        format.put("type", "json_schema");
        format.put("name", "chat_moderation_decision");
        format.put("strict", true);
        ObjectNode schema = format.putObject("schema");
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        properties.putObject("decision").put("type", "string")
                .putArray("enum").add("ALLOW").add("BLOCK");
        ObjectNode reason = properties.putObject("reason");
        reason.putArray("type").add("string").add("null");
        reason.putArray("enum")
                .add("PROFANITY").add("SEXUAL_CONTENT").add("IMPLICIT_INSULT")
                .add("FAMILY_INSULT").add("ADVERTISEMENT").add("OTHER").addNull();
        properties.putObject("confidence").put("type", "number")
                .put("minimum", 0.0).put("maximum", 1.0);
        schema.putArray("required").add("decision").add("reason").add("confidence");
        schema.put("additionalProperties", false);
        return JSON.writeValueAsString(root);
    }

    private SemanticModerationResult parseResponse(String body) throws JsonProcessingException {
        JsonNode response = JSON.readTree(body);
        if (!"completed".equals(response.path("status").asText())) {
            throw new IllegalArgumentException("response did not complete");
        }
        JsonNode decisionJson = JSON.readTree(findOutputText(response.path("output")));
        String decisionValue = requiredText(decisionJson, "decision");
        double confidence = decisionJson.path("confidence").asDouble(Double.NaN);
        if (!Double.isFinite(confidence) || confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("invalid confidence");
        }
        JsonNode reason = decisionJson.get("reason");
        return switch (decisionValue) {
            case "ALLOW" -> {
                if (reason != null && !reason.isNull()) {
                    throw new IllegalArgumentException("ALLOW must not include a reason");
                }
                yield SemanticModerationResult.allow(confidence, providerName());
            }
            case "BLOCK" -> {
                if (reason == null || reason.isNull()) {
                    throw new IllegalArgumentException("BLOCK must include a reason");
                }
                yield SemanticModerationResult.block(
                        List.of(mapReason(reason.asText())), confidence, providerName());
            }
            default -> throw new IllegalArgumentException("unsupported decision");
        };
    }

    private String findOutputText(JsonNode output) {
        if (!output.isArray()) throw new IllegalArgumentException("missing output array");
        for (JsonNode item : output) {
            if (!"message".equals(item.path("type").asText())) continue;
            for (JsonNode content : item.path("content")) {
                if ("output_text".equals(content.path("type").asText())) {
                    return requiredText(content, "text");
                }
            }
        }
        throw new IllegalArgumentException("missing structured output text");
    }

    private String requiredText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new IllegalArgumentException("missing text field: " + field);
        }
        return value.asText();
    }

    private ModerationReason mapReason(String reason) {
        return switch (reason.toUpperCase(Locale.ROOT)) {
            case "PROFANITY" -> ModerationReason.PROFANITY;
            case "SEXUAL_CONTENT" -> ModerationReason.SEXUAL_CONTENT;
            case "IMPLICIT_INSULT", "FAMILY_INSULT" -> ModerationReason.HARASSMENT;
            case "ADVERTISEMENT" -> ModerationReason.SPAM;
            case "OTHER" -> ModerationReason.OTHER;
            default -> throw new IllegalArgumentException("unsupported reason");
        };
    }

    private SemanticModerationResult unavailable(SemanticModerationResult.Status status) {
        return SemanticModerationResult.unavailable(status, providerName());
    }

    private String providerName() {
        return "openai-responses:" + config.model();
    }

    @FunctionalInterface
    interface Transport {
        TransportResponse send(String requestBody) throws IOException, InterruptedException;
    }

    record TransportResponse(int statusCode, String body) {
    }

    private static final class JdkHttpTransport implements Transport {
        private final OpenAiLunaProviderConfig config;
        private final HttpClient client;

        private JdkHttpTransport(OpenAiLunaProviderConfig config) {
            this.config = config;
            this.client = HttpClient.newBuilder().connectTimeout(config.timeout()).build();
        }

        @Override
        public TransportResponse send(String requestBody) throws IOException, InterruptedException {
            HttpRequest request = HttpRequest.newBuilder(config.endpoint())
                    .timeout(config.timeout())
                    .header("Authorization", "Bearer " + config.apiKey())
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = client.send(
                    request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new TransportResponse(response.statusCode(), response.body());
        }
    }
}
