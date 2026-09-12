package dev.chatmoderation.semantic.openai;

import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.atomic.LongAdder;

public final class OpenAiProviderTelemetry {
    private final LongAdder requests = new LongAdder();
    private final LongAdder successes = new LongAdder();
    private final LongAdder errors = new LongAdder();
    private final LongAdder timeouts = new LongAdder();
    private final LongAdder rateLimited = new LongAdder();
    private final LongAdder inputTokens = new LongAdder();
    private final LongAdder cachedInputTokens = new LongAdder();
    private final LongAdder outputTokens = new LongAdder();
    private final LongAdder totalTokens = new LongAdder();
    private long[] latenciesNanos = new long[128];
    private int latencyCount;

    void recordRequest() {
        requests.increment();
    }

    void recordSuccess(long input, long cachedInput, long output, long total) {
        successes.increment();
        inputTokens.add(input);
        cachedInputTokens.add(cachedInput);
        outputTokens.add(output);
        totalTokens.add(total);
    }

    void recordError() {
        errors.increment();
    }

    void recordTimeout() {
        timeouts.increment();
    }

    void recordRateLimit() {
        rateLimited.increment();
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
                inputTokens.sum(),
                cachedInputTokens.sum(),
                outputTokens.sum(),
                totalTokens.sum(),
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
            long inputTokens,
            long cachedInputTokens,
            long outputTokens,
            long totalTokens,
            double averageLatencyMillis,
            double p50LatencyMillis,
            double p95LatencyMillis,
            double p99LatencyMillis
    ) {
        public String format() {
            return String.format(Locale.ROOT,
                    "requests=%d, success=%d, error=%d, timeout=%d, 429=%d%n"
                            + "input tokens=%d (cached=%d), output tokens=%d, total tokens=%d%n"
                            + "latency average=%.3f ms, p50=%.3f ms, p95=%.3f ms, p99=%.3f ms",
                    requests, successes, errors, timeouts, rateLimited,
                    inputTokens, cachedInputTokens, outputTokens, totalTokens,
                    averageLatencyMillis, p50LatencyMillis, p95LatencyMillis, p99LatencyMillis);
        }
    }
}
