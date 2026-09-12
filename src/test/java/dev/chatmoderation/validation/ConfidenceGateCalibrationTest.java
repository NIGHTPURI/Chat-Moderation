package dev.chatmoderation.validation;

import dev.chatmoderation.gate.GateEvaluationResources;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfidenceGateCalibrationTest {
    @Test
    void calibrationCandidateRecallIsMeasuredWithoutUsingSealedData() {
        ConfidenceGateEvaluationSupport.RoutingMetrics metrics =
                ConfidenceGateCalibrationEvaluation.evaluate(
                        GateEvaluationResources.loadCalibration())
                        .routingMetrics(ConfidenceGateEvaluationSupport.RouteVersion.PHASE_315);
        assertTrue(metrics.candidateRecall() >= 0.90,
                () -> "candidate recall=" + metrics.candidateRecall()
                        + " missed=" + metrics.missedCandidates());
    }
}
