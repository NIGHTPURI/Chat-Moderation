package dev.chatmoderation.validation;

import dev.chatmoderation.calibration.PolicyCalibrationResources;
import dev.chatmoderation.gate.GateEvaluationCase;
import dev.chatmoderation.gate.GateEvaluationResources;
import dev.chatmoderation.router.RouterEvaluationResources;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfidenceGateDatasetIndependenceTest {
    @Test
    void allPhase315DatasetsAreDisjointFromEachOtherAndHistoricalSemanticData() {
        Set<String> historical = new HashSet<>();
        ValidationResources.loadSemanticEvaluation()
                .forEach(item -> historical.add(item.message()));
        PolicyCalibrationResources.loadCalibration()
                .forEach(item -> historical.add(item.message()));
        PolicyCalibrationResources.loadHoldout()
                .forEach(item -> historical.add(item.message()));
        RouterEvaluationResources.loadCalibration()
                .forEach(item -> historical.add(item.message()));
        RouterEvaluationResources.loadHoldout()
                .forEach(item -> historical.add(item.message()));

        Set<String> phase315 = new HashSet<>();
        assertIndependent(GateEvaluationResources.loadCalibration(), historical, phase315);
        assertIndependent(GateEvaluationResources.loadProductionLike(), historical, phase315);
        assertIndependent(GateEvaluationResources.loadHoldout(), historical, phase315);
    }

    private static void assertIndependent(
            List<GateEvaluationCase> cases,
            Set<String> historical,
            Set<String> phase315
    ) {
        for (GateEvaluationCase item : cases) {
            assertTrue(!historical.contains(item.message()),
                    () -> "historical overlap: " + item.message());
            assertTrue(phase315.add(item.message()),
                    () -> "Phase 3.15 overlap: " + item.message());
        }
    }
}
