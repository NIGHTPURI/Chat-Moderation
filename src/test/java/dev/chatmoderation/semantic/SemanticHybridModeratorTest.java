package dev.chatmoderation.semantic;

import dev.chatmoderation.core.ChatModerationService;
import dev.chatmoderation.core.DefaultChatModerationService;
import dev.chatmoderation.core.model.ModerationAction;
import dev.chatmoderation.core.model.ModerationReason;
import dev.chatmoderation.core.model.ModerationResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SemanticHybridModeratorTest {

    @Test
    void doesNotRouteClearAllowOrClearBlock() {
        AtomicInteger calls = new AtomicInteger();
        SemanticHybridModerator moderator = hybrid(message -> {
            calls.incrementAndGet();
            return allowResult();
        }, ProviderFailurePolicy.DETERMINISTIC_FALLBACK);

        SemanticHybridResult allowed = moderator.moderate("오늘 날씨가 좋네요");
        SemanticHybridResult blocked = moderator.moderate("씨발 그만해");

        assertEquals(SemanticRoutingDecision.CLEAR_ALLOW, allowed.routingDecision());
        assertEquals(SemanticRoutingDecision.CLEAR_BLOCK, blocked.routingDecision());
        assertEquals(0, calls.get());
    }

    @Test
    void doesNotRouteClearMaskWithoutContextTrigger() {
        AtomicInteger calls = new AtomicInteger();
        SemanticHybridResult result = hybrid(message -> {
            calls.incrementAndGet();
            return allowResult();
        }, ProviderFailurePolicy.DETERMINISTIC_FALLBACK)
                .moderate("연락처는 010-1234-5678입니다");

        assertEquals(SemanticRoutingDecision.CLEAR_MASK, result.routingDecision());
        assertEquals(ModerationAction.MASK, result.result().action());
        assertEquals("연락처는 *************입니다", result.result().outputMessage());
        assertEquals(0, calls.get());
    }

    @Test
    void semanticAllowCanReleaseAmbiguousDeterministicBlockInExperiment() {
        SemanticHybridResult result = hybrid(
                new LocalHeuristicSemanticProvider(),
                ProviderFailurePolicy.DETERMINISTIC_FALLBACK
        ).moderate("병신년 기록을 확인했다");

        assertTrue(result.routedToSemanticProvider());
        assertEquals(ModerationAction.ALLOW, result.result().action());
    }

    @Test
    void ambiguousTermCannotReleaseAnotherClearBlock() {
        AtomicInteger calls = new AtomicInteger();
        SemanticHybridResult result = hybrid(message -> {
            calls.incrementAndGet();
            return allowResult();
        }, ProviderFailurePolicy.DETERMINISTIC_FALLBACK)
                .moderate("병신년을 설명하면서 씨발이라고 욕했다");

        assertEquals(SemanticRoutingDecision.CLEAR_BLOCK, result.routingDecision());
        assertEquals(ModerationAction.BLOCK, result.result().action());
        assertEquals(0, calls.get());
    }

    @Test
    void semanticBlockCanBlockContextualInsult() {
        SemanticHybridResult result = hybrid(
                new LocalHeuristicSemanticProvider(),
                ProviderFailurePolicy.DETERMINISTIC_FALLBACK
        ).moderate("너는 정말 쓸모없는 사람이다");

        assertTrue(result.routedToSemanticProvider());
        assertEquals(ModerationAction.BLOCK, result.result().action());
        assertEquals(List.of(ModerationReason.PROFANITY), result.result().reasons());
    }

    @Test
    void sendsOnlyMaskedContentWhenPersonalInformationNeedsReview() {
        AtomicReference<String> providerInput = new AtomicReference<>();
        SemanticHybridModerator moderator = hybrid(message -> {
            providerInput.set(message);
            return allowResult();
        }, ProviderFailurePolicy.DETERMINISTIC_FALLBACK);

        SemanticHybridResult result = moderator.moderate("몸 사진은 010-1234-5678로 보내 줘");

        assertTrue(result.routedToSemanticProvider());
        assertFalse(providerInput.get().contains("010-1234-5678"));
        assertEquals("몸 사진은 *************로 보내 줘", providerInput.get());
        assertEquals(ModerationAction.MASK, result.result().action());
        assertEquals("몸 사진은 *************로 보내 줘", result.result().outputMessage());
    }

    @Test
    void fallsBackToDeterministicResultOnProviderException() {
        SemanticHybridResult result = hybrid(
                message -> {
                    throw new IllegalStateException("provider unavailable");
                },
                ProviderFailurePolicy.DETERMINISTIC_FALLBACK
        ).moderate("너는 쓸모없는 사람이다");

        assertEquals(SemanticModerationResult.Status.ERROR, result.providerStatus());
        assertEquals(ModerationAction.ALLOW, result.result().action());
    }

    @Test
    void supportsExplicitFailClosedPolicy() {
        SemanticHybridResult result = hybrid(
                message -> timeoutResult(),
                ProviderFailurePolicy.FAIL_CLOSED
        ).moderate("너는 쓸모없는 사람이다");

        assertEquals(SemanticModerationResult.Status.TIMEOUT, result.providerStatus());
        assertEquals(ModerationAction.BLOCK, result.result().action());
        assertNull(result.result().outputMessage());
    }

    @Test
    void failOpenStillPreservesMaskedPersonalInformation() {
        SemanticHybridResult result = hybrid(
                message -> timeoutResult(),
                ProviderFailurePolicy.FAIL_OPEN
        ).moderate("몸 사진은 010-1234-5678로 보내 줘");

        assertEquals(ModerationAction.MASK, result.result().action());
        assertEquals("몸 사진은 *************로 보내 줘", result.result().outputMessage());
    }

    private SemanticHybridModerator hybrid(
            SemanticModerationProvider provider,
            ProviderFailurePolicy failurePolicy
    ) {
        ChatModerationService deterministic = new DefaultChatModerationService(
                Map.of(
                        ModerationReason.PROFANITY, List.of("씨발", "병신"),
                        ModerationReason.SEXUAL_CONTENT, List.of("야동", "후장")
                ),
                Map.of(),
                Map.of()
        );
        return new SemanticHybridModerator(
                deterministic,
                new SemanticReviewRouter(),
                provider,
                failurePolicy
        );
    }

    private SemanticModerationResult allowResult() {
        return new SemanticModerationResult(
                SemanticModerationResult.Decision.ALLOW,
                List.of(),
                0.9,
                SemanticModerationResult.Status.SUCCESS,
                "test"
        );
    }

    private SemanticModerationResult timeoutResult() {
        return new SemanticModerationResult(
                SemanticModerationResult.Decision.UNKNOWN,
                List.of(),
                0.0,
                SemanticModerationResult.Status.TIMEOUT,
                "test"
        );
    }
}
