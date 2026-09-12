package dev.chatmoderation.validation;

import dev.chatmoderation.calibration.ModerationThresholdCalibrator;
import dev.chatmoderation.calibration.PolicyCalibrationCase;
import dev.chatmoderation.calibration.PolicyCalibrationResources;
import dev.chatmoderation.core.ChatModerationService;
import dev.chatmoderation.core.DefaultChatModerationService;
import dev.chatmoderation.core.model.ModerationAction;
import dev.chatmoderation.core.model.ModerationResult;
import dev.chatmoderation.semantic.SemanticModerationResult;
import dev.chatmoderation.semantic.SemanticRoute;
import dev.chatmoderation.semantic.SemanticReviewRouter;
import dev.chatmoderation.semantic.SemanticRoutingDecision;
import dev.chatmoderation.semantic.openai.LunaCostProjection;
import dev.chatmoderation.semantic.openai.OpenAiProviderTelemetry;
import dev.chatmoderation.semantic.openai.OpenAiSemanticModerationProvider;
import dev.chatmoderation.semantic.openai.OpenAiSemanticProviderConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class LunaBaselineEvaluation {
    private static final String REQUIRED_MODEL = "gpt-5.6-luna";
    private static final int EXPECTED_SEMANTIC_CASES = 108;
    private static final int EXPECTED_POLICY_HOLDOUT_CASES = 165;
    private static final long REQUEST_INTERVAL_MILLIS = 100;
    private static final List<Long> MONTHLY_CHAT_VOLUMES = List.of(
            10_000L, 100_000L, 1_000_000L, 10_000_000L
    );

    private LunaBaselineEvaluation() {
    }

    public static void main(String[] args) {
        Optional<OpenAiSemanticProviderConfig> optionalConfig =
                OpenAiSemanticProviderConfig.fromEnvironment();
        if (optionalConfig.isEmpty()) {
            printCredentialGuidance();
            return;
        }
        OpenAiSemanticProviderConfig config = optionalConfig.orElseThrow();
        if (!REQUIRED_MODEL.equals(config.model())) {
            throw new IllegalArgumentException("Phase 3.13 requires model " + REQUIRED_MODEL
                    + "; configured model was " + config.model());
        }
        if (config.pricing().isEmpty()) {
            printPricingGuidance();
            return;
        }

        ChatModerationService deterministic = deterministicService();
        SemanticReviewRouter router = new SemanticReviewRouter();
        OpenAiSemanticModerationProvider provider = new OpenAiSemanticModerationProvider(config);

        List<BenchmarkCase> semanticCases = semanticCases();
        List<BenchmarkCase> policyCases = policyHoldoutCases();
        List<EvaluatedCase> semantic = evaluate(
                semanticCases, deterministic, router, provider
        );
        List<EvaluatedCase> policy = evaluate(
                policyCases, deterministic, router, provider
        );

        System.out.println("Phase 3.13 GPT-5.6 Luna baseline evaluation");
        System.out.println("Model: " + config.model());
        System.out.println("Prompt/router/datasets: frozen before this run");
        printDataset("Frozen semantic-evaluation.tsv", semantic);
        printRouterAnalysis("Frozen semantic-evaluation.tsv", semantic);
        printOmniHistoricalComparison(semantic);
        printDataset("Phase 3.12 sealed policy holdout", policy);
        printRouterAnalysis("Phase 3.12 sealed policy holdout", policy);
        printRouterLosses(semantic, policy);

        OpenAiProviderTelemetry.Snapshot telemetry = provider.telemetry();
        printTelemetry("Actual Luna calls (one shared prediction per message)", telemetry);
        printCost(config, telemetry, deterministic, router);
    }

    private static List<EvaluatedCase> evaluate(
            List<BenchmarkCase> cases,
            ChatModerationService deterministic,
            SemanticReviewRouter router,
            OpenAiSemanticModerationProvider provider
    ) {
        List<EvaluatedCase> evaluated = new ArrayList<>();
        for (BenchmarkCase testCase : cases) {
            ModerationResult deterministicResult = deterministic.moderate(testCase.message());
            SemanticModerationResult only = provider.moderate(
                    sanitizedMessage(testCase.message(), deterministicResult)
            );
            throttle();
            SemanticRoute route = router.route(testCase.message(), deterministicResult);
            evaluated.add(new EvaluatedCase(
                    testCase,
                    deterministicResult,
                    only,
                    route.decision(),
                    hybridBlocks(deterministicResult, route, only)
            ));
        }
        return List.copyOf(evaluated);
    }

    private static void printDataset(String label, List<EvaluatedCase> cases) {
        System.out.println();
        System.out.println("============================================================");
        System.out.println(label + " (" + cases.size() + " cases)");
        System.out.println("============================================================");
        printMetrics("A. Luna-only", metrics(cases, LunaBaselineEvaluation::onlyBlocks));
        printMetrics("B. Current frozen router + Luna",
                metrics(cases, EvaluatedCase::hybridBlocks));
        printCategoryAccuracy(cases);
        printIncorrectCases("Luna-only", cases, LunaBaselineEvaluation::onlyBlocks);
        printIncorrectCases("Current frozen router + Luna", cases,
                EvaluatedCase::hybridBlocks);
    }

    private static void printCategoryAccuracy(List<EvaluatedCase> cases) {
        System.out.println();
        System.out.printf("%-28s %5s %10s %10s %10s %10s %8s %9s%n",
                "category", "n", "only acc", "router acc", "only rec", "router rec",
                "only FP", "router FP");
        cases.stream().map(item -> item.testCase().category()).distinct().forEach(category -> {
            List<EvaluatedCase> subset = cases.stream()
                    .filter(item -> item.testCase().category().equals(category)).toList();
            ModerationThresholdCalibrator.BinaryMetrics only = metrics(
                    subset, LunaBaselineEvaluation::onlyBlocks
            );
            ModerationThresholdCalibrator.BinaryMetrics routed = metrics(
                    subset, EvaluatedCase::hybridBlocks
            );
            System.out.printf(Locale.ROOT,
                    "%-28s %5d %9.2f%% %9.2f%% %9.2f%% %9.2f%% %8d %9d%n",
                    category, subset.size(), only.accuracy() * 100.0,
                    routed.accuracy() * 100.0, only.recall() * 100.0,
                    routed.recall() * 100.0, only.falsePositive(), routed.falsePositive());
        });
    }

    private static void printRouterAnalysis(String label, List<EvaluatedCase> cases) {
        long routed = cases.stream().filter(LunaBaselineEvaluation::routed).count();
        long expectedBlocks = cases.stream().filter(LunaBaselineEvaluation::expectedBlock).count();
        long routedBlocks = cases.stream().filter(LunaBaselineEvaluation::expectedBlock)
                .filter(LunaBaselineEvaluation::routed).count();
        long missedBlocks = expectedBlocks - routedBlocks;
        double onlyRecall = metrics(cases, LunaBaselineEvaluation::onlyBlocks).recall();
        double hybridRecall = metrics(cases,
                EvaluatedCase::hybridBlocks).recall();

        System.out.println();
        System.out.println(label + " router analysis");
        printRate("routing rate", routed, cases.size());
        printRate("semantic candidate recall", routedBlocks, expectedBlocks);
        System.out.println("routed BLOCK count=" + routedBlocks
                + ", missed BLOCK count=" + missedBlocks);
        System.out.printf(Locale.ROOT, "Luna-only vs hybrid recall gap: %.2f percentage points%n",
                (onlyRecall - hybridRecall) * 100.0);
    }

    private static void printRouterLosses(
            List<EvaluatedCase> semantic,
            List<EvaluatedCase> policy
    ) {
        System.out.println();
        System.out.println("Router-caused losses (Luna-only BLOCK, hybrid ALLOW)");
        List<EvaluatedCase> combined = new ArrayList<>(semantic);
        combined.addAll(policy);
        combined.stream()
                .filter(LunaBaselineEvaluation::expectedBlock)
                .filter(LunaBaselineEvaluation::onlyBlocks)
                .filter(item -> !item.hybridBlocks())
                .filter(item -> !routed(item))
                .forEach(item -> System.out.println(item.testCase().dataset() + " | "
                        + item.testCase().category() + " | " + item.testCase().message()));
    }

    private static void printIncorrectCases(
            String label,
            List<EvaluatedCase> cases,
            Prediction prediction
    ) {
        System.out.println();
        System.out.println(label + " incorrect cases");
        cases.stream().filter(item -> prediction.blocks(item) != expectedBlock(item))
                .forEach(item -> System.out.println(
                        (prediction.blocks(item) ? "FALSE POSITIVE" : "FALSE NEGATIVE")
                                + " | " + item.testCase().category()
                                + " | " + item.testCase().message()
                ));
    }

    private static void printOmniHistoricalComparison(List<EvaluatedCase> semantic) {
        ModerationThresholdCalibrator.BinaryMetrics luna = metrics(
                semantic, LunaBaselineEvaluation::onlyBlocks
        );
        System.out.println();
        System.out.println("Historical omni-moderation-latest default comparison");
        System.out.println("omni: accuracy=49.07%, precision=100.00%, recall=14.06%, "
                + "FPR=0.00%, FNR=85.94%, TP=9, TN=44, FP=0, FN=55");
        System.out.printf(Locale.ROOT,
                "Luna delta: accuracy=%+.2f pp, recall=%+.2f pp, FPR=%+.2f pp%n",
                luna.accuracy() * 100.0 - 49.07,
                luna.recall() * 100.0 - 14.06,
                luna.falsePositiveRate() * 100.0);
        System.out.println("Phase 3.12 per-category omni output is not persisted in the repository; "
                + "the policy category table above is emitted for direct report comparison.");
    }

    private static void printTelemetry(
            String label,
            OpenAiProviderTelemetry.Snapshot telemetry
    ) {
        System.out.println();
        System.out.println(label + " telemetry");
        System.out.println(telemetry.format());
    }

    private static void printCost(
            OpenAiSemanticProviderConfig config,
            OpenAiProviderTelemetry.Snapshot telemetry,
            ChatModerationService deterministic,
            SemanticReviewRouter router
    ) {
        OpenAiSemanticProviderConfig.TokenPricing pricing = config.pricing().orElseThrow();
        double experimentCost = pricing.estimateUsd(
                telemetry.inputTokens(), telemetry.cachedInputTokens(), telemetry.outputTokens()
        );
        double averageCost = perRequest(experimentCost, telemetry.requests());
        double operationalRoutingRate = operationalRoutingRate(deterministic, router);
        LunaCostProjection projection = new LunaCostProjection(
                averageCost, averageCost, operationalRoutingRate
        );

        System.out.println();
        System.out.println("Observed cost and monthly simulation");
        System.out.printf(Locale.ROOT,
                "configured price / 1M tokens: input=$%.6f cached=$%.6f output=$%.6f%n",
                pricing.inputUsdPerMillion(), pricing.cachedInputUsdPerMillion(),
                pricing.outputUsdPerMillion());
        System.out.printf(Locale.ROOT,
                "experiment cost=$%.6f, average cost/request=$%.8f%n",
                experimentCost, averageCost);
        System.out.printf(Locale.ROOT,
                "Phase 3.9 operational holdout routing rate=%.2f%%%n",
                operationalRoutingRate * 100.0);
        System.out.printf("%-14s %18s %20s%n", "monthly chats", "Luna 100%", "current router");
        for (long volume : MONTHLY_CHAT_VOLUMES) {
            LunaCostProjection.MonthlyEstimate estimate = projection.estimate(volume);
            System.out.printf(Locale.ROOT, "%,14d $%17.4f $%19.4f%n",
                    volume, estimate.fullCoverageUsd(), estimate.currentRouterUsd());
        }
    }

    private static double operationalRoutingRate(
            ChatModerationService deterministic,
            SemanticReviewRouter router
    ) {
        List<EvaluationCase> holdout = ValidationResources.loadEvaluationHoldout();
        long routed = holdout.stream().filter(testCase -> router.route(
                testCase.message(), deterministic.moderate(testCase.message())
        ).decision() == SemanticRoutingDecision.NEEDS_SEMANTIC_REVIEW).count();
        return holdout.isEmpty() ? 0.0 : (double) routed / holdout.size();
    }

    private static ModerationThresholdCalibrator.BinaryMetrics metrics(
            List<EvaluatedCase> cases,
            Prediction prediction
    ) {
        int tp = 0;
        int tn = 0;
        int fp = 0;
        int fn = 0;
        for (EvaluatedCase item : cases) {
            boolean expected = expectedBlock(item);
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
                "%s: accuracy=%.2f%% precision=%.2f%% recall=%.2f%% F1=%.2f%% "
                        + "FPR=%.2f%% FNR=%.2f%% TP=%d TN=%d FP=%d FN=%d%n",
                label, metrics.accuracy() * 100.0, metrics.precision() * 100.0,
                metrics.recall() * 100.0, metrics.f1() * 100.0,
                metrics.falsePositiveRate() * 100.0,
                metrics.falseNegativeRate() * 100.0,
                metrics.truePositive(), metrics.trueNegative(),
                metrics.falsePositive(), metrics.falseNegative());
    }

    private static boolean onlyBlocks(EvaluatedCase item) {
        return item.only().status() == SemanticModerationResult.Status.SUCCESS
                && item.only().decision() == SemanticModerationResult.Decision.BLOCK;
    }

    private static boolean hybridBlocks(
            ModerationResult deterministic,
            SemanticRoute route,
            SemanticModerationResult semantic
    ) {
        if (route.decision() != SemanticRoutingDecision.NEEDS_SEMANTIC_REVIEW) {
            return positive(deterministic.action());
        }
        if (semantic.status() != SemanticModerationResult.Status.SUCCESS
                || semantic.decision() == SemanticModerationResult.Decision.UNKNOWN) {
            return positive(deterministic.action());
        }
        if (semantic.decision() == SemanticModerationResult.Decision.BLOCK) {
            return true;
        }
        return deterministic.action() == ModerationAction.MASK;
    }

    private static boolean routed(EvaluatedCase item) {
        return item.routingDecision() == SemanticRoutingDecision.NEEDS_SEMANTIC_REVIEW;
    }

    private static boolean expectedBlock(EvaluatedCase item) {
        return item.testCase().expectedAction() == ModerationAction.BLOCK;
    }

    private static boolean positive(ModerationAction action) {
        return action != ModerationAction.ALLOW;
    }

    private static String sanitizedMessage(String original, ModerationResult deterministic) {
        return deterministic.action() == ModerationAction.MASK
                ? deterministic.outputMessage() : original.strip();
    }

    private static double perRequest(double cost, long requests) {
        return requests == 0 ? 0.0 : cost / requests;
    }

    private static void printRate(String label, long numerator, long denominator) {
        double percentage = denominator == 0 ? 0.0 : 100.0 * numerator / denominator;
        System.out.printf(Locale.ROOT, "%s=%d/%d (%.2f%%)%n",
                label, numerator, denominator, percentage);
    }

    private static void throttle() {
        try {
            Thread.sleep(REQUEST_INTERVAL_MILLIS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Luna baseline interrupted", exception);
        }
    }

    private static List<BenchmarkCase> semanticCases() {
        List<SemanticEvaluationCase> source = ValidationResources.loadSemanticEvaluation();
        if (source.size() != EXPECTED_SEMANTIC_CASES) {
            throw new IllegalStateException("semantic dataset must remain exactly 108 cases");
        }
        return source.stream().map(item -> new BenchmarkCase(
                "semantic-108", item.category().name(), item.message(), item.expectedAction()
        )).toList();
    }

    private static List<BenchmarkCase> policyHoldoutCases() {
        List<PolicyCalibrationCase> source = PolicyCalibrationResources.loadHoldout();
        if (source.size() != EXPECTED_POLICY_HOLDOUT_CASES) {
            throw new IllegalStateException("policy holdout must remain exactly 165 cases");
        }
        return source.stream().map(item -> new BenchmarkCase(
                "policy-holdout-165", item.category().name(), item.message(),
                item.expectedAction()
        )).toList();
    }

    private static ChatModerationService deterministicService() {
        return new DefaultChatModerationService(
                ValidationResources.loadKeywordsByReason(),
                ValidationResources.loadKeywordExceptions(),
                ValidationResources.loadAliasesByReason()
        );
    }

    private static void printCredentialGuidance() {
        System.out.println("Luna baseline evaluation skipped: OPENAI_API_KEY is not set.");
        System.out.println("Set credentials and documented execution-time prices, then run:");
        System.out.println("  ./gradlew lunaBaselineEvaluation");
    }

    private static void printPricingGuidance() {
        System.out.println("Luna baseline evaluation skipped: token pricing is not configured.");
        System.out.println("Set all three execution-time price variables:");
        System.out.println("  OPENAI_SEMANTIC_INPUT_USD_PER_MILLION");
        System.out.println("  OPENAI_SEMANTIC_CACHED_INPUT_USD_PER_MILLION");
        System.out.println("  OPENAI_SEMANTIC_OUTPUT_USD_PER_MILLION");
    }

    @FunctionalInterface
    private interface Prediction {
        boolean blocks(EvaluatedCase item);
    }

    private record BenchmarkCase(
            String dataset,
            String category,
            String message,
            ModerationAction expectedAction
    ) {
    }

    private record EvaluatedCase(
            BenchmarkCase testCase,
            ModerationResult deterministic,
            SemanticModerationResult only,
            SemanticRoutingDecision routingDecision,
            boolean hybridBlocks
    ) {
    }
}
