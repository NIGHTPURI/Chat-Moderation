package dev.chatmoderation.calibration;

import dev.chatmoderation.core.model.ModerationAction;
import dev.chatmoderation.semantic.openai.moderation.OpenAiModerationObservation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

public final class ModerationThresholdCalibrator {
    private final double maximumFalsePositiveRate;

    public ModerationThresholdCalibrator(double maximumFalsePositiveRate) {
        if (!Double.isFinite(maximumFalsePositiveRate)
                || maximumFalsePositiveRate < 0.0 || maximumFalsePositiveRate > 1.0) {
            throw new IllegalArgumentException("maximumFalsePositiveRate must be between 0 and 1");
        }
        this.maximumFalsePositiveRate = maximumFalsePositiveRate;
    }

    public Selection select(List<Sample> samples) {
        if (samples.isEmpty()) {
            throw new IllegalArgumentException("samples must not be empty");
        }
        List<Double> abuseCandidates = candidates(samples, ScoreKind.ABUSE);
        List<Double> sexualCandidates = candidates(samples, ScoreKind.SEXUAL);
        Selection best = null;
        for (double abuse : abuseCandidates) {
            for (double sexual : sexualCandidates) {
                ModerationScoreThresholds thresholds = new ModerationScoreThresholds(
                        abuse,
                        sexual
                );
                BinaryMetrics metrics = evaluate(samples, new ModerationScorePolicy(thresholds));
                if (metrics.falsePositiveRate() > maximumFalsePositiveRate) {
                    continue;
                }
                Selection candidate = new Selection(thresholds, metrics);
                if (best == null || better(candidate, best)) {
                    best = candidate;
                }
            }
        }
        if (best == null) {
            throw new IllegalStateException("no threshold satisfies the FPR constraint");
        }
        return best;
    }

    public static BinaryMetrics evaluate(
            List<Sample> samples,
            ModerationScorePolicy policy
    ) {
        int truePositive = 0;
        int trueNegative = 0;
        int falsePositive = 0;
        int falseNegative = 0;
        for (Sample sample : samples) {
            boolean expected = sample.testCase().expectedAction() == ModerationAction.BLOCK;
            boolean actual = policy.blocks(sample.observation());
            if (expected && actual) {
                truePositive++;
            } else if (!expected && !actual) {
                trueNegative++;
            } else if (!expected) {
                falsePositive++;
            } else {
                falseNegative++;
            }
        }
        return new BinaryMetrics(truePositive, trueNegative, falsePositive, falseNegative);
    }

    private boolean better(Selection candidate, Selection current) {
        Comparator<Selection> comparator = Comparator
                .comparingDouble((Selection item) -> item.metrics().recall())
                .thenComparingInt(item -> -item.metrics().falsePositive())
                .thenComparingDouble(item -> item.metrics().precision())
                .thenComparingDouble(item -> item.metrics().f1())
                .thenComparingDouble(item -> item.thresholds().abuse())
                .thenComparingDouble(item -> item.thresholds().sexual());
        return comparator.compare(candidate, current) > 0;
    }

    private List<Double> candidates(List<Sample> samples, ScoreKind kind) {
        TreeSet<Double> values = new TreeSet<>();
        values.add(0.0);
        double maximum = 0.0;
        for (Sample sample : samples) {
            double score = switch (kind) {
                case ABUSE -> ModerationScorePolicy.maximumScore(
                        sample.observation().categoryScores(),
                        "harassment", "hate"
                );
                case SEXUAL -> ModerationScorePolicy.maximumScore(
                        sample.observation().categoryScores(),
                        "sexual"
                );
            };
            values.add(score);
            maximum = Math.max(maximum, score);
        }
        values.add(maximum < 1.0 ? Math.nextUp(maximum) : 1.0);
        return new ArrayList<>(values);
    }

    private enum ScoreKind {
        ABUSE,
        SEXUAL
    }

    public record Sample(
            PolicyCalibrationCase testCase,
            OpenAiModerationObservation observation
    ) {
    }

    public record Selection(
            ModerationScoreThresholds thresholds,
            BinaryMetrics metrics
    ) {
    }

    public record BinaryMetrics(
            int truePositive,
            int trueNegative,
            int falsePositive,
            int falseNegative
    ) {
        public int total() {
            return truePositive + trueNegative + falsePositive + falseNegative;
        }

        public double accuracy() {
            return ratio(truePositive + trueNegative, total());
        }

        public double precision() {
            return ratio(truePositive, truePositive + falsePositive);
        }

        public double recall() {
            return ratio(truePositive, truePositive + falseNegative);
        }

        public double f1() {
            int denominator = 2 * truePositive + falsePositive + falseNegative;
            return denominator == 0 ? 0.0 : 2.0 * truePositive / denominator;
        }

        public double falsePositiveRate() {
            return ratio(falsePositive, falsePositive + trueNegative);
        }

        public double falseNegativeRate() {
            return ratio(falseNegative, falseNegative + truePositive);
        }

        private static double ratio(int numerator, int denominator) {
            return denominator == 0 ? 0.0 : (double) numerator / denominator;
        }
    }
}
