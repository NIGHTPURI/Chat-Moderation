package dev.chatmoderation.validation;

import dev.chatmoderation.core.ChatModerationService;
import dev.chatmoderation.core.DefaultChatModerationService;
import dev.chatmoderation.core.model.ModerationAction;
import dev.chatmoderation.core.model.ModerationReason;
import dev.chatmoderation.core.model.ModerationResult;
import dev.chatmoderation.semantic.LocalHeuristicSemanticProvider;
import dev.chatmoderation.semantic.ProviderFailurePolicy;
import dev.chatmoderation.semantic.SemanticHybridModerator;
import dev.chatmoderation.semantic.SemanticHybridResult;
import dev.chatmoderation.semantic.SemanticModerationProvider;
import dev.chatmoderation.semantic.SemanticModerationResult;
import dev.chatmoderation.semantic.SemanticReviewRouter;
import dev.chatmoderation.semantic.SemanticRoutingDecision;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class SemanticModerationEvaluation {
    private static final int MINIMUM_CASES = 100;

    private SemanticModerationEvaluation() {
    }

    public static void main(String[] args) {
        List<SemanticEvaluationCase> cases = ValidationResources.loadSemanticEvaluation();
        if (cases.size() < MINIMUM_CASES) {
            throw new IllegalStateException("semantic dataset must contain at least "
                    + MINIMUM_CASES + " cases");
        }

        ChatModerationService deterministic = deterministicService();
        SemanticModerationProvider provider = new LocalHeuristicSemanticProvider();
        SemanticReviewRouter router = new SemanticReviewRouter();
        SemanticHybridModerator hybrid = new SemanticHybridModerator(
                deterministic,
                router,
                provider,
                ProviderFailurePolicy.DETERMINISTIC_FALLBACK
        );

        List<ComparedCase> compared = new ArrayList<>();
        List<Long> semanticOnlyLatencies = new ArrayList<>();
        List<Long> hybridLatencies = new ArrayList<>();
        int providerErrors = 0;
        int providerTimeouts = 0;

        for (SemanticEvaluationCase testCase : cases) {
            ModerationResult deterministicResult = deterministic.moderate(testCase.message());

            long semanticStarted = System.nanoTime();
            SemanticModerationResult semanticResult = provider.moderate(testCase.message());
            semanticOnlyLatencies.add(System.nanoTime() - semanticStarted);
            if (semanticResult.status() == SemanticModerationResult.Status.ERROR) {
                providerErrors++;
            } else if (semanticResult.status() == SemanticModerationResult.Status.TIMEOUT) {
                providerTimeouts++;
            }

            SemanticHybridResult hybridResult = hybrid.moderate(testCase.message());
            if (hybridResult.routedToSemanticProvider()) {
                hybridLatencies.add(hybridResult.semanticLatencyNanos());
                if (hybridResult.providerStatus() == SemanticModerationResult.Status.ERROR) {
                    providerErrors++;
                } else if (hybridResult.providerStatus() == SemanticModerationResult.Status.TIMEOUT) {
                    providerTimeouts++;
                }
            }
            compared.add(new ComparedCase(
                    testCase,
                    deterministicResult,
                    toModerationResult(testCase.message(), semanticResult),
                    hybridResult
            ));
        }

        System.out.println("Semantic moderation experiment; not a CI pass/fail criterion");
        System.out.println("Provider: local-heuristic-v1 (reproducible stand-in, not an AI model)");
        System.out.println("Dataset: semantic-evaluation.tsv (" + cases.size() + " cases)");
        printDatasetDistribution(cases);
        printMetrics("A. deterministic only", compared, ComparedCase::deterministicResult);
        printMetrics("B. semantic only", compared, ComparedCase::semanticOnlyResult);
        printMetrics("C. deterministic + semantic routing", compared,
                item -> item.hybridResult().result());
        printCategoryComparison(compared);
        printRouting(compared, deterministic, router);
        printLatency("semantic-only provider latency", semanticOnlyLatencies);
        printLatency("hybrid routed provider latency", hybridLatencies);
        System.out.println("provider errors: " + providerErrors);
        System.out.println("provider timeouts: " + providerTimeouts);
        printHybridFailures(compared);
    }

    private static ChatModerationService deterministicService() {
        return new DefaultChatModerationService(
                ValidationResources.loadKeywordsByReason(),
                ValidationResources.loadKeywordExceptions(),
                ValidationResources.loadAliasesByReason()
        );
    }

    private static ModerationResult toModerationResult(
            String message,
            SemanticModerationResult semantic
    ) {
        if (semantic.status() != SemanticModerationResult.Status.SUCCESS
                || semantic.decision() == SemanticModerationResult.Decision.UNKNOWN) {
            return ModerationResult.allow(message.strip());
        }
        return semantic.decision() == SemanticModerationResult.Decision.BLOCK
                ? ModerationResult.block(semantic.reasons().isEmpty()
                        ? List.of(ModerationReason.OTHER) : semantic.reasons())
                : ModerationResult.allow(message.strip());
    }

    private static void printDatasetDistribution(List<SemanticEvaluationCase> cases) {
        Map<SemanticEvaluationCategory, Integer> counts = new EnumMap<>(
                SemanticEvaluationCategory.class
        );
        cases.forEach(testCase -> counts.merge(testCase.category(), 1, Integer::sum));
        System.out.println();
        System.out.println("Dataset distribution");
        for (SemanticEvaluationCategory category : SemanticEvaluationCategory.values()) {
            System.out.printf("%-24s %3d%n", category, counts.getOrDefault(category, 0));
        }
    }

    private static void printMetrics(
            String label,
            List<ComparedCase> cases,
            ResultSelector selector
    ) {
        int correct = count(cases, item -> correct(item.testCase(), selector.select(item)));
        int truePositive = count(cases, item -> positive(item.testCase().expectedAction())
                && positive(selector.select(item).action()));
        int trueNegative = count(cases, item -> !positive(item.testCase().expectedAction())
                && !positive(selector.select(item).action()));
        int falsePositive = count(cases, item -> !positive(item.testCase().expectedAction())
                && positive(selector.select(item).action()));
        int falseNegative = count(cases, item -> positive(item.testCase().expectedAction())
                && !positive(selector.select(item).action()));

        System.out.println();
        System.out.println("============================================================");
        System.out.println(label);
        System.out.println("============================================================");
        System.out.println("total: " + cases.size());
        System.out.println("correct: " + correct);
        printPercent("accuracy", correct, cases.size());
        System.out.println("TP: " + truePositive + ", TN: " + trueNegative
                + ", FP: " + falsePositive + ", FN: " + falseNegative);
        printPercent("precision", truePositive, truePositive + falsePositive);
        printPercent("recall", truePositive, truePositive + falseNegative);
        int f1Denominator = 2 * truePositive + falsePositive + falseNegative;
        System.out.printf(Locale.ROOT, "F1: %.2f%%%n",
                f1Denominator == 0 ? 0.0 : 200.0 * truePositive / f1Denominator);
        printPercent("FPR", falsePositive, falsePositive + trueNegative);
        printPercent("FNR", falseNegative, falseNegative + truePositive);
        printConfusionMatrix(cases, selector);
    }

    private static void printConfusionMatrix(
            List<ComparedCase> cases,
            ResultSelector selector
    ) {
        int[][] matrix = new int[ModerationAction.values().length][ModerationAction.values().length];
        cases.forEach(item -> matrix[item.testCase().expectedAction().ordinal()]
                [selector.select(item).action().ordinal()]++);
        System.out.println("Action confusion matrix (Expected \\ Actual)");
        System.out.printf("%-10s %8s %8s %8s%n", "", "ALLOW", "MASK", "BLOCK");
        for (ModerationAction expected : ModerationAction.values()) {
            System.out.printf(Locale.ROOT, "%-10s %8d %8d %8d%n", expected,
                    matrix[expected.ordinal()][ModerationAction.ALLOW.ordinal()],
                    matrix[expected.ordinal()][ModerationAction.MASK.ordinal()],
                    matrix[expected.ordinal()][ModerationAction.BLOCK.ordinal()]);
        }
    }

    private static void printCategoryComparison(List<ComparedCase> cases) {
        System.out.println();
        System.out.println("Category accuracy");
        System.out.printf("%-24s %12s %12s %12s%n",
                "category", "deterministic", "semantic", "hybrid");
        for (SemanticEvaluationCategory category : SemanticEvaluationCategory.values()) {
            List<ComparedCase> subset = cases.stream()
                    .filter(item -> item.testCase().category() == category)
                    .toList();
            System.out.printf(Locale.ROOT, "%-24s %11.2f%% %11.2f%% %11.2f%%%n", category,
                    accuracy(subset, ComparedCase::deterministicResult),
                    accuracy(subset, ComparedCase::semanticOnlyResult),
                    accuracy(subset, item -> item.hybridResult().result()));
        }
    }

    private static void printRouting(
            List<ComparedCase> cases,
            ChatModerationService deterministic,
            SemanticReviewRouter router
    ) {
        long routed = cases.stream().filter(item -> item.hybridResult().routedToSemanticProvider())
                .count();
        List<EvaluationCase> holdout = ValidationResources.loadEvaluationHoldout();
        long holdoutRouted = holdout.stream().filter(testCase -> router.route(
                testCase.message(),
                deterministic.moderate(testCase.message())
        ).decision() == SemanticRoutingDecision.NEEDS_SEMANTIC_REVIEW).count();

        System.out.println();
        System.out.println("Routing");
        System.out.println("semantic dataset routed: " + routed + " / " + cases.size());
        printPercent("semantic dataset routing rate", (int) routed, cases.size());
        System.out.println("Phase 3.9 holdout routed (operational proxy): "
                + holdoutRouted + " / " + holdout.size());
        printPercent("holdout routing rate", (int) holdoutRouted, holdout.size());
    }

    private static void printLatency(String label, List<Long> latencies) {
        long[] sorted = latencies.stream().mapToLong(Long::longValue).sorted().toArray();
        System.out.println();
        System.out.println(label);
        System.out.println("calls: " + sorted.length);
        if (sorted.length == 0) {
            return;
        }
        double averageMicros = Arrays.stream(sorted).average().orElse(0.0) / 1_000.0;
        System.out.printf(Locale.ROOT, "average: %.3f us%n", averageMicros);
        System.out.printf(Locale.ROOT, "p50: %.3f us%n", percentile(sorted, 0.50) / 1_000.0);
        System.out.printf(Locale.ROOT, "p95: %.3f us%n", percentile(sorted, 0.95) / 1_000.0);
        System.out.printf(Locale.ROOT, "p99: %.3f us%n", percentile(sorted, 0.99) / 1_000.0);
    }

    private static void printHybridFailures(List<ComparedCase> cases) {
        System.out.println();
        System.out.println("Hybrid incorrect cases");
        for (ComparedCase item : cases) {
            ModerationResult actual = item.hybridResult().result();
            if (correct(item.testCase(), actual)) {
                continue;
            }
            String type = !positive(item.testCase().expectedAction()) && positive(actual.action())
                    ? "FALSE POSITIVE"
                    : positive(item.testCase().expectedAction()) && !positive(actual.action())
                    ? "FALSE NEGATIVE" : "MISMATCH";
            System.out.println(type + " | " + item.testCase().category()
                    + " | " + item.testCase().message()
                    + " | expected=" + item.testCase().expectedAction()
                    + "/" + item.testCase().expectedReason()
                    + " actual=" + actual.action() + "/" + actual.reasons());
        }
    }

    private static boolean correct(SemanticEvaluationCase testCase, ModerationResult result) {
        return testCase.expectedAction() == result.action()
                && (testCase.expectedReason() == null
                || result.reasons().contains(testCase.expectedReason()));
    }

    private static boolean positive(ModerationAction action) {
        return action != ModerationAction.ALLOW;
    }

    private static int count(
            List<ComparedCase> cases,
            java.util.function.Predicate<ComparedCase> predicate
    ) {
        return (int) cases.stream().filter(predicate).count();
    }

    private static double accuracy(List<ComparedCase> cases, ResultSelector selector) {
        int correct = count(cases, item -> correct(item.testCase(), selector.select(item)));
        return cases.isEmpty() ? 0.0 : 100.0 * correct / cases.size();
    }

    private static void printPercent(String label, int numerator, int denominator) {
        double value = denominator == 0 ? 0.0 : 100.0 * numerator / denominator;
        System.out.printf(Locale.ROOT, "%s: %.2f%%%n", label, value);
    }

    private static long percentile(long[] sorted, double quantile) {
        int index = (int) Math.ceil(quantile * sorted.length) - 1;
        return sorted[Math.max(0, index)];
    }

    @FunctionalInterface
    private interface ResultSelector {
        ModerationResult select(ComparedCase item);
    }

    private record ComparedCase(
            SemanticEvaluationCase testCase,
            ModerationResult deterministicResult,
            ModerationResult semanticOnlyResult,
            SemanticHybridResult hybridResult
    ) {
    }
}
