package dev.chatmoderation.gate;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GateCalibrationDatasetTest {
    @Test
    void calibrationHasAtLeastFourHundredUniqueCases() {
        List<GateEvaluationCase> cases = GateEvaluationResources.loadCalibration();
        assertTrue(cases.size() >= 400);
        assertEquals(cases.size(), cases.stream().map(GateEvaluationCase::message)
                .collect(java.util.stream.Collectors.toSet()).size());
        assertTrue(cases.stream().anyMatch(item -> item.expectedAction().name().equals("BLOCK")));
        assertTrue(cases.stream().anyMatch(item -> item.expectedAction().name().equals("ALLOW")));
    }

    @Test
    void productionLikeDatasetIsUniqueAndBenignHeavy() {
        List<GateEvaluationCase> cases = GateEvaluationResources.loadProductionLike();
        Set<String> messages = new HashSet<>();
        long allows = cases.stream().filter(item -> item.expectedAction().name().equals("ALLOW"))
                .count();
        cases.forEach(item -> assertTrue(messages.add(item.message())));
        assertTrue(allows >= cases.size() * 0.8);
    }
}
