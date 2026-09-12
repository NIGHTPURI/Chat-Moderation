package dev.chatmoderation.semantic.openai.moderation;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;

public final class OpenAiModerationApiTelemetry {
    private final LongAdder requests = new LongAdder();
    private final LongAdder successes = new LongAdder();
    private final LongAdder errors = new LongAdder();
    private final LongAdder timeouts = new LongAdder();
    private final LongAdder rateLimited = new LongAdder();
    private final Map<Integer, Long> httpStatuses = new LinkedHashMap<>();
    private final Map<String, Long> categoryEvaluated = new LinkedHashMap<>();
    private final Map<String, Long> categoryFlagged = new LinkedHashMap<>();
    private final List<OpenAiModerationObservation> observations = new java.util.ArrayList<>();
    private long[] latenciesNanos = new long[128];
    private int latencyCount;

    void recordRequest() {
        requests.increment();
    }

    synchronized void recordHttpStatus(int statusCode) {
        httpStatuses.merge(statusCode, 1L, Long::sum);
        if (statusCode == 429) {
            rateLimited.increment();
        }
    }

    synchronized void recordSuccess(OpenAiModerationObservation observation) {
        successes.increment();
        observations.add(observation);
        observation.categories().forEach((category, flagged) -> {
            categoryEvaluated.merge(category, 1L, Long::sum);
            if (flagged) {
                categoryFlagged.merge(category, 1L, Long::sum);
            }
        });
    }

    void recordError() {
        errors.increment();
    }

    void recordTimeout() {
        timeouts.increment();
    }

    synchronized void recordLatency(long nanos) {
        if (latencyCount == latenciesNanos.length) {
            latenciesNanos = Arrays.copyOf(latenciesNanos, latenciesNanos.length * 2);
        }
        latenciesNanos[latencyCount++] = nanos;
    }

    public synchronized Snapshot snapshot() {
        long[] sorted = Arrays.copyOf(latenciesNanos, latencyCount);
        Arrays.sort(sorted);
        return new Snapshot(
                requests.sum(),
                successes.sum(),
                errors.sum(),
                timeouts.sum(),
                rateLimited.sum(),
                Map.copyOf(httpStatuses),
                Map.copyOf(categoryEvaluated),
                Map.copyOf(categoryFlagged),
                List.copyOf(observations),
                averageMillis(sorted),
                percentileMillis(sorted, 0.50),
                percentileMillis(sorted, 0.95),
                percentileMillis(sorted, 0.99)
        );
    }

    private static double averageMillis(long[] sorted) {
        return sorted.length == 0 ? 0.0 : Arrays.stream(sorted).average().orElse(0.0) / 1_000_000.0;
    }

    private static double percentileMillis(long[] sorted, double quantile) {
        if (sorted.length == 0) {
            return 0.0;
        }
        int index = (int) Math.ceil(quantile * sorted.length) - 1;
        return sorted[Math.max(0, index)] / 1_000_000.0;
    }

    public record Snapshot(
            long requests,
            long successes,
            long errors,
            long timeouts,
            long rateLimited,
            Map<Integer, Long> httpStatuses,
            Map<String, Long> categoryEvaluated,
            Map<String, Long> categoryFlagged,
            List<OpenAiModerationObservation> observations,
            double averageLatencyMillis,
            double p50LatencyMillis,
            double p95LatencyMillis,
            double p99LatencyMillis
    ) {
    }
}
