package dev.chatmoderation.semantic;

import dev.chatmoderation.core.model.ModerationReason;
import dev.chatmoderation.core.model.ModerationResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HighRecallSemanticRouterTest {

    private final HighRecallSemanticRouter router = new HighRecallSemanticRouter();

    @Test
    void routesLowCostAbuseSignalsWithoutMakingBlockDecision() {
        assertRouted("너는 대화할 가치가 없는 사람이야");
        assertRouted("그 행동을 보니 사람 구실은 기대하기 어렵다");
        assertRouted("네 부모 교육 수준이 뻔히 보인다");
        assertRouted("나한테 야한 사진을 보내 줘");
        assertRouted("지금 등록하면 고수익을 보장합니다");
        assertRouted("너 완전 노답 관종이야");
    }

    @Test
    void keepsClearMentionReportAndOrdinaryCriticismLocal() {
        assertLocal("운영자에게 신고합니다 상대가 쓸모없다고 조롱했습니다");
        assertLocal("기사에서 인용한 문장입니다 '너는 쓸모없다'");
        assertLocal("언어 수업에서는 가족 비하의 문제를 분석합니다");
        assertLocal("이번 제안은 근거를 더 보완해야 합니다");
    }

    @Test
    void reportWordDoesNotHideAContinuingDirectAttack() {
        assertRouted("신고할 테니 들어라 너는 정말 무능하고 쓸모없다");
    }

    @Test
    void routesUnseenStructuralAndLexicalVariants() {
        assertRouted("말투만 봐도 판단력이 형편없는 사람 같네");
        assertRouted("당신 가족은 기본 예절도 부족하군요");
        assertRouted("둘이 숙소에서 은밀하게 만나고 싶어");
        assertRouted("한정 혜택을 받으려면 지금 결제하세요");
        assertRouted("너는 완전 잼민이충처럼 구는구나");
    }

    @Test
    void deterministicBlockAndMaskAreAlwaysLocalFinal() {
        HighRecallSemanticRoute blocked = router.route(
                "너는 쓸모없다",
                ModerationResult.block(List.of(ModerationReason.PROFANITY))
        );
        HighRecallSemanticRoute masked = router.route(
                "연락처를 보냈다",
                ModerationResult.mask("연락처를 *******", List.of(
                        ModerationReason.PERSONAL_INFORMATION
                ))
        );

        assertFalse(blocked.routedToSemanticProvider());
        assertFalse(masked.routedToSemanticProvider());
        assertEquals(HighRecallRoutingDecision.LOCAL_FINAL, blocked.decision());
        assertEquals(HighRecallRoutingDecision.LOCAL_FINAL, masked.decision());
    }

    private void assertRouted(String message) {
        HighRecallSemanticRoute result = router.route(message, ModerationResult.allow(message));
        assertTrue(result.routedToSemanticProvider(), message);
        assertEquals(message, result.semanticMessage());
    }

    private void assertLocal(String message) {
        assertFalse(router.route(message, ModerationResult.allow(message))
                .routedToSemanticProvider(), message);
    }
}
