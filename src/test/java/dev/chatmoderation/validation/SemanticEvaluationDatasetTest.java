package dev.chatmoderation.validation;

import dev.chatmoderation.core.model.ModerationAction;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SemanticEvaluationDatasetTest {

    @Test
    void containsAtLeastOneHundredCasesAcrossEveryCategory() {
        List<SemanticEvaluationCase> cases = ValidationResources.loadSemanticEvaluation();

        assertTrue(cases.size() >= 100);
        assertEquals(
                EnumSet.allOf(SemanticEvaluationCategory.class),
                cases.stream()
                        .map(SemanticEvaluationCase::category)
                        .collect(java.util.stream.Collectors.toSet())
        );
    }

    @Test
    void balancesBenignAndPolicyViolatingContexts() {
        List<SemanticEvaluationCase> cases = ValidationResources.loadSemanticEvaluation();
        long allowed = cases.stream()
                .filter(testCase -> testCase.expectedAction() == ModerationAction.ALLOW)
                .count();
        long blocked = cases.stream()
                .filter(testCase -> testCase.expectedAction() == ModerationAction.BLOCK)
                .count();

        assertTrue(allowed >= 40, "normal context must be represented sufficiently");
        assertTrue(blocked >= 60, "harmful context must be represented sufficiently");
    }
}
