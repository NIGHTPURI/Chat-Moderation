package dev.chatmoderation.semantic.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.chatmoderation.core.DefaultChatModerationService;
import dev.chatmoderation.core.model.ModerationAction;
import dev.chatmoderation.core.model.ModerationReason;
import dev.chatmoderation.semantic.ProviderFailurePolicy;
import dev.chatmoderation.semantic.SemanticHybridModerator;
import dev.chatmoderation.semantic.SemanticHybridResult;
import dev.chatmoderation.semantic.SemanticModerationResult;
import dev.chatmoderation.semantic.SemanticReviewRouter;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiSemanticModerationProviderTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void createsStructuredOutputRequestWithExplicitPolicy() throws Exception {
        OpenAiSemanticModerationProvider provider = provider(body -> response(
                200,
                completed("ALLOW", null, 0.91, 120, 20, 8)
        ));

        JsonNode request = JSON.readTree(provider.createRequestBody("보지 못한 영화다"));

        assertEquals("gpt-test", request.path("model").asText());
        assertFalse(request.path("store").asBoolean());
        assertEquals("json_schema", request.path("text").path("format")
                .path("type").asText());
        assertTrue(request.path("text").path("format").path("strict").asBoolean());
        String instructions = request.path("input").get(0).path("content").asText();
        assertTrue(instructions.contains("욕설이나 유해 표현을 설명, 인용"));
        assertTrue(instructions.contains("부모나 가족을 이용한 모욕"));
        assertTrue(instructions.contains("광고·도배 의도"));
    }

    @Test
    void parsesAllowAndTokenUsage() {
        OpenAiSemanticModerationProvider provider = provider(body -> response(
                200,
                completed("ALLOW", null, 0.91, 120, 20, 8)
        ));

        SemanticModerationResult result = provider.moderate("보지 못한 영화다");

        assertEquals(SemanticModerationResult.Decision.ALLOW, result.decision());
        assertEquals(SemanticModerationResult.Status.SUCCESS, result.status());
        assertEquals(List.of(), result.reasons());
        assertEquals(1, provider.telemetry().requests());
        assertEquals(1, provider.telemetry().successes());
        assertEquals(120, provider.telemetry().inputTokens());
        assertEquals(20, provider.telemetry().cachedInputTokens());
        assertEquals(8, provider.telemetry().outputTokens());
    }

    @Test
    void mapsProviderSpecificReasonsToExistingCoreReasons() {
        OpenAiSemanticModerationProvider provider = provider(body -> response(
                200,
                completed("BLOCK", "FAMILY_INSULT", 0.88, 100, 0, 9)
        ));

        SemanticModerationResult result = provider.moderate("네 가족도 똑같아");

        assertEquals(SemanticModerationResult.Decision.BLOCK, result.decision());
        assertEquals(List.of(ModerationReason.HARASSMENT), result.reasons());
    }

    @Test
    void malformedStructuredOutputBecomesUnknownError() {
        OpenAiSemanticModerationProvider provider = provider(body -> response(
                200,
                "{\"status\":\"completed\",\"output\":[]}"
        ));

        SemanticModerationResult result = provider.moderate("테스트");

        assertEquals(SemanticModerationResult.Decision.UNKNOWN, result.decision());
        assertEquals(SemanticModerationResult.Status.ERROR, result.status());
        assertEquals(1, provider.telemetry().errors());
    }

    @Test
    void timeoutBecomesUnknownTimeout() {
        OpenAiSemanticModerationProvider provider = provider(body -> {
            throw new HttpTimeoutException("forced timeout");
        });

        SemanticModerationResult result = provider.moderate("테스트");

        assertEquals(SemanticModerationResult.Decision.UNKNOWN, result.decision());
        assertEquals(SemanticModerationResult.Status.TIMEOUT, result.status());
        assertEquals(1, provider.telemetry().timeouts());
    }

    @Test
    void hybridSendsMaskedMessageThroughRealProviderTransport() {
        AtomicReference<String> requestBody = new AtomicReference<>();
        OpenAiSemanticModerationProvider provider = provider(body -> {
            requestBody.set(body);
            return response(200, completed("ALLOW", null, 0.90, 100, 0, 8));
        });
        SemanticHybridModerator hybrid = new SemanticHybridModerator(
                new DefaultChatModerationService(List.of()),
                new SemanticReviewRouter(),
                provider,
                ProviderFailurePolicy.DETERMINISTIC_FALLBACK
        );

        SemanticHybridResult result = hybrid.moderate("몸 사진은 010-1234-5678로 보내 줘");

        assertTrue(result.routedToSemanticProvider());
        assertFalse(requestBody.get().contains("010-1234-5678"));
        assertTrue(requestBody.get().contains("*************"));
        assertEquals(ModerationAction.MASK, result.result().action());
        assertEquals("몸 사진은 *************로 보내 줘", result.result().outputMessage());
    }

    @Test
    void missingCredentialSkipsEnvironmentConfiguration() {
        assertTrue(OpenAiSemanticProviderConfig.fromEnvironment(Map.of()).isEmpty());
    }

    @Test
    void pricingIsOptionalAndConfigurable() {
        Optional<OpenAiSemanticProviderConfig> loaded = OpenAiSemanticProviderConfig.fromEnvironment(
                Map.of(
                        OpenAiSemanticProviderConfig.API_KEY_ENV, "test-key",
                        OpenAiSemanticProviderConfig.INPUT_PRICE_ENV, "0.20",
                        OpenAiSemanticProviderConfig.CACHED_INPUT_PRICE_ENV, "0.02",
                        OpenAiSemanticProviderConfig.OUTPUT_PRICE_ENV, "1.20"
                )
        );

        OpenAiSemanticProviderConfig config = loaded.orElseThrow();
        assertEquals("gpt-5.6-luna", config.model());
        assertEquals(0.00013, config.pricing().orElseThrow()
                .estimateUsd(500, 100, 40), 0.0000001);
    }

    private OpenAiSemanticModerationProvider provider(
            OpenAiSemanticModerationProvider.OpenAiTransport transport
    ) {
        return new OpenAiSemanticModerationProvider(
                new OpenAiSemanticProviderConfig(
                        URI.create("https://example.invalid/v1/responses"),
                        "test-key",
                        "gpt-test",
                        Duration.ofSeconds(1),
                        Optional.empty()
                ),
                transport
        );
    }

    private OpenAiSemanticModerationProvider.TransportResponse response(int status, String body) {
        return new OpenAiSemanticModerationProvider.TransportResponse(status, body);
    }

    private String completed(
            String decision,
            String reason,
            double confidence,
            long inputTokens,
            long cachedTokens,
            long outputTokens
    ) {
        String reasonJson = reason == null ? "null" : "\"" + reason + "\"";
        String structured = "{\"decision\":\"" + decision + "\",\"reason\":"
                + reasonJson + ",\"confidence\":" + confidence + "}";
        return """
                {
                  "status": "completed",
                  "output": [{
                    "type": "message",
                    "content": [{"type": "output_text", "text": %s}]
                  }],
                  "usage": {
                    "input_tokens": %d,
                    "input_tokens_details": {"cached_tokens": %d},
                    "output_tokens": %d
                  }
                }
                """.formatted(jsonString(structured), inputTokens, cachedTokens, outputTokens);
    }

    private String jsonString(String value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
