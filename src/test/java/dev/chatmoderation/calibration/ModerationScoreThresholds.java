package dev.chatmoderation.calibration;

public record ModerationScoreThresholds(double abuse, double sexual) {
    public ModerationScoreThresholds {
        requireProbability("abuse", abuse);
        requireProbability("sexual", sexual);
    }

    private static void requireProbability(String name, double value) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " threshold must be between 0 and 1");
        }
    }
}
