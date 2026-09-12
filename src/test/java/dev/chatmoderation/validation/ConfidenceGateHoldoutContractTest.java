package dev.chatmoderation.validation;

import dev.chatmoderation.gate.GateEvaluationResources;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfidenceGateHoldoutContractTest {
    @Test
    void sealedCandidateRecallMeetsPhaseTarget() {
        ConfidenceGateEvaluationSupport.RoutingMetrics metrics =
                ConfidenceGateCalibrationEvaluation.evaluate(
                        GateEvaluationResources.loadHoldout())
                        .routingMetrics(ConfidenceGateEvaluationSupport.RouteVersion.PHASE_315);
        assertTrue(metrics.candidateRecall() >= 0.95,
                () -> "sealed candidate recall=" + metrics.candidateRecall()
                        + " missed=" + metrics.missedCandidates());
    }
}
