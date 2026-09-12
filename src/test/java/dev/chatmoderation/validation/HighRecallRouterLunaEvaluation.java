package dev.chatmoderation.validation;

import dev.chatmoderation.calibration.ModerationThresholdCalibrator;
import dev.chatmoderation.core.ChatModerationService;
import dev.chatmoderation.core.DefaultChatModerationService;
import dev.chatmoderation.core.model.ModerationAction;
import dev.chatmoderation.core.model.ModerationResult;
import dev.chatmoderation.router.RouterEvaluationCase;
import dev.chatmoderation.router.RouterEvaluationCategory;
import dev.chatmoderation.router.RouterEvaluationResources;
import dev.chatmoderation.semantic.HighRecallSemanticRouter;
import dev.chatmoderation.semantic.SemanticModerationResult;
import dev.chatmoderation.semantic.SemanticReviewRouter;
import dev.chatmoderation.semantic.SemanticRoutingDecision;
import dev.chatmoderation.semantic.openai.OpenAiSemanticModerationProvider;
import dev.chatmoderation.semantic.openai.OpenAiSemanticProviderConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class HighRecallRouterLunaEvaluation {
    private static final String REQUIRED_MODEL = "gpt-5.6-luna";
    private static final long REQUEST_INTERVAL_MILLIS = 100;

    private HighRecallRouterLunaEvaluation() {
    }

    public static void main(String[] args) {
        Optional<OpenAiSemanticProviderConfig> optionalConfig =
                OpenAiSemanticProviderConfig.fromEnvironment();
        if (optionalConfig.isEmpty()) {
            System.out.println("Phase 3.14 Luna holdout evaluation skipped: "
                    + "OPENAI_API_KEY is not set.");
            System.out.println("Run ./gradlew highRecallRouterLunaEvaluation after setting it.");
            return;
        }
        OpenAiSemanticProviderConfig config = optionalConfig.orElseThrow();
        if (!REQUIRED_MODEL.equals(config.model())) {
            throw new IllegalArgumentException("Phase 3.14 requires " + REQUIRED_MODEL);
        }

        ChatModerationService deterministic = deterministicService();
        SemanticReviewRouter existing = new SemanticReviewRouter();
        HighRecallSemanticRouter redesigned = new HighRecallSemanticRouter();
        OpenAiSemanticModerationProvider provider =
                new OpenAiSemanticModerationProvider(config);
        List<EvaluatedCase> evaluated = new ArrayList<>();
        for (RouterEvaluationCase testCase : RouterEvaluationResources.loadHoldout()) {
            ModerationResult local = deterministic.moderate(testCase.message());
            SemanticModerationResult luna = provider.moderate(
                    local.action() == ModerationAction.MASK
                            ? local.outputMessage() : testCase.message().strip()
            );
            boolean oldRouted = existing.route(testCase.message(), local).decision()
                    == SemanticRoutingDecision.NEEDS_SEMANTIC_REVIEW;
            boolean newRouted = redesigned.route(testCase.message(), local)
                    .routedToSemanticProvider();
            evaluated.add(new EvaluatedCase(testCase, local, luna, oldRouted, newRouted));
            throttle();
        }

        System.out.println("Phase 3.14 actual Luna sealed-holdout comparison");
        printMetrics("A. Luna 100%", metrics(evaluated, HighRecallRouterLunaEvaluation::lunaBlocks));
        printMetrics("B. existing frozen router + Luna", metrics(evaluated,
                item -> hybridBlocks(item, item.existingRouted())));
        printMetrics("C. new high-recall router + Luna", metrics(evaluated,
                item -> hybridBlocks(item, item.redesignedRouted())));
        printCategories(evaluated);
        printIncorrect("new router + Luna", evaluated,
                item -> hybridBlocks(item, item.redesignedRouted()));
        System.out.println();
        System.out.println(provider.telemetry().format());
    }

    private static void printCategories(List<EvaluatedCase> cases) {
        System.out.println();
        System.out.printf("%-24s %6s %10s %10s %10s%n",
                "category", "n", "Luna", "existing", "new router");
        for (RouterEvaluationCategory category : RouterEvaluationCategory.values()) {
            List<EvaluatedCase> subset = cases.stream()
                    .filter(item -> item.testCase().category() == category).toList();
            System.out.printf(Locale.ROOT, "%-24s %6d %9.2f%% %9.2f%% %9.2f%%%n",
                    category, subset.size(),
                    metrics(subset, HighRecallRouterLunaEvaluation::lunaBlocks)
                            .accuracy() * 100.0,
                    metrics(subset, item -> hybridBlocks(item, item.existingRouted()))
                            .accuracy() * 100.0,
                    metrics(subset, item -> hybridBlocks(item, item.redesignedRouted()))
                            .accuracy() * 100.0);
        }
    }

    private static void printIncorrect(
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

    private static boolean hybridBlocks(EvaluatedCase item, boolean routed) {
        if (!routed) {
            return positive(item.deterministic().action());
        }
        if (item.luna().status() != SemanticModerationResult.Status.SUCCESS
                || item.luna().decision() == SemanticModerationResult.Decision.UNKNOWN) {
            return positive(item.deterministic().action());
        }
        if (item.luna().decision() == SemanticModerationResult.Decision.BLOCK) {
            return true;
        }
        return item.deterministic().action() == ModerationAction.MASK;
    }

    private static boolean lunaBlocks(EvaluatedCase item) {
        return item.luna().status() == SemanticModerationResult.Status.SUCCESS
                && item.luna().decision() == SemanticModerationResult.Decision.BLOCK;
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
        HighRecallRouterCalibrationEvaluation.printMetrics(label, metrics);
    }

    private static boolean expectedBlock(EvaluatedCase item) {
        return item.testCase().expectedAction() == ModerationAction.BLOCK;
    }

    private static boolean positive(ModerationAction action) {
        return action != ModerationAction.ALLOW;
    }

    private static void throttle() {
        try {
            Thread.sleep(REQUEST_INTERVAL_MILLIS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Luna evaluation interrupted", exception);
        }
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
        boolean blocks(EvaluatedCase item);
    }

    private record EvaluatedCase(
            RouterEvaluationCase testCase,
            ModerationResult deterministic,
            SemanticModerationResult luna,
            boolean existingRouted,
            boolean redesignedRouted
    ) {
    }
}
