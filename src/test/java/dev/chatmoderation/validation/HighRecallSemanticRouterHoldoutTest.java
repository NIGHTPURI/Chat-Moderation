package dev.chatmoderation.validation;

import dev.chatmoderation.core.ChatModerationService;
import dev.chatmoderation.core.DefaultChatModerationService;
import dev.chatmoderation.router.RouterEvaluationResources;
import dev.chatmoderation.semantic.HighRecallSemanticRouter;
import dev.chatmoderation.semantic.SemanticReviewRouter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HighRecallSemanticRouterHoldoutTest {

    @Test
    void sealedHoldoutResultIsRecordedWithoutRetuning() {
        ChatModerationService deterministic = new DefaultChatModerationService(
                ValidationResources.loadKeywordsByReason(),
                ValidationResources.loadKeywordExceptions(),
                ValidationResources.loadAliasesByReason()
        );
        RouterEvaluationSupport.Evaluated evaluated = RouterEvaluationSupport.evaluate(
                RouterEvaluationResources.loadHoldout(),
                deterministic,
                new SemanticReviewRouter(),
                new HighRecallSemanticRouter()
        );
        RouterEvaluationSupport.RouterMetrics metrics = evaluated.redesignedRouterMetrics();

        assertEquals(112, metrics.semanticBlockCandidates());
        assertEquals(98, metrics.routedBlocks());
        assertEquals(14, metrics.missedBlocks());
        assertEquals(0, metrics.routedAllows());
        assertEquals(98, metrics.routed());
    }
}
