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

public final class OpenAiSemanticModerationProvider implements SemanticModerationProvider {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int MAX_OUTPUT_TOKENS = 128;

    private final OpenAiSemanticProviderConfig config;
    private final OpenAiTransport transport;
    private final OpenAiProviderTelemetry telemetry = new OpenAiProviderTelemetry();

    public OpenAiSemanticModerationProvider(OpenAiSemanticProviderConfig config) {
        this(config, new JdkHttpTransport(config));
    }

    OpenAiSemanticModerationProvider(
            OpenAiSemanticProviderConfig config,
            OpenAiTransport transport
    ) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.transport = Objects.requireNonNull(transport, "transport must not be null");
    }

    @Override
    public SemanticModerationResult moderate(String message) {
        Objects.requireNonNull(message, "message must not be null");
        telemetry.recordRequest();
        long startedAt = System.nanoTime();
        try {
            TransportResponse response = transport.send(createRequestBody(message));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                telemetry.recordError();
                return unknown(SemanticModerationResult.Status.ERROR);
            }
            ParsedResponse parsed = parseResponse(response.body());
            telemetry.recordSuccess(
                    parsed.inputTokens(),
                    parsed.cachedInputTokens(),
                    parsed.outputTokens()
            );
            return parsed.result();
        } catch (HttpTimeoutException exception) {
            telemetry.recordTimeout();
            return unknown(SemanticModerationResult.Status.TIMEOUT);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            telemetry.recordError();
            return unknown(SemanticModerationResult.Status.ERROR);
        } catch (IOException | RuntimeException exception) {
            telemetry.recordError();
            return unknown(SemanticModerationResult.Status.ERROR);
        } finally {
            telemetry.recordLatency(System.nanoTime() - startedAt);
        }
    }

    public String model() {
        return config.model();
    }

    public OpenAiProviderTelemetry.Snapshot telemetry() {
        return telemetry.snapshot();
    }

    String createRequestBody(String message) throws JsonProcessingException {
        ObjectNode root = JSON.createObjectNode();
        root.put("model", config.model());
        root.put("store", false);
        root.put("max_output_tokens", MAX_OUTPUT_TOKENS);
        root.set("reasoning", JSON.createObjectNode().put("effort", "none"));

        ArrayNode input = root.putArray("input");
        input.addObject()
                .put("role", "system")
                .put("content", OpenAiModerationPolicyPrompt.INSTRUCTIONS);
        input.addObject()
                .put("role", "user")
                .put("content", "다음 메시지를 판정하라:\n<message>\n" + message + "\n</message>");

        ObjectNode format = root.putObject("text").putObject("format");
        format.put("type", "json_schema");
        format.put("name", "chat_moderation_decision");
        format.put("strict", true);
        ObjectNode schema = format.putObject("schema");
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        properties.putObject("decision")
                .put("type", "string")
                .putArray("enum").add("ALLOW").add("BLOCK");
        ObjectNode reason = properties.putObject("reason");
        reason.putArray("type").add("string").add("null");
        reason.putArray("enum")
                .add("PROFANITY")
                .add("SEXUAL_CONTENT")
                .add("IMPLICIT_INSULT")
                .add("FAMILY_INSULT")
                .add("ADVERTISEMENT")
                .add("OTHER")
                .addNull();
        properties.putObject("confidence")
                .put("type", "number")
                .put("minimum", 0.0)
                .put("maximum", 1.0);
        schema.putArray("required").add("decision").add("reason").add("confidence");
        schema.put("additionalProperties", false);
        return JSON.writeValueAsString(root);
    }

    private ParsedResponse parseResponse(String body) throws JsonProcessingException {
        JsonNode response = JSON.readTree(body);
        if (!"completed".equals(response.path("status").asText())) {
            throw new IllegalArgumentException("response did not complete");
        }
        String outputText = findOutputText(response.path("output"));
        JsonNode decisionJson = JSON.readTree(outputText);
        String decisionValue = requiredText(decisionJson, "decision");
        double confidence = decisionJson.path("confidence").asDouble(Double.NaN);
        if (!Double.isFinite(confidence) || confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("invalid confidence");
        }

        SemanticModerationResult.Decision decision = switch (decisionValue) {
            case "ALLOW" -> SemanticModerationResult.Decision.ALLOW;
            case "BLOCK" -> SemanticModerationResult.Decision.BLOCK;
            default -> throw new IllegalArgumentException("unsupported decision");
        };
        JsonNode reasonNode = decisionJson.get("reason");
        List<ModerationReason> reasons;
        if (decision == SemanticModerationResult.Decision.ALLOW) {
            if (reasonNode != null && !reasonNode.isNull()) {
                throw new IllegalArgumentException("ALLOW must not include a reason");
            }
            reasons = List.of();
        } else {
            if (reasonNode == null || reasonNode.isNull()) {
                throw new IllegalArgumentException("BLOCK must include a reason");
            }
            reasons = List.of(mapReason(reasonNode.asText()));
        }

        JsonNode usage = response.path("usage");
        long inputTokens = usage.path("input_tokens").asLong(0);
        long cachedInputTokens = usage.path("input_tokens_details")
                .path("cached_tokens").asLong(0);
        long outputTokens = usage.path("output_tokens").asLong(0);
        return new ParsedResponse(
                new SemanticModerationResult(
                        decision,
                        reasons,
                        confidence,
                        SemanticModerationResult.Status.SUCCESS,
                        "openai-responses:" + config.model()
                ),
                inputTokens,
                cachedInputTokens,
                outputTokens
        );
    }

    private String findOutputText(JsonNode output) {
        if (!output.isArray()) {
            throw new IllegalArgumentException("missing output array");
        }
        for (JsonNode item : output) {
            if (!"message".equals(item.path("type").asText())) {
                continue;
            }
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

    private SemanticModerationResult unknown(SemanticModerationResult.Status status) {
        return new SemanticModerationResult(
                SemanticModerationResult.Decision.UNKNOWN,
                List.of(),
                0.0,
                status,
                "openai-responses:" + config.model()
        );
    }

    @FunctionalInterface
    interface OpenAiTransport {
        TransportResponse send(String requestBody) throws IOException, InterruptedException;
    }

    record TransportResponse(int statusCode, String body) {
    }

    private record ParsedResponse(
            SemanticModerationResult result,
            long inputTokens,
            long cachedInputTokens,
            long outputTokens
    ) {
    }

    private static final class JdkHttpTransport implements OpenAiTransport {
        private final OpenAiSemanticProviderConfig config;
        private final HttpClient client;

        private JdkHttpTransport(OpenAiSemanticProviderConfig config) {
            this.config = config;
            this.client = HttpClient.newBuilder()
                    .connectTimeout(config.timeout())
                    .build();
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
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            return new TransportResponse(response.statusCode(), response.body());
        }
    }
}
