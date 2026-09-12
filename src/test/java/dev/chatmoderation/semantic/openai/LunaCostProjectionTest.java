package dev.chatmoderation.semantic.openai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LunaCostProjectionTest {

    @Test
    void projectsFullAndRouterMonthlyCostFromObservedRequestCosts() {
        LunaCostProjection projection = new LunaCostProjection(0.0001, 0.00012, 0.06875);

        LunaCostProjection.MonthlyEstimate estimate = projection.estimate(100_000);

        assertEquals(10.0, estimate.fullCoverageUsd(), 0.000001);
        assertEquals(0.825, estimate.currentRouterUsd(), 0.000001);
    }

    @Test
    void rejectsInvalidInputs() {
        assertThrows(IllegalArgumentException.class,
                () -> new LunaCostProjection(-1.0, 0.0, 0.1));
        assertThrows(IllegalArgumentException.class,
                () -> new LunaCostProjection(0.0, 0.0, 1.1));
        LunaCostProjection projection = new LunaCostProjection(0.0, 0.0, 0.0);
        assertThrows(IllegalArgumentException.class, () -> projection.estimate(-1));
    }
}
