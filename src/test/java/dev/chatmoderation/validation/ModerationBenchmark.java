package dev.chatmoderation.validation;

import dev.chatmoderation.core.ChatModerationService;
import dev.chatmoderation.core.DefaultChatModerationService;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class ModerationBenchmark {
    private static final int WARMUP_ROUNDS = 20;
    private static final int MEASURED_ITERATIONS = 200;

    private ModerationBenchmark() {
    }

    public static void main(String[] args) {
        ChatModerationService service = new DefaultChatModerationService(
                ValidationResources.loadKeywordsByReason(),
                ValidationResources.loadKeywordExceptions(),
                ValidationResources.loadAliasesByReason()
        );
        List<String> messages = ValidationResources.loadBenchmarkMessages();

        runRounds(service, messages, WARMUP_ROUNDS, null);

        long[] latencies = new long[messages.size() * MEASURED_ITERATIONS];
        long startedAt = System.nanoTime();
        runRounds(service, messages, MEASURED_ITERATIONS, latencies);
        long totalNanos = System.nanoTime() - startedAt;
        Arrays.sort(latencies);

        System.out.println("Development benchmark; not a CI pass/fail criterion");
        System.out.println("dataset messages: " + messages.size());
        System.out.println("warmup rounds: " + WARMUP_ROUNDS);
        System.out.println("measured iterations: " + MEASURED_ITERATIONS);
        System.out.println("measured messages: " + latencies.length);
        System.out.printf(Locale.ROOT, "total time: %.3f ms%n", nanosToMillis(totalNanos));
        System.out.printf(
                Locale.ROOT,
                "average latency: %.3f µs%n",
                (double) totalNanos / latencies.length / 1_000.0
        );
        System.out.printf(Locale.ROOT, "p50: %.3f µs%n", nanosToMicros(percentile(latencies, 50)));
        System.out.printf(Locale.ROOT, "p95: %.3f µs%n", nanosToMicros(percentile(latencies, 95)));
        System.out.printf(Locale.ROOT, "p99: %.3f µs%n", nanosToMicros(percentile(latencies, 99)));
    }

    private static void runRounds(
            ChatModerationService service,
            List<String> messages,
            int rounds,
            long[] latencies
    ) {
        int measurementIndex = 0;
        for (int round = 0; round < rounds; round++) {
            for (String message : messages) {
                long startedAt = System.nanoTime();
                service.moderate(message);
                long elapsed = System.nanoTime() - startedAt;
                if (latencies != null) {
                    latencies[measurementIndex++] = elapsed;
                }
            }
        }
    }

    private static long percentile(long[] sortedValues, int percentile) {
        int index = (int) Math.ceil(percentile / 100.0 * sortedValues.length) - 1;
        return sortedValues[Math.max(index, 0)];
    }

    private static double nanosToMillis(long nanos) {
        return nanos / 1_000_000.0;
    }

    private static double nanosToMicros(long nanos) {
        return nanos / 1_000.0;
    }

}
