package dev.chatmoderation.calibration;

import dev.chatmoderation.semantic.openai.moderation.OpenAiModerationObservation;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class ModerationScorePolicy {
    private final ModerationScoreThresholds thresholds;

    public ModerationScorePolicy(ModerationScoreThresholds thresholds) {
        this.thresholds = Objects.requireNonNull(thresholds, "thresholds must not be null");
    }

    public boolean blocks(OpenAiModerationObservation observation) {
        double abuse = maximumScore(observation.categoryScores(), "harassment", "hate");
        double sexual = maximumScore(observation.categoryScores(), "sexual");
        return abuse >= thresholds.abuse() || sexual >= thresholds.sexual();
    }

    static double maximumScore(Map<String, Double> scores, String... prefixes) {
        return scores.entrySet().stream()
                .filter(entry -> startsWithAny(entry.getKey(), prefixes))
                .mapToDouble(Map.Entry::getValue)
                .max()
                .orElse(0.0);
    }

    private static boolean startsWithAny(String category, String[] prefixes) {
        String normalized = category.toLowerCase(Locale.ROOT);
        for (String prefix : prefixes) {
            if (normalized.equals(prefix) || normalized.startsWith(prefix + "/")) {
                return true;
            }
        }
        return false;
    }
}
