package dev.chatmoderation.semantic;

import dev.chatmoderation.core.model.ModerationReason;
import dev.chatmoderation.core.model.ModerationResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfidenceAwareSemanticGateTest {
    private final ConfidenceAwareSemanticGate gate = new ConfidenceAwareSemanticGate();

    @Test
    void distinguishesAllFourInternalStates() {
        assertEquals(ConfidenceGateState.CERTAIN_ALLOW,
                gate.route("오늘 날씨가 좋다", ModerationResult.allow("오늘 날씨가 좋다")).state());
        assertEquals(ConfidenceGateState.CERTAIN_BLOCK,
                gate.route("너한테 심한 욕을 했다",
                        ModerationResult.block(List.of(ModerationReason.PROFANITY))).state());
        assertEquals(ConfidenceGateState.CERTAIN_MASK,
                gate.route("연락처는 010-1234-5678",
                        ModerationResult.mask("연락처는 *************",
                                List.of(ModerationReason.PERSONAL_INFORMATION))).state());
        assertEquals(ConfidenceGateState.NEEDS_SEMANTIC_REVIEW,
                gate.route("너는 대화할 가치가 없어",
                        ModerationResult.allow("너는 대화할 가치가 없어")).state());
    }

    @Test
    void contextSensitiveDeterministicBlockCanReachReview() {
        ConfidenceAwareSemanticRoute route = gate.route(
                "공학 보고서에서 후장식 구조를 설명합니다",
                ModerationResult.block(List.of(ModerationReason.SEXUAL_CONTENT))
        );
        assertTrue(route.routed());
    }

    @Test
    void gateNeverChangesTheDeterministicResult() {
        ModerationResult local = ModerationResult.allow("오늘 밤 호텔에서 같이 자자");
        assertEquals(local, gate.route("오늘 밤 호텔에서 같이 자자", local)
                .deterministicResult());
    }
}
