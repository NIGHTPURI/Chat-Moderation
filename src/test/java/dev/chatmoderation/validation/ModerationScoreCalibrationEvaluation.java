package dev.chatmoderation.validation;

import dev.chatmoderation.calibration.CategoryScoreDistribution;
import dev.chatmoderation.calibration.ModerationScorePolicy;
import dev.chatmoderation.calibration.ModerationScoreThresholds;
import dev.chatmoderation.calibration.ModerationThresholdCalibrator;
import dev.chatmoderation.calibration.PolicyCalibrationCase;
import dev.chatmoderation.calibration.PolicyCalibrationCategory;
import dev.chatmoderation.calibration.PolicyCalibrationResources;
import dev.chatmoderation.core.ChatModerationService;
import dev.chatmoderation.core.DefaultChatModerationService;
import dev.chatmoderation.core.model.ModerationAction;
import dev.chatmoderation.core.model.ModerationResult;
import dev.chatmoderation.semantic.SemanticReviewRouter;
import dev.chatmoderation.semantic.SemanticRoutingDecision;
import dev.chatmoderation.semantic.openai.moderation.OpenAiModerationApiConfig;
import dev.chatmoderation.semantic.openai.moderation.OpenAiModerationApiProvider;
import dev.chatmoderation.semantic.openai.moderation.OpenAiModerationApiTelemetry;
import dev.chatmoderation.semantic.openai.moderation.OpenAiModerationObservation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class ModerationScoreCalibrationEvaluation {
    private static final double TARGET_MAXIMUM_FPR = 0.05;
    private static final long REQUEST_INTERVAL_MILLIS = 250;

    private ModerationScoreCalibrationEvaluation() {
    }

    public static void main(String[] args) {
        Optional<OpenAiModerationApiConfig> optionalConfig =
                OpenAiModerationApiConfig.fromEnvironment();
        if (optionalConfig.isEmpty()) {
            System.out.println("Moderation score calibration skipped: OPENAI_API_KEY is not set.");
            System.out.println("Set the key only in the process environment, then run:");
            System.out.println("  ./gradlew moderationScoreCalibration");
            return;
        }

        ChatModerationService deterministic = deterministicService();
        SemanticReviewRouter router = new SemanticReviewRouter();
        List<PolicyCalibrationCase> calibrationCases =
                PolicyCalibrationResources.loadCalibration();
        OpenAiModerationApiProvider calibrationProvider =
                new OpenAiModerationApiProvider(optionalConfig.orElseThrow());
        List<ScoredCase> calibration = collect(
                "calibration",
                calibrationCases,
                deterministic,
                calibrationProvider
        );
        List<ModerationThresholdCalibrator.Sample> calibrationSamples = samples(calibration);

        System.out.println("Phase 3.12 policy adjudication and score calibration");
        System.out.println("Model: " + OpenAiModerationApiConfig.MODEL + " (API cost: $0)");
        System.out.println("Historical semantic-evaluation.tsv: benchmark only, not loaded");
        System.out.println("Calibration cases: " + calibration.size());
        printScoreDistributions(calibrationSamples);
        printCandidatePolicies(calibrationSamples);
        ModerationThresholdCalibrator.Selection selected =
                new ModerationThresholdCalibrator(TARGET_MAXIMUM_FPR)
                        .select(calibrationSamples);
        printSelection(selected);

        // The sealed holdout is deliberately not loaded until threshold selection is complete.
        List<PolicyCalibrationCase> holdoutCases = PolicyCalibrationResources.loadHoldout();
        OpenAiModerationApiProvider holdoutProvider =
                new OpenAiModerationApiProvider(optionalConfig.orElseThrow());
        List<ScoredCase> holdout = collect(
                "holdout",
                holdoutCases,
                deterministic,
                holdoutProvider
        );
        printHoldoutComparison(holdout, router, selected.thresholds());
        printCategoryComparison(holdout, router, selected.thresholds());
        printIncorrectCases(holdout, router, selected.thresholds());
        printTelemetry("Calibration", calibrationProvider.telemetry());
        printTelemetry("Holdout", holdoutProvider.telemetry());
        System.out.println("API cost: $0");
    }

    private static List<ScoredCase> collect(
            String label,
            List<PolicyCalibrationCase> cases,
            ChatModerationService deterministic,
            OpenAiModerationApiProvider provider
    ) {
        List<ScoredCase> scored = new ArrayList<>();
        for (PolicyCalibrationCase testCase : cases) {
            ModerationResult deterministicResult = deterministic.moderate(testCase.message());
            String providerMessage = deterministicResult.action() == ModerationAction.MASK
                    ? deterministicResult.outputMessage() : testCase.message().strip();
            provider.moderate(providerMessage);
            OpenAiModerationObservation observation = provider.lastObservation().orElse(null);
            if (observation != null) {
                scored.add(new ScoredCase(testCase, deterministicResult, observation));
            }
            throttle();
        }
        if (scored.size() != cases.size()) {
            printTelemetry(label + " incomplete", provider.telemetry());
            throw new IllegalStateException(label + " requires every API response; received "
                    + scored.size() + " / " + cases.size());
        }
        return List.copyOf(scored);
    }

    private static List<ModerationThresholdCalibrator.Sample> samples(List<ScoredCase> cases) {
        return cases.stream().map(item -> new ModerationThresholdCalibrator.Sample(
                item.testCase(),
                item.observation()
        )).toList();
    }

    private static void printScoreDistributions(
            List<ModerationThresholdCalibrator.Sample> samples
    ) {
        System.out.println();
        System.out.println("Category score distributions (positive=BLOCK, negative=ALLOW)");
        System.out.printf("%-28s %-8s %5s %11s %11s %11s %11s %11s%n",
                "category", "label", "n", "min", "median", "p90", "p95", "max");
        CategoryScoreDistribution.summarize(samples).entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    printDistribution(entry.getKey(), "positive", entry.getValue().positive());
                    printDistribution(entry.getKey(), "negative", entry.getValue().negative());
                });
    }

    private static void printDistribution(
            String category,
            String label,
            CategoryScoreDistribution.Summary summary
    ) {
        System.out.printf(Locale.ROOT,
                "%-28s %-8s %5d %11.7f %11.7f %11.7f %11.7f %11.7f%n",
                category, label, summary.count(), summary.minimum(), summary.median(),
                summary.p90(), summary.p95(), summary.maximum());
    }

    private static void printCandidatePolicies(
            List<ModerationThresholdCalibrator.Sample> samples
    ) {
        System.out.println();
        System.out.println("Best calibration candidates by FPR ceiling");
        for (double ceiling : List.of(0.0, 0.01, 0.02, 0.05)) {
            ModerationThresholdCalibrator.Selection selection =
                    new ModerationThresholdCalibrator(ceiling).select(samples);
            System.out.printf(Locale.ROOT,
                    "ceiling=%.2f%% abuse=%.9f sexual=%.9f recall=%.2f%% precision=%.2f%% FPR=%.2f%%%n",
                    ceiling * 100.0,
                    selection.thresholds().abuse(), selection.thresholds().sexual(),
                    selection.metrics().recall() * 100.0,
                    selection.metrics().precision() * 100.0,
                    selection.metrics().falsePositiveRate() * 100.0);
        }
    }

    private static void printSelection(ModerationThresholdCalibrator.Selection selected) {
        System.out.println();
        System.out.println("Selected threshold (calibration only)");
        System.out.printf(Locale.ROOT, "abuse=%.9f, sexual=%.9f%n",
                selected.thresholds().abuse(), selected.thresholds().sexual());
        printMetrics("Calibration custom threshold", selected.metrics());
    }

    private static void printHoldoutComparison(
            List<ScoredCase> cases,
            SemanticReviewRouter router,
            ModerationScoreThresholds thresholds
    ) {
        ModerationScorePolicy custom = new ModerationScorePolicy(thresholds);
        System.out.println();
        System.out.println("Sealed holdout comparison (" + cases.size() + " cases)");
        printMetrics("A. deterministic", metrics(cases,
                item -> positive(item.deterministic().action())));
        printMetrics("B. Moderation API default flagged", metrics(cases,
                item -> item.observation().flagged()));
        printMetrics("C. Moderation API custom score threshold", metrics(cases,
                item -> custom.blocks(item.observation())));
        printMetrics("D. deterministic + default moderation", metrics(cases,
                item -> hybridBlocks(item, router, item.observation().flagged())));
        printMetrics("E. deterministic + custom-threshold moderation", metrics(cases,
                item -> hybridBlocks(item, router, custom.blocks(item.observation()))));
    }

    private static void printCategoryComparison(
            List<ScoredCase> cases,
            SemanticReviewRouter router,
            ModerationScoreThresholds thresholds
    ) {
        ModerationScorePolicy custom = new ModerationScorePolicy(thresholds);
        System.out.println();
        System.out.println("Holdout category accuracy (action only)");
        System.out.printf("%-24s %9s %9s %9s %9s %9s%n",
                "category", "det", "default", "custom", "det+def", "det+cust");
        for (PolicyCalibrationCategory category : PolicyCalibrationCategory.values()) {
            List<ScoredCase> subset = cases.stream()
                    .filter(item -> item.testCase().category() == category).toList();
            System.out.printf(Locale.ROOT, "%-24s %8.2f%% %8.2f%% %8.2f%% %8.2f%% %8.2f%%%n",
                    category,
                    accuracy(subset, item -> positive(item.deterministic().action())),
                    accuracy(subset, item -> item.observation().flagged()),
                    accuracy(subset, item -> custom.blocks(item.observation())),
                    accuracy(subset, item -> hybridBlocks(
                            item, router, item.observation().flagged())),
                    accuracy(subset, item -> hybridBlocks(
                            item, router, custom.blocks(item.observation()))));
        }
    }

    private static void printIncorrectCases(
            List<ScoredCase> cases,
            SemanticReviewRouter router,
            ModerationScoreThresholds thresholds
    ) {
        ModerationScorePolicy custom = new ModerationScorePolicy(thresholds);
        System.out.println();
        System.out.println("Custom-threshold hybrid incorrect cases");
        for (ScoredCase item : cases) {
            boolean actual = hybridBlocks(item, router, custom.blocks(item.observation()));
            boolean expected = item.testCase().expectedAction() == ModerationAction.BLOCK;
            if (actual == expected) {
                continue;
            }
            System.out.println((actual ? "FALSE POSITIVE" : "FALSE NEGATIVE")
                    + " | " + item.testCase().category()
                    + " | " + item.testCase().message());
        }
    }

    private static boolean hybridBlocks(
            ScoredCase item,
            SemanticReviewRouter router,
            boolean providerBlocks
    ) {
        SemanticRoutingDecision route = router.route(
                item.testCase().message(),
                item.deterministic()
        ).decision();
        if (route != SemanticRoutingDecision.NEEDS_SEMANTIC_REVIEW) {
            return positive(item.deterministic().action());
        }
        return providerBlocks || item.deterministic().action() == ModerationAction.MASK;
    }

    private static ModerationThresholdCalibrator.BinaryMetrics metrics(
            List<ScoredCase> cases,
            Prediction prediction
    ) {
        int tp = 0;
        int tn = 0;
        int fp = 0;
        int fn = 0;
        for (ScoredCase item : cases) {
            boolean expected = item.testCase().expectedAction() == ModerationAction.BLOCK;
            boolean actual = prediction.blocks(item);
            if (expected && actual) tp++;
            else if (!expected && !actual) tn++;
            else if (!expected) fp++;
            else fn++;
        }
        return new ModerationThresholdCalibrator.BinaryMetrics(tp, tn, fp, fn);
    }

    private static void printMetrics(
            String label,
            ModerationThresholdCalibrator.BinaryMetrics metrics
    ) {
        System.out.printf(Locale.ROOT,
                "%s: accuracy=%.2f%% precision=%.2f%% recall=%.2f%% F1=%.2f%% FPR=%.2f%% FNR=%.2f%% TP=%d TN=%d FP=%d FN=%d%n",
                label,
                metrics.accuracy() * 100.0, metrics.precision() * 100.0,
                metrics.recall() * 100.0, metrics.f1() * 100.0,
                metrics.falsePositiveRate() * 100.0,
                metrics.falseNegativeRate() * 100.0,
                metrics.truePositive(), metrics.trueNegative(),
                metrics.falsePositive(), metrics.falseNegative());
    }

    private static double accuracy(List<ScoredCase> cases, Prediction prediction) {
        return metrics(cases, prediction).accuracy() * 100.0;
    }

    private static void printTelemetry(
            String label,
            OpenAiModerationApiTelemetry.Snapshot telemetry
    ) {
        System.out.println();
        System.out.println(label + " API telemetry");
        System.out.println("requests=" + telemetry.requests() + ", success="
                + telemetry.successes() + ", error=" + telemetry.errors()
                + ", timeout=" + telemetry.timeouts() + ", 429=" + telemetry.rateLimited());
        System.out.println("HTTP statuses: " + telemetry.httpStatuses());
        System.out.printf(Locale.ROOT,
                "latency average=%.3f ms, p50=%.3f ms, p95=%.3f ms, p99=%.3f ms%n",
                telemetry.averageLatencyMillis(), telemetry.p50LatencyMillis(),
                telemetry.p95LatencyMillis(), telemetry.p99LatencyMillis());
    }

    private static void throttle() {
        try {
            Thread.sleep(REQUEST_INTERVAL_MILLIS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("evaluation interrupted during rate-limit delay", exception);
        }
    }

    private static boolean positive(ModerationAction action) {
        return action != ModerationAction.ALLOW;
    }

    private static ChatModerationService deterministicService() {
        return new DefaultChatModerationService(
                ValidationResources.loadKeywordsByReason(),
                ValidationResources.loadKeywordExceptions(),
                ValidationResources.loadAliasesByReason()
        );
    }

    @FunctionalInterface
    private interface Prediction {
        boolean blocks(ScoredCase item);
    }

    private record ScoredCase(
            PolicyCalibrationCase testCase,
            ModerationResult deterministic,
            OpenAiModerationObservation observation
    ) {
    }
}
