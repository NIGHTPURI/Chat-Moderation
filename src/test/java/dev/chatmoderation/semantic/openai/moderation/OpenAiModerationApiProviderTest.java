package dev.chatmoderation.semantic.openai.moderation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.chatmoderation.core.DefaultChatModerationService;
import dev.chatmoderation.core.model.ModerationAction;
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
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiModerationApiProviderTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void createsOfficialModerationRequestShape() throws Exception {
        OpenAiModerationApiProvider provider = provider(body -> response(200, allowedResponse()));

        JsonNode request = JSON.readTree(provider.createRequestBody("검사할 메시지"));

        assertEquals("omni-moderation-latest", request.path("model").asText());
        assertEquals("검사할 메시지", request.path("input").asText());
        assertEquals(2, request.size());
    }

    @Test
    void flaggedFalseBecomesAllowAndPreservesNativeCategories() {
        OpenAiModerationApiProvider provider = provider(body -> response(200, allowedResponse()));

        SemanticModerationResult result = provider.moderate("정상 메시지");
        OpenAiModerationObservation observation = provider.telemetry()
                .observations().getFirst();

        assertEquals(SemanticModerationResult.Decision.ALLOW, result.decision());
        assertEquals(List.of(), result.reasons());
        assertFalse(observation.flagged());
        assertEquals(false, observation.categories().get("harassment"));
        assertEquals(0.02, observation.categoryScores().get("harassment"));
        assertEquals(observation, provider.lastObservation().orElseThrow());
    }

    @Test
    void flaggedTrueKeepsNativeCategoriesWithoutInventingCoreMapping() {
        OpenAiModerationApiProvider provider = provider(body -> response(200, """
                {
                  "model": "omni-moderation-latest",
                  "results": [{
                    "flagged": true,
                    "categories": {
                      "sexual": true,
                      "future/native-category": true,
                      "harassment": false
                    },
                    "category_scores": {
                      "sexual": 0.97,
                      "future/native-category": 0.81,
                      "harassment": 0.04
                    }
                  }]
                }
                """));

        SemanticModerationResult result = provider.moderate("차단 메시지");
        OpenAiModerationApiTelemetry.Snapshot telemetry = provider.telemetry();

        assertEquals(SemanticModerationResult.Decision.BLOCK, result.decision());
        assertEquals(List.of(), result.reasons());
        assertEquals(1, telemetry.categoryFlagged().get("future/native-category"));
        assertEquals(0.81, telemetry.observations().getFirst()
                .categoryScores().get("future/native-category"));
    }

    @Test
    void hybridUsesOtherWhenProviderHasOnlyNativeCategoryMetadata() {
        OpenAiModerationApiProvider provider = provider(body -> response(200, """
                {
                  "model": "omni-moderation-latest",
                  "results": [{
                    "flagged": true,
                    "categories": {"violence": true},
                    "category_scores": {"violence": 0.91}
                  }]
                }
                """));

        SemanticHybridModerator hybrid = new SemanticHybridModerator(
                new DefaultChatModerationService(List.of()),
                new SemanticReviewRouter(),
                provider,
                ProviderFailurePolicy.DETERMINISTIC_FALLBACK
        );
        SemanticHybridResult result = hybrid.moderate("너는 정말 쓸모없는 사람이다");

        assertEquals(List.of(dev.chatmoderation.core.model.ModerationReason.OTHER),
                result.result().reasons());
    }

    @Test
    void rateLimitResponseIsVisibleAsErrorAnd429() {
        OpenAiModerationApiProvider provider = provider(body -> response(429, "{}"));

        SemanticModerationResult result = provider.moderate("테스트");

        assertEquals(SemanticModerationResult.Decision.UNKNOWN, result.decision());
        assertEquals(SemanticModerationResult.Status.ERROR, result.status());
        assertEquals(1, provider.telemetry().errors());
        assertTrue(provider.lastObservation().isEmpty());
        assertEquals(1, provider.telemetry().rateLimited());
        assertEquals(1, provider.telemetry().httpStatuses().get(429));
    }

    @Test
    void malformedResponseBecomesUnknownError() {
        OpenAiModerationApiProvider provider = provider(body -> response(200, "{}"));

        SemanticModerationResult result = provider.moderate("테스트");

        assertEquals(SemanticModerationResult.Decision.UNKNOWN, result.decision());
        assertEquals(SemanticModerationResult.Status.ERROR, result.status());
        assertEquals(1, provider.telemetry().errors());
    }

    @Test
    void timeoutBecomesUnknownTimeout() {
        OpenAiModerationApiProvider provider = provider(body -> {
            throw new HttpTimeoutException("forced timeout");
        });

        SemanticModerationResult result = provider.moderate("테스트");

        assertEquals(SemanticModerationResult.Decision.UNKNOWN, result.decision());
        assertEquals(SemanticModerationResult.Status.TIMEOUT, result.status());
        assertEquals(1, provider.telemetry().timeouts());
    }

    @Test
    void hybridSerializesOnlyMaskedPersonalInformation() {
        AtomicReference<String> requestBody = new AtomicReference<>();
        OpenAiModerationApiProvider provider = provider(body -> {
            requestBody.set(body);
            return response(200, allowedResponse());
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
    }

    @Test
    void environmentConfigurationUsesOnlyOpenAiApiKey() {
        assertTrue(OpenAiModerationApiConfig.fromEnvironment(Map.of()).isEmpty());
        OpenAiModerationApiConfig config = OpenAiModerationApiConfig.fromEnvironment(
                Map.of(OpenAiModerationApiConfig.API_KEY_ENV, "test-key")
        ).orElseThrow();

        assertEquals(URI.create("https://api.openai.com/v1/moderations"), config.endpoint());
        assertEquals(Duration.ofSeconds(30), config.timeout());
    }

    private OpenAiModerationApiProvider provider(
            OpenAiModerationApiProvider.ModerationTransport transport
    ) {
        return new OpenAiModerationApiProvider(
                new OpenAiModerationApiConfig(
                        URI.create("https://example.invalid/v1/moderations"),
                        "test-key",
                        Duration.ofSeconds(1)
                ),
                transport
        );
    }

    private OpenAiModerationApiProvider.TransportResponse response(int status, String body) {
        return new OpenAiModerationApiProvider.TransportResponse(status, body);
    }

    private String allowedResponse() {
        return """
                {
                  "model": "omni-moderation-latest",
                  "results": [{
                    "flagged": false,
                    "categories": {
                      "harassment": false,
                      "sexual": false
                    },
                    "category_scores": {
                      "harassment": 0.02,
                      "sexual": 0.01
                    }
                  }]
                }
                """;
    }
}
