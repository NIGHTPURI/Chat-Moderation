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
import dev.chatmoderation.semantic.SemanticModerationResult;
import dev.chatmoderation.semantic.SemanticReviewRouter;
import dev.chatmoderation.semantic.SemanticRoutingDecision;
import dev.chatmoderation.semantic.openai.moderation.OpenAiModerationApiConfig;
import dev.chatmoderation.semantic.openai.moderation.OpenAiModerationApiProvider;
import dev.chatmoderation.semantic.openai.moderation.OpenAiModerationApiTelemetry;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class OpenAiModerationEvaluation {
    private static final int EXPECTED_CASES = 108;
    private static final long REQUEST_INTERVAL_MILLIS = 250;

    private OpenAiModerationEvaluation() {
    }

    public static void main(String[] args) {
        Optional<OpenAiModerationApiConfig> optionalConfig =
                OpenAiModerationApiConfig.fromEnvironment();
        if (optionalConfig.isEmpty()) {
            System.out.println("OpenAI Moderation API evaluation skipped: OPENAI_API_KEY is not set.");
            System.out.println("Set the key only in the process environment, then run:");
            System.out.println("  ./gradlew openAiModerationEvaluation");
            return;
        }

        List<SemanticEvaluationCase> cases = ValidationResources.loadSemanticEvaluation();
        if (cases.size() != EXPECTED_CASES) {
            throw new IllegalStateException("frozen semantic dataset must contain exactly "
                    + EXPECTED_CASES + " cases, found " + cases.size());
        }

        ChatModerationService deterministic = deterministicService();
        SemanticReviewRouter router = new SemanticReviewRouter();
        LocalHeuristicSemanticProvider localProvider = new LocalHeuristicSemanticProvider();
        OpenAiModerationApiProvider moderationOnlyProvider =
                new OpenAiModerationApiProvider(optionalConfig.orElseThrow());
        OpenAiModerationApiProvider moderationHybridProvider =
                new OpenAiModerationApiProvider(optionalConfig.orElseThrow());
        SemanticHybridModerator localHybrid = hybrid(deterministic, router, localProvider);
        SemanticHybridModerator moderationHybrid = hybrid(
                deterministic,
                router,
                moderationHybridProvider
        );

        List<ComparedCase> compared = new ArrayList<>();
        for (SemanticEvaluationCase testCase : cases) {
            ModerationResult deterministicResult = deterministic.moderate(testCase.message());
            String sanitized = sanitizedProviderMessage(testCase.message(), deterministicResult);
            SemanticModerationResult moderationOnly = moderationOnlyProvider.moderate(sanitized);
            throttle();
            SemanticHybridResult moderationHybridResult = moderationHybrid.moderate(
                    testCase.message()
            );
            if (moderationHybridResult.routedToSemanticProvider()) {
                throttle();
            }
            compared.add(new ComparedCase(
                    testCase,
                    deterministicResult,
                    toModerationResult(testCase.message(), localProvider.moderate(sanitized)),
                    localHybrid.moderate(testCase.message()).result(),
                    toModerationResult(testCase.message(), moderationOnly),
                    moderationHybridResult
            ));
        }

        System.out.println("Phase 3.11b OpenAI free Moderation API experiment");
        System.out.println("Model: " + OpenAiModerationApiConfig.MODEL);
        System.out.println("Endpoint: POST https://api.openai.com/v1/moderations");
        System.out.println("Dataset: frozen semantic-evaluation.tsv (" + cases.size() + " cases)");
        System.out.println("Router: frozen Phase 3.10 SemanticReviewRouter");
        System.out.println("Execution: sequential, at least " + REQUEST_INTERVAL_MILLIS
                + " ms between calls");
        printMetrics("A. deterministic only", compared, ComparedCase::deterministic);
        printMetrics("B. local-heuristic-v1 only", compared, ComparedCase::localOnly);
        printMetrics("C. deterministic + local-heuristic-v1 hybrid", compared,
                ComparedCase::localHybrid);
        printMetrics("D. OpenAI Moderation API only", compared, ComparedCase::moderationOnly);
        printMetrics("E. deterministic + OpenAI Moderation API hybrid", compared,
                item -> item.moderationHybrid().result());
        printCategoryComparison(compared);
        printRouting(compared, deterministic, router);
        printTelemetry("Moderation API only", moderationOnlyProvider.telemetry());
        printTelemetry("Moderation API hybrid", moderationHybridProvider.telemetry());
        printCombinedTelemetry(
                moderationOnlyProvider.telemetry(),
                moderationHybridProvider.telemetry()
        );
        printNativeCategoryStatistics(moderationOnlyProvider.telemetry());
        printIncorrectCases("Moderation API hybrid incorrect cases", compared,
                item -> item.moderationHybrid().result());
        System.out.println();
        System.out.println("API cost: $0");
    }

    private static ChatModerationService deterministicService() {
        return new DefaultChatModerationService(
                ValidationResources.loadKeywordsByReason(),
                ValidationResources.loadKeywordExceptions(),
                ValidationResources.loadAliasesByReason()
        );
    }

    private static SemanticHybridModerator hybrid(
            ChatModerationService deterministic,
            SemanticReviewRouter router,
            dev.chatmoderation.semantic.SemanticModerationProvider provider
    ) {
        return new SemanticHybridModerator(
                deterministic,
                router,
                provider,
                ProviderFailurePolicy.DETERMINISTIC_FALLBACK
        );
    }

    private static String sanitizedProviderMessage(
            String original,
            ModerationResult deterministicResult
    ) {
        return deterministicResult.action() == ModerationAction.MASK
                ? deterministicResult.outputMessage()
                : original.strip();
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

    private static void throttle() {
        try {
            Thread.sleep(REQUEST_INTERVAL_MILLIS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("evaluation interrupted during rate-limit delay", exception);
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
        int f1Denominator = 2 * truePositive + falsePositive + falseNegative;

        System.out.println();
        System.out.println(label);
        printPercent("accuracy", correct, cases.size());
        printPercent("precision", truePositive, truePositive + falsePositive);
        printPercent("recall", truePositive, truePositive + falseNegative);
        System.out.printf(Locale.ROOT, "F1: %.2f%%%n",
                f1Denominator == 0 ? 0.0 : 200.0 * truePositive / f1Denominator);
        printPercent("FPR", falsePositive, falsePositive + trueNegative);
        printPercent("FNR", falseNegative, falseNegative + truePositive);
        System.out.println("TP=" + truePositive + ", TN=" + trueNegative
                + ", FP=" + falsePositive + ", FN=" + falseNegative);
    }

    private static void printCategoryComparison(List<ComparedCase> cases) {
        System.out.println();
        System.out.println("Category accuracy (action only; provider taxonomies differ)");
        System.out.printf("%-24s %10s %10s %10s %10s %10s%n",
                "category", "det", "local", "local+det", "mod", "mod+det");
        for (SemanticEvaluationCategory category : SemanticEvaluationCategory.values()) {
            List<ComparedCase> subset = cases.stream()
                    .filter(item -> item.testCase().category() == category)
                    .toList();
            System.out.printf(Locale.ROOT, "%-24s %9.2f%% %9.2f%% %9.2f%% %9.2f%% %9.2f%%%n",
                    category,
                    accuracy(subset, ComparedCase::deterministic),
                    accuracy(subset, ComparedCase::localOnly),
                    accuracy(subset, ComparedCase::localHybrid),
                    accuracy(subset, ComparedCase::moderationOnly),
                    accuracy(subset, item -> item.moderationHybrid().result()));
        }
    }

    private static void printRouting(
            List<ComparedCase> cases,
            ChatModerationService deterministic,
            SemanticReviewRouter router
    ) {
        long semanticRouted = cases.stream()
                .filter(item -> item.moderationHybrid().routedToSemanticProvider()).count();
        List<EvaluationCase> holdout = ValidationResources.loadEvaluationHoldout();
        long holdoutRouted = holdout.stream().filter(testCase -> router.route(
                testCase.message(),
                deterministic.moderate(testCase.message())
        ).decision() == SemanticRoutingDecision.NEEDS_SEMANTIC_REVIEW).count();

        System.out.println();
        System.out.println("Routing (provider-independent frozen router)");
        System.out.println("semantic dataset: " + semanticRouted + " / " + cases.size());
        printPercent("semantic routing rate", (int) semanticRouted, cases.size());
        System.out.println("Phase 3.9 holdout: " + holdoutRouted + " / " + holdout.size());
        printPercent("holdout routing rate", (int) holdoutRouted, holdout.size());
    }

    private static void printTelemetry(
            String label,
            OpenAiModerationApiTelemetry.Snapshot telemetry
    ) {
        System.out.println();
        System.out.println(label + " network telemetry");
        System.out.println("requests=" + telemetry.requests() + ", success="
                + telemetry.successes() + ", error=" + telemetry.errors()
                + ", timeout=" + telemetry.timeouts() + ", 429=" + telemetry.rateLimited());
        System.out.println("HTTP statuses: " + telemetry.httpStatuses());
        System.out.printf(Locale.ROOT,
                "latency average=%.3f ms, p50=%.3f ms, p95=%.3f ms, p99=%.3f ms%n",
                telemetry.averageLatencyMillis(), telemetry.p50LatencyMillis(),
                telemetry.p95LatencyMillis(), telemetry.p99LatencyMillis());
    }

    private static void printCombinedTelemetry(
            OpenAiModerationApiTelemetry.Snapshot only,
            OpenAiModerationApiTelemetry.Snapshot hybrid
    ) {
        System.out.println();
        System.out.println("Combined live calls");
        System.out.println("requests=" + (only.requests() + hybrid.requests())
                + ", success=" + (only.successes() + hybrid.successes())
                + ", error=" + (only.errors() + hybrid.errors())
                + ", timeout=" + (only.timeouts() + hybrid.timeouts())
                + ", 429=" + (only.rateLimited() + hybrid.rateLimited()));
    }

    private static void printNativeCategoryStatistics(
            OpenAiModerationApiTelemetry.Snapshot telemetry
    ) {
        System.out.println();
        System.out.println("Provider-native category statistics (API-only 108 calls)");
        telemetry.categoryEvaluated().keySet().stream().sorted().forEach(category ->
                System.out.println(category + ": flagged="
                        + telemetry.categoryFlagged().getOrDefault(category, 0L)
                        + " / evaluated=" + telemetry.categoryEvaluated().get(category))
        );
    }

    private static void printIncorrectCases(
            String label,
            List<ComparedCase> cases,
            ResultSelector selector
    ) {
        System.out.println();
        System.out.println(label);
        for (ComparedCase item : cases) {
            ModerationResult actual = selector.select(item);
            if (correct(item.testCase(), actual)) {
                continue;
            }
            String type = !positive(item.testCase().expectedAction()) && positive(actual.action())
                    ? "FALSE POSITIVE"
                    : positive(item.testCase().expectedAction()) && !positive(actual.action())
                    ? "FALSE NEGATIVE" : "REASON/ACTION MISMATCH";
            System.out.println(type + " | " + item.testCase().category()
                    + " | " + item.testCase().message()
                    + " | expected=" + item.testCase().expectedAction()
                    + "/" + item.testCase().expectedReason()
                    + " actual=" + actual.action() + "/" + actual.reasons());
        }
    }

    private static boolean correct(SemanticEvaluationCase testCase, ModerationResult result) {
        return testCase.expectedAction() == result.action();
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

    @FunctionalInterface
    private interface ResultSelector {
        ModerationResult select(ComparedCase item);
    }

    private record ComparedCase(
            SemanticEvaluationCase testCase,
            ModerationResult deterministic,
            ModerationResult localOnly,
            ModerationResult localHybrid,
            ModerationResult moderationOnly,
            SemanticHybridResult moderationHybrid
    ) {
    }
}
