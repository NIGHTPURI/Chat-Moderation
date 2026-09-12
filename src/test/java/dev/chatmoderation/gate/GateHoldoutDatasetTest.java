package dev.chatmoderation.gate;

import dev.chatmoderation.router.RouterEvaluationResources;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GateHoldoutDatasetTest {
    @Test
    void sealedHoldoutHasAtLeastTwoHundredFiftyUniqueIndependentCases() {
        List<GateEvaluationCase> holdout = GateEvaluationResources.loadHoldout();
        Set<String> messages = new HashSet<>();
        holdout.forEach(item -> assertTrue(messages.add(item.message()), item.message()));
        assertTrue(holdout.size() >= 250);
        assertEquals(holdout.size(), messages.size());

        Set<String> excluded = new HashSet<>();
        GateEvaluationResources.loadCalibration().forEach(item -> excluded.add(item.message()));
        GateEvaluationResources.loadProductionLike().forEach(item -> excluded.add(item.message()));
        RouterEvaluationResources.loadCalibration().forEach(item -> excluded.add(item.message()));
        RouterEvaluationResources.loadHoldout().forEach(item -> excluded.add(item.message()));
        assertTrue(messages.stream().noneMatch(excluded::contains));
    }
}
