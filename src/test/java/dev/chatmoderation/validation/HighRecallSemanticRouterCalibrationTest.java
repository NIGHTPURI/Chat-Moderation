package dev.chatmoderation.validation;

import dev.chatmoderation.core.ChatModerationService;
import dev.chatmoderation.core.DefaultChatModerationService;
import dev.chatmoderation.core.model.ModerationAction;
import dev.chatmoderation.core.model.ModerationResult;
import dev.chatmoderation.router.RouterEvaluationCase;
import dev.chatmoderation.router.RouterEvaluationResources;
import dev.chatmoderation.semantic.HighRecallSemanticRouter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class HighRecallSemanticRouterCalibrationTest {

    @Test
    void calibrationSemanticCandidateRecallIsAtLeastNinetyFivePercent() {
        ChatModerationService deterministic = deterministicService();
        HighRecallSemanticRouter router = new HighRecallSemanticRouter();
        List<RouterEvaluationCase> cases = RouterEvaluationResources.loadCalibration();
        long semanticBlocks = 0;
        long routedBlocks = 0;
        List<String> missed = new java.util.ArrayList<>();
        for (RouterEvaluationCase testCase : cases) {
            ModerationResult local = deterministic.moderate(testCase.message());
            if (testCase.expectedAction() != ModerationAction.BLOCK
                    || local.action() != ModerationAction.ALLOW) {
                continue;
            }
            semanticBlocks++;
            if (router.route(testCase.message(), local).routedToSemanticProvider()) {
                routedBlocks++;
            } else {
                missed.add(testCase.category() + ": " + testCase.message());
            }
        }

        double candidateRecall = semanticBlocks == 0
                ? 0.0 : (double) routedBlocks / semanticBlocks;
        assertTrue(candidateRecall >= 0.95,
                "candidate recall=" + candidateRecall + " (" + routedBlocks
                        + "/" + semanticBlocks + "), missed=" + missed);
    }

    private ChatModerationService deterministicService() {
        return new DefaultChatModerationService(
                ValidationResources.loadKeywordsByReason(),
                ValidationResources.loadKeywordExceptions(),
                ValidationResources.loadAliasesByReason()
        );
    }
}
