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
import dev.chatmoderation.semantic.openai.OpenAiProviderTelemetry;
import dev.chatmoderation.semantic.openai.OpenAiSemanticModerationProvider;
import dev.chatmoderation.semantic.openai.OpenAiSemanticProviderConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class RealSemanticModerationEvaluation {
    private static final int EXPECTED_CASES = 108;

    private RealSemanticModerationEvaluation() {
    }

    public static void main(String[] args) {
        Optional<OpenAiSemanticProviderConfig> optionalConfig =
                OpenAiSemanticProviderConfig.fromEnvironment();
        if (optionalConfig.isEmpty()) {
            printCredentialGuidance();
            return;
        }

        List<SemanticEvaluationCase> cases = ValidationResources.loadSemanticEvaluation();
        if (cases.size() != EXPECTED_CASES) {
            throw new IllegalStateException("frozen semantic dataset must contain exactly "
                    + EXPECTED_CASES + " cases, found " + cases.size());
        }

        OpenAiSemanticProviderConfig config = optionalConfig.orElseThrow();
        ChatModerationService deterministic = deterministicService();
        SemanticReviewRouter router = new SemanticReviewRouter();
        LocalHeuristicSemanticProvider localProvider = new LocalHeuristicSemanticProvider();
        OpenAiSemanticModerationProvider realOnlyProvider =
                new OpenAiSemanticModerationProvider(config);
        OpenAiSemanticModerationProvider realHybridProvider =
                new OpenAiSemanticModerationProvider(config);
        SemanticHybridModerator localHybrid = hybrid(deterministic, router, localProvider);
        SemanticHybridModerator realHybrid = hybrid(deterministic, router, realHybridProvider);

        List<ComparedCase> compared = new ArrayList<>();
        for (SemanticEvaluationCase testCase : cases) {
            ModerationResult deterministicResult = deterministic.moderate(testCase.message());
            String sanitizedProviderMessage = sanitizedProviderMessage(
                    testCase.message(),
                    deterministicResult
            );
            SemanticModerationResult localOnly = localProvider.moderate(sanitizedProviderMessage);
            SemanticModerationResult realOnly = realOnlyProvider.moderate(sanitizedProviderMessage);
            compared.add(new ComparedCase(
                    testCase,
                    deterministicResult,
                    toModerationResult(testCase.message(), localOnly),
                    localHybrid.moderate(testCase.message()).result(),
                    toModerationResult(testCase.message(), realOnly),
                    realHybrid.moderate(testCase.message())
            ));
        }

        System.out.println("Phase 3.11 real semantic moderation experiment");
        System.out.println("Dataset: frozen semantic-evaluation.tsv (" + cases.size() + " cases)");
        System.out.println("Router: frozen Phase 3.10 SemanticReviewRouter");
        System.out.println("Real provider: OpenAI Responses API");
        System.out.println("Model: " + config.model());
        printMetrics("A. deterministic only", compared, ComparedCase::deterministic);
        printMetrics("B. local-heuristic-v1 only", compared, ComparedCase::localOnly);
        printMetrics("C. deterministic + local-heuristic-v1 hybrid", compared,
                ComparedCase::localHybrid);
        printMetrics("D. real semantic provider only", compared, ComparedCase::realOnly);
        printMetrics("E. deterministic + real semantic provider hybrid", compared,
                item -> item.realHybrid().result());
        printCategoryComparison(compared);
        printRouting(compared, deterministic, router);
        printTelemetry("Real provider only", realOnlyProvider.telemetry());
        printTelemetry("Real provider hybrid", realHybridProvider.telemetry());
        printCombinedUsageAndCost(config, realOnlyProvider.telemetry(), realHybridProvider.telemetry());
        printIncorrectCases("Real provider only incorrect cases", compared,
                ComparedCase::realOnly);
        printIncorrectCases("Real provider hybrid incorrect cases", compared,
                item -> item.realHybrid().result());
    }

    private static void printCredentialGuidance() {
        System.out.println("Real semantic evaluation skipped: OPENAI_API_KEY is not set.");
        System.out.println("Set the key only in the process environment, then run:");
        System.out.println("  ./gradlew realSemanticModerationEvaluation");
        System.out.println("Optional: OPENAI_SEMANTIC_MODEL and OPENAI_SEMANTIC_TIMEOUT_MS");
        System.out.println("For cost estimation, set all three price environment variables:");
        System.out.println("  OPENAI_SEMANTIC_INPUT_USD_PER_MILLION");
        System.out.println("  OPENAI_SEMANTIC_CACHED_INPUT_USD_PER_MILLION");
        System.out.println("  OPENAI_SEMANTIC_OUTPUT_USD_PER_MILLION");
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
            SemanticModerationProvider provider
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
        System.out.println("Category accuracy (action and expected core reason)");
        System.out.printf("%-24s %10s %10s %10s %10s %10s%n",
                "category", "det", "local", "local+det", "real", "real+det");
        for (SemanticEvaluationCategory category : SemanticEvaluationCategory.values()) {
            List<ComparedCase> subset = cases.stream()
                    .filter(item -> item.testCase().category() == category)
                    .toList();
            System.out.printf(Locale.ROOT, "%-24s %9.2f%% %9.2f%% %9.2f%% %9.2f%% %9.2f%%%n",
                    category,
                    accuracy(subset, ComparedCase::deterministic),
                    accuracy(subset, ComparedCase::localOnly),
                    accuracy(subset, ComparedCase::localHybrid),
                    accuracy(subset, ComparedCase::realOnly),
                    accuracy(subset, item -> item.realHybrid().result()));
        }
    }

    private static void printRouting(
            List<ComparedCase> cases,
            ChatModerationService deterministic,
            SemanticReviewRouter router
    ) {
        long semanticRouted = cases.stream()
                .filter(item -> item.realHybrid().routedToSemanticProvider()).count();
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

    private static void printTelemetry(String label, OpenAiProviderTelemetry.Snapshot snapshot) {
        System.out.println();
        System.out.println(label + " network telemetry");
        System.out.println(snapshot.format());
    }

    private static void printCombinedUsageAndCost(
            OpenAiSemanticProviderConfig config,
            OpenAiProviderTelemetry.Snapshot only,
            OpenAiProviderTelemetry.Snapshot hybrid
    ) {
        long requests = only.requests() + hybrid.requests();
        long input = only.inputTokens() + hybrid.inputTokens();
        long cachedInput = only.cachedInputTokens() + hybrid.cachedInputTokens();
        long output = only.outputTokens() + hybrid.outputTokens();
        System.out.println();
        System.out.println("Combined real-provider usage");
        System.out.println("requests=" + requests + ", input tokens=" + input
                + " (cached=" + cachedInput + "), output tokens=" + output);
        config.pricing().ifPresentOrElse(
                pricing -> System.out.printf(Locale.ROOT, "estimated cost: $%.6f USD%n",
                        pricing.estimateUsd(input, cachedInput, output)),
                () -> System.out.println("estimated cost: unavailable (token prices not configured)")
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

    @FunctionalInterface
    private interface ResultSelector {
        ModerationResult select(ComparedCase item);
    }

    private record ComparedCase(
            SemanticEvaluationCase testCase,
            ModerationResult deterministic,
            ModerationResult localOnly,
            ModerationResult localHybrid,
            ModerationResult realOnly,
            SemanticHybridResult realHybrid
    ) {
    }
}
