package dev.chatmoderation.validation;

import dev.chatmoderation.calibration.ModerationThresholdCalibrator;
import dev.chatmoderation.core.ChatModerationService;
import dev.chatmoderation.core.DefaultChatModerationService;
import dev.chatmoderation.core.model.ModerationAction;
import dev.chatmoderation.core.model.ModerationResult;
import dev.chatmoderation.gate.GateEvaluationCase;
import dev.chatmoderation.gate.GateEvaluationCategory;
import dev.chatmoderation.gate.GateEvaluationResources;
import dev.chatmoderation.semantic.ConfidenceAwareSemanticGate;
import dev.chatmoderation.semantic.HighRecallSemanticRouter;
import dev.chatmoderation.semantic.SemanticModerationResult;
import dev.chatmoderation.semantic.openai.OpenAiProviderTelemetry;
import dev.chatmoderation.semantic.openai.OpenAiSemanticModerationProvider;
import dev.chatmoderation.semantic.openai.OpenAiSemanticProviderConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class FinalLunaEvaluation {
    private static final String REQUIRED_MODEL = "gpt-5.6-luna";
    private static final long REQUEST_INTERVAL_MILLIS = 100;
    private static final List<Long> MONTHLY_VOLUMES = List.of(
            10_000L, 100_000L, 1_000_000L, 10_000_000L);

    private FinalLunaEvaluation() {
    }

    public static void main(String[] args) {
        Optional<OpenAiSemanticProviderConfig> optional =
                OpenAiSemanticProviderConfig.fromEnvironment();
        if (optional.isEmpty()) {
            System.out.println("Final Luna evaluation skipped: OPENAI_API_KEY is not set.");
            System.out.println("Run ./gradlew finalLunaEvaluation after setting the documented environment variables.");
            return;
        }
        OpenAiSemanticProviderConfig config = optional.orElseThrow();
        if (!REQUIRED_MODEL.equals(config.model())) {
            throw new IllegalArgumentException("Final evaluation requires " + REQUIRED_MODEL
                    + "; configured model was " + config.model());
        }
        if (config.pricing().isEmpty()) {
            System.out.println("Final Luna evaluation skipped: all three token price variables are required for actual cost reporting.");
            return;
        }

        ChatModerationService deterministic = deterministicService();
        HighRecallSemanticRouter phase314 = new HighRecallSemanticRouter();
        ConfidenceAwareSemanticGate phase315 = new ConfidenceAwareSemanticGate();
        OpenAiSemanticModerationProvider provider = new OpenAiSemanticModerationProvider(config);
        List<EvaluatedCase> evaluated = new ArrayList<>();
        for (GateEvaluationCase testCase : GateEvaluationResources.loadHoldout()) {
            ModerationResult local = deterministic.moderate(testCase.message());
            SemanticModerationResult luna = provider.moderate(
                    local.action() == ModerationAction.MASK
                            ? local.outputMessage() : testCase.message().strip());
            evaluated.add(new EvaluatedCase(
                    testCase, local, luna,
                    phase314.route(testCase.message(), local).routedToSemanticProvider(),
                    phase315.route(testCase.message(), local).routed()));
            throttle();
        }

        System.out.println("Phase 3.16 final GPT-5.6 Luna evaluation");
        System.out.println("Dataset: frozen Phase 3.15 sealed holdout (" + evaluated.size() + " cases)");
        System.out.println("Prompt/model/gates were frozen before live execution");
        printMetrics("A. Luna 100%", metrics(evaluated, FinalLunaEvaluation::lunaBlocks));
        printMetrics("B. Phase 3.14 router + Luna", metrics(evaluated,
                item -> hybridBlocks(item, item.phase314Routed())));
        printMetrics("C. Phase 3.15 confidence gate + Luna", metrics(evaluated,
                item -> hybridBlocks(item, item.phase315Routed())));
        printCategoryMetrics(evaluated);
        printRouting("Phase 3.14", evaluated, EvaluatedCase::phase314Routed);
        printRouting("Phase 3.15", evaluated, EvaluatedCase::phase315Routed);
        printRecallLoss(evaluated);
        printMissedBlocks(evaluated);
        OpenAiProviderTelemetry.Snapshot telemetry = provider.telemetry();
        System.out.println("\nActual request telemetry");
        System.out.println(telemetry.format());
        printActualCost(config.pricing().orElseThrow(), telemetry, evaluated);
    }

    private static void printCategoryMetrics(List<EvaluatedCase> cases) {
        System.out.printf("%n%-24s %5s %10s %10s %10s %10s %10s %10s%n",
                "category", "n", "A acc", "B acc", "C acc", "A recall", "B recall", "C recall");
        for (GateEvaluationCategory category : GateEvaluationCategory.values()) {
            List<EvaluatedCase> subset = cases.stream()
                    .filter(item -> item.testCase().category() == category).toList();
            if (subset.isEmpty()) continue;
            ModerationThresholdCalibrator.BinaryMetrics a = metrics(subset, FinalLunaEvaluation::lunaBlocks);
            ModerationThresholdCalibrator.BinaryMetrics b = metrics(subset,
                    item -> hybridBlocks(item, item.phase314Routed()));
            ModerationThresholdCalibrator.BinaryMetrics c = metrics(subset,
                    item -> hybridBlocks(item, item.phase315Routed()));
            System.out.printf(Locale.ROOT, "%-24s %5d %9.2f%% %9.2f%% %9.2f%% %9.2f%% %9.2f%% %9.2f%%%n",
                    category, subset.size(), a.accuracy() * 100, b.accuracy() * 100,
                    c.accuracy() * 100, a.recall() * 100, b.recall() * 100, c.recall() * 100);
        }
    }

    private static void printRouting(String label, List<EvaluatedCase> cases, Route route) {
        long routed = cases.stream().filter(route::routed).count();
        long candidates = cases.stream().filter(FinalLunaEvaluation::semanticCandidate).count();
        long routedCandidates = cases.stream().filter(FinalLunaEvaluation::semanticCandidate)
                .filter(route::routed).count();
        long routedBlocks = cases.stream().filter(FinalLunaEvaluation::expectedBlock)
                .filter(route::routed).count();
        long routedAllows = cases.stream().filter(item -> !expectedBlock(item))
                .filter(route::routed).count();
        System.out.printf(Locale.ROOT,
                "%n%s routing: rate=%d/%d (%.2f%%), candidate recall=%d/%d (%.2f%%), "
                        + "routed BLOCK=%d, missed BLOCK=%d, routed ALLOW=%d%n",
                label, routed, cases.size(), percent(routed, cases.size()),
                routedCandidates, candidates, percent(routedCandidates, candidates),
                routedBlocks, candidates - routedCandidates, routedAllows);
    }

    private static void printRecallLoss(List<EvaluatedCase> cases) {
        double only = metrics(cases, FinalLunaEvaluation::lunaBlocks).recall();
        double phase314 = metrics(cases,
                item -> hybridBlocks(item, item.phase314Routed())).recall();
        double phase315 = metrics(cases,
                item -> hybridBlocks(item, item.phase315Routed())).recall();
        System.out.printf(Locale.ROOT,
                "Luna-only vs gated recall loss: Phase 3.14=%.2f pp, Phase 3.15=%.2f pp%n",
                (only - phase314) * 100, (only - phase315) * 100);
    }

    private static void printMissedBlocks(List<EvaluatedCase> cases) {
        System.out.println("\nPhase 3.15 missed BLOCK cases");
        cases.stream().filter(FinalLunaEvaluation::semanticCandidate)
                .filter(item -> !item.phase315Routed())
                .forEach(item -> System.out.println(item.testCase().category() + " | "
                        + item.testCase().message()));
    }

    private static void printActualCost(
            OpenAiSemanticProviderConfig.TokenPricing pricing,
            OpenAiProviderTelemetry.Snapshot telemetry,
            List<EvaluatedCase> cases
    ) {
        double cost = pricing.estimateUsd(
                telemetry.inputTokens(), telemetry.cachedInputTokens(), telemetry.outputTokens());
        double average = telemetry.requests() == 0 ? 0.0 : cost / telemetry.requests();
        double phase314Rate = ratio(cases.stream().filter(EvaluatedCase::phase314Routed).count(), cases.size());
        double phase315Rate = ratio(cases.stream().filter(EvaluatedCase::phase315Routed).count(), cases.size());
        System.out.printf(Locale.ROOT,
                "%nActual request cost: total=$%.6f, average/request=$%.8f%n", cost, average);
        System.out.printf("%-14s %14s %14s %14s%n", "messages", "Luna 100%", "Phase 3.14", "Phase 3.15");
        for (long volume : MONTHLY_VOLUMES) {
            System.out.printf(Locale.ROOT, "%,14d $%13.4f $%13.4f $%13.4f%n",
                    volume, volume * average, volume * phase314Rate * average,
                    volume * phase315Rate * average);
        }
    }

    private static boolean hybridBlocks(EvaluatedCase item, boolean routed) {
        if (!routed) return item.deterministic().action() == ModerationAction.BLOCK;
        if (item.luna().status() != SemanticModerationResult.Status.SUCCESS
                || item.luna().decision() == SemanticModerationResult.Decision.UNKNOWN) {
            return item.deterministic().action() == ModerationAction.BLOCK;
        }
        return item.luna().decision() == SemanticModerationResult.Decision.BLOCK;
    }

    private static boolean lunaBlocks(EvaluatedCase item) {
        return item.luna().status() == SemanticModerationResult.Status.SUCCESS
                && item.luna().decision() == SemanticModerationResult.Decision.BLOCK;
    }

    private static ModerationThresholdCalibrator.BinaryMetrics metrics(
            List<EvaluatedCase> cases, Prediction prediction
    ) {
        int tp = 0, tn = 0, fp = 0, fn = 0;
        for (EvaluatedCase item : cases) {
            boolean expected = expectedBlock(item);
            boolean actual = prediction.blocks(item);
            if (expected && actual) tp++;
            else if (!expected && !actual) tn++;
            else if (actual) fp++;
            else fn++;
        }
        return new ModerationThresholdCalibrator.BinaryMetrics(tp, tn, fp, fn);
    }

    private static void printMetrics(String label, ModerationThresholdCalibrator.BinaryMetrics metrics) {
        HighRecallRouterCalibrationEvaluation.printMetrics(label, metrics);
    }

    private static boolean expectedBlock(EvaluatedCase item) {
        return item.testCase().expectedAction() == ModerationAction.BLOCK;
    }

    private static boolean semanticCandidate(EvaluatedCase item) {
        return expectedBlock(item) && item.deterministic().action() == ModerationAction.ALLOW;
    }

    private static double percent(long numerator, long denominator) {
        return ratio(numerator, denominator) * 100.0;
    }

    private static double ratio(long numerator, long denominator) {
        return denominator == 0 ? 0.0 : (double) numerator / denominator;
    }

    private static void throttle() {
        try {
            Thread.sleep(REQUEST_INTERVAL_MILLIS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Final Luna evaluation interrupted", exception);
        }
    }

    private static ChatModerationService deterministicService() {
        return new DefaultChatModerationService(
                ValidationResources.loadKeywordsByReason(),
                ValidationResources.loadKeywordExceptions(),
                ValidationResources.loadAliasesByReason());
    }

    @FunctionalInterface
    private interface Prediction { boolean blocks(EvaluatedCase item); }

    @FunctionalInterface
    private interface Route { boolean routed(EvaluatedCase item); }

    private record EvaluatedCase(
            GateEvaluationCase testCase,
            ModerationResult deterministic,
            SemanticModerationResult luna,
            boolean phase314Routed,
            boolean phase315Routed
    ) { }
}
