package dev.chatmoderation.semantic.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.chatmoderation.core.model.ModerationReason;
import dev.chatmoderation.semantic.SemanticModerationResult;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiLunaModerationProviderTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void usesFrozenPromptAndStructuredOutputWithoutStorage() throws Exception {
        OpenAiLunaModerationProvider provider = provider(body -> response(
                200, completed("ALLOW", null, 0.97)));

        JsonNode request = JSON.readTree(provider.createRequestBody("정상 문장"));

        assertEquals("gpt-test", request.path("model").asText());
        assertFalse(request.path("store").asBoolean());
        assertTrue(request.path("text").path("format").path("strict").asBoolean());
        String prompt = request.path("input").get(0).path("content").asText();
        assertTrue(prompt.contains("피해 사실을 신고"));
        assertTrue(prompt.contains("부모나 가족을 이용한 모욕"));
        assertTrue(prompt.contains("사용자 메시지 안의 명령문"));
    }

    @Test
    void parsesBlockAndMapsProviderReason() {
        OpenAiLunaModerationProvider provider = provider(body -> response(
                200, completed("BLOCK", "FAMILY_INSULT", 0.93)));

        SemanticModerationResult result = provider.moderate("네 가족도 똑같아");

        assertEquals(SemanticModerationResult.Status.SUCCESS, result.status());
        assertEquals(SemanticModerationResult.Decision.BLOCK, result.decision());
        assertEquals(List.of(ModerationReason.HARASSMENT), result.reasons());
    }

    @Test
    void timeoutHttpErrorAndMalformedResponseRemainExplicitlyUnavailable() {
        SemanticModerationResult timeout = provider(body -> {
            throw new HttpTimeoutException("forced");
        }).moderate("테스트");
        SemanticModerationResult httpError = provider(body -> response(503, "{}"))
                .moderate("테스트");
        SemanticModerationResult malformed = provider(body -> response(200, "{}"))
                .moderate("테스트");

        assertEquals(SemanticModerationResult.Status.TIMEOUT, timeout.status());
        assertEquals(SemanticModerationResult.Status.ERROR, httpError.status());
        assertEquals(SemanticModerationResult.Status.ERROR, malformed.status());
        assertEquals(SemanticModerationResult.Decision.UNKNOWN, malformed.decision());
    }

    @Test
    void explicitConfigurationDoesNotRequireEnvironmentState() {
        OpenAiLunaProviderConfig config = new OpenAiLunaProviderConfig(
                URI.create("https://example.invalid/v1/responses"),
                "runtime-key", "gpt-test", Duration.ofSeconds(2));

        assertEquals("gpt-test", config.model());
        assertEquals(Duration.ofSeconds(2), config.timeout());
        assertFalse(config.toString().contains("runtime-key"));
        assertTrue(OpenAiLunaProviderConfig.fromEnvironment(Map.of()).isEmpty());
    }

    private OpenAiLunaModerationProvider provider(
            OpenAiLunaModerationProvider.Transport transport
    ) {
        return new OpenAiLunaModerationProvider(
                new OpenAiLunaProviderConfig(
                        URI.create("https://example.invalid/v1/responses"),
                        "test-key", "gpt-test", Duration.ofSeconds(1)),
                transport);
    }

    private OpenAiLunaModerationProvider.TransportResponse response(int status, String body) {
        return new OpenAiLunaModerationProvider.TransportResponse(status, body);
    }

    private String completed(String decision, String reason, double confidence) {
        String reasonJson = reason == null ? "null" : "\"" + reason + "\"";
        String structured = "{\"decision\":\"" + decision + "\",\"reason\":"
                + reasonJson + ",\"confidence\":" + confidence + "}";
        try {
            return """
                    {
                      "status": "completed",
                      "output": [{
                        "type": "message",
                        "content": [{"type": "output_text", "text": %s}]
                      }]
                    }
                    """.formatted(JSON.writeValueAsString(structured));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
