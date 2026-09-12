package dev.chatmoderation.semantic.openai.moderation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.chatmoderation.semantic.SemanticModerationProvider;
import dev.chatmoderation.semantic.SemanticModerationResult;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class OpenAiModerationApiProvider implements SemanticModerationProvider {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String PROVIDER_NAME = "openai-moderation-api:"
            + OpenAiModerationApiConfig.MODEL;

    private final OpenAiModerationApiConfig config;
    private final ModerationTransport transport;
    private final OpenAiModerationApiTelemetry telemetry = new OpenAiModerationApiTelemetry();
    private volatile OpenAiModerationObservation lastObservation;

    public OpenAiModerationApiProvider(OpenAiModerationApiConfig config) {
        this(config, new JdkHttpTransport(config));
    }

    OpenAiModerationApiProvider(
            OpenAiModerationApiConfig config,
            ModerationTransport transport
    ) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.transport = Objects.requireNonNull(transport, "transport must not be null");
    }

    @Override
    public SemanticModerationResult moderate(String message) {
        Objects.requireNonNull(message, "message must not be null");
        lastObservation = null;
        telemetry.recordRequest();
        long startedAt = System.nanoTime();
        try {
            TransportResponse response = transport.send(createRequestBody(message));
            telemetry.recordHttpStatus(response.statusCode());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                telemetry.recordError();
                return unknown(SemanticModerationResult.Status.ERROR);
            }
            ParsedResponse parsed = parseResponse(response.body());
            lastObservation = parsed.observation();
            telemetry.recordSuccess(parsed.observation());
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

    public OpenAiModerationApiTelemetry.Snapshot telemetry() {
        return telemetry.snapshot();
    }

    public Optional<OpenAiModerationObservation> lastObservation() {
        return Optional.ofNullable(lastObservation);
    }

    String createRequestBody(String message) throws JsonProcessingException {
        ObjectNode request = JSON.createObjectNode();
        request.put("model", OpenAiModerationApiConfig.MODEL);
        request.put("input", message);
        return JSON.writeValueAsString(request);
    }

    private ParsedResponse parseResponse(String body) throws JsonProcessingException {
        JsonNode response = JSON.readTree(body);
        JsonNode firstResult = response.path("results").path(0);
        if (firstResult.isMissingNode()) {
            throw new IllegalArgumentException("missing results[0]");
        }
        JsonNode flaggedNode = firstResult.get("flagged");
        JsonNode categoriesNode = firstResult.get("categories");
        JsonNode scoresNode = firstResult.get("category_scores");
        if (flaggedNode == null || !flaggedNode.isBoolean()
                || categoriesNode == null || !categoriesNode.isObject()
                || scoresNode == null || !scoresNode.isObject()) {
            throw new IllegalArgumentException("invalid moderation response shape");
        }

        Map<String, Boolean> categories = booleanFields(categoriesNode);
        Map<String, Double> scores = numericFields(scoresNode);
        boolean flagged = flaggedNode.asBoolean();
        OpenAiModerationObservation observation = new OpenAiModerationObservation(
                response.path("model").asText(OpenAiModerationApiConfig.MODEL),
                flagged,
                categories,
                scores
        );
        SemanticModerationResult result = new SemanticModerationResult(
                flagged ? SemanticModerationResult.Decision.BLOCK
                        : SemanticModerationResult.Decision.ALLOW,
                List.of(),
                confidence(flagged, categories, scores),
                SemanticModerationResult.Status.SUCCESS,
                PROVIDER_NAME
        );
        return new ParsedResponse(result, observation);
    }

    private Map<String, Boolean> booleanFields(JsonNode object) {
        Map<String, Boolean> values = new LinkedHashMap<>();
        object.properties().forEach(entry -> {
            if (!entry.getValue().isBoolean()) {
                throw new IllegalArgumentException("category values must be boolean");
            }
            values.put(entry.getKey(), entry.getValue().asBoolean());
        });
        return Map.copyOf(values);
    }

    private Map<String, Double> numericFields(JsonNode object) {
        Map<String, Double> values = new LinkedHashMap<>();
        object.properties().forEach(entry -> {
            if (!entry.getValue().isNumber()) {
                throw new IllegalArgumentException("category score values must be numeric");
            }
            double score = entry.getValue().asDouble();
            if (!Double.isFinite(score) || score < 0.0 || score > 1.0) {
                throw new IllegalArgumentException("category scores must be between 0 and 1");
            }
            values.put(entry.getKey(), score);
        });
        return Map.copyOf(values);
    }

    private double confidence(
            boolean flagged,
            Map<String, Boolean> categories,
            Map<String, Double> scores
    ) {
        double selected = categories.entrySet().stream()
                .filter(entry -> entry.getValue() == flagged)
                .map(Map.Entry::getKey)
                .map(scores::get)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .max()
                .orElse(0.0);
        return flagged ? selected : 1.0 - selected;
    }

    private SemanticModerationResult unknown(SemanticModerationResult.Status status) {
        return new SemanticModerationResult(
                SemanticModerationResult.Decision.UNKNOWN,
                List.of(),
                0.0,
                status,
                PROVIDER_NAME
        );
    }

    @FunctionalInterface
    interface ModerationTransport {
        TransportResponse send(String requestBody) throws IOException, InterruptedException;
    }

    record TransportResponse(int statusCode, String body) {
    }

    private record ParsedResponse(
            SemanticModerationResult result,
            OpenAiModerationObservation observation
    ) {
    }

    private static final class JdkHttpTransport implements ModerationTransport {
        private final OpenAiModerationApiConfig config;
        private final HttpClient client;

        private JdkHttpTransport(OpenAiModerationApiConfig config) {
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
