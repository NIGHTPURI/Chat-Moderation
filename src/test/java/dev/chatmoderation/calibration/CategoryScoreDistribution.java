package dev.chatmoderation.calibration;

import dev.chatmoderation.core.model.ModerationAction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

public final class CategoryScoreDistribution {
    private CategoryScoreDistribution() {
    }

    public static Map<String, LabeledDistribution> summarize(
            List<ModerationThresholdCalibrator.Sample> samples
    ) {
        TreeSet<String> categories = new TreeSet<>();
        samples.forEach(sample -> categories.addAll(
                sample.observation().categoryScores().keySet()
        ));
        Map<String, LabeledDistribution> result = new TreeMap<>();
        for (String category : categories) {
            List<Double> positive = scores(samples, category, ModerationAction.BLOCK);
            List<Double> negative = scores(samples, category, ModerationAction.ALLOW);
            result.put(category, new LabeledDistribution(
                    Summary.of(positive),
                    Summary.of(negative)
            ));
        }
        return Map.copyOf(result);
    }

    private static List<Double> scores(
            List<ModerationThresholdCalibrator.Sample> samples,
            String category,
            ModerationAction expectedAction
    ) {
        List<Double> values = new ArrayList<>();
        for (ModerationThresholdCalibrator.Sample sample : samples) {
            if (sample.testCase().expectedAction() != expectedAction) {
                continue;
            }
            Double value = sample.observation().categoryScores().get(category);
            if (value != null) {
                values.add(value);
            }
        }
        return values;
    }

    public record LabeledDistribution(Summary positive, Summary negative) {
    }

    public record Summary(
            int count,
            double minimum,
            double median,
            double p90,
            double p95,
            double maximum
    ) {
        static Summary of(List<Double> values) {
            if (values.isEmpty()) {
                return new Summary(0, 0.0, 0.0, 0.0, 0.0, 0.0);
            }
            List<Double> sorted = new ArrayList<>(values);
            Collections.sort(sorted);
            return new Summary(
                    sorted.size(),
                    sorted.getFirst(),
                    percentile(sorted, 0.50),
                    percentile(sorted, 0.90),
                    percentile(sorted, 0.95),
                    sorted.getLast()
            );
        }

        private static double percentile(List<Double> sorted, double quantile) {
            int index = (int) Math.ceil(quantile * sorted.size()) - 1;
            return sorted.get(Math.max(0, index));
        }
    }
}
