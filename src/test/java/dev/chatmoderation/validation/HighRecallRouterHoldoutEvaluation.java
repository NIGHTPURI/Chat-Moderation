package dev.chatmoderation.validation;

import dev.chatmoderation.calibration.ModerationThresholdCalibrator;
import dev.chatmoderation.core.ChatModerationService;
import dev.chatmoderation.core.DefaultChatModerationService;
import dev.chatmoderation.router.RouterEvaluationCase;
import dev.chatmoderation.router.RouterEvaluationCategory;
import dev.chatmoderation.router.RouterEvaluationResources;
import dev.chatmoderation.semantic.HighRecallSemanticRouter;
import dev.chatmoderation.semantic.SemanticReviewRouter;

import java.util.List;
import java.util.Locale;

public final class HighRecallRouterHoldoutEvaluation {
    private static final double HISTORICAL_LUNA_COST_PER_REQUEST_USD = 0.00014119;
    private static final List<Long> MONTHLY_VOLUMES = List.of(
            10_000L, 100_000L, 1_000_000L, 10_000_000L
    );

    private HighRecallRouterHoldoutEvaluation() {
    }

    public static void main(String[] args) {
        ChatModerationService deterministic = deterministicService();
        SemanticReviewRouter existing = new SemanticReviewRouter();
        HighRecallSemanticRouter redesigned = new HighRecallSemanticRouter();
        List<RouterEvaluationCase> holdout = RouterEvaluationResources.loadHoldout();
        RouterEvaluationSupport.Evaluated evaluated = RouterEvaluationSupport.evaluate(
                holdout, deterministic, existing, redesigned
        );

        System.out.println("Phase 3.14 sealed router holdout (224 cases)");
        System.out.println("No Luna API call: semantic-label oracle isolates router upper bound");
        HighRecallRouterCalibrationEvaluation.printMetrics(
                "A. semantic-label oracle (Luna 100% upper bound)",
                evaluated.oracleMetrics()
        );
        HighRecallRouterCalibrationEvaluation.printMetrics(
                "B. existing frozen router + oracle",
                evaluated.existingHybridMetrics()
        );
        HighRecallRouterCalibrationEvaluation.printMetrics(
                "C. new high-recall router + oracle",
                evaluated.redesignedHybridMetrics()
        );
        HighRecallRouterCalibrationEvaluation.printRouter(
                "existing frozen router", evaluated.existingRouterMetrics()
        );
        HighRecallRouterCalibrationEvaluation.printRouter(
                "new high-recall router", evaluated.redesignedRouterMetrics()
        );
        printCategoryAccuracy(holdout, deterministic, existing, redesigned);
        printMissedBlocks(evaluated);
        printImprovement(evaluated);
        printCostProjection(
                evaluated.existingRouterMetrics().routingRate(),
                evaluated.redesignedRouterMetrics().routingRate()
        );
        System.out.println();
        System.out.println("Historical Phase 3.13 Luna policy holdout: accuracy=98.79%, "
                + "precision=100%, recall=97.33%, FPR=0%, TP=73 TN=90 FP=0 FN=2");
        System.out.println("Actual Luna metrics on this new holdout require the separate live task.");
    }

    private static void printCategoryAccuracy(
            List<RouterEvaluationCase> cases,
            ChatModerationService deterministic,
            SemanticReviewRouter existing,
            HighRecallSemanticRouter redesigned
    ) {
        System.out.println();
        System.out.printf("%-24s %6s %10s %10s %10s%n",
                "category", "n", "oracle", "existing", "new router");
        for (RouterEvaluationCategory category : RouterEvaluationCategory.values()) {
            List<RouterEvaluationCase> subset = cases.stream()
                    .filter(item -> item.category() == category).toList();
            RouterEvaluationSupport.Evaluated result = RouterEvaluationSupport.evaluate(
                    subset, deterministic, existing, redesigned
            );
            System.out.printf(Locale.ROOT, "%-24s %6d %9.2f%% %9.2f%% %9.2f%%%n",
                    category, subset.size(),
                    result.oracleMetrics().accuracy() * 100.0,
                    result.existingHybridMetrics().accuracy() * 100.0,
                    result.redesignedHybridMetrics().accuracy() * 100.0);
        }
    }

    private static void printMissedBlocks(RouterEvaluationSupport.Evaluated evaluated) {
        System.out.println();
        System.out.println("New router missed semantic BLOCK cases");
        boolean found = false;
        for (RouterEvaluationSupport.CaseResult item : evaluated.cases()) {
            if (item.testCase().expectedAction()
                    != dev.chatmoderation.core.model.ModerationAction.BLOCK
                    || item.deterministic().action()
                    != dev.chatmoderation.core.model.ModerationAction.ALLOW
                    || item.redesignedRouted()) {
                continue;
            }
            found = true;
            System.out.println(item.testCase().category() + " | " + item.testCase().message());
        }
        if (!found) {
            System.out.println("none");
        }
    }

    private static void printImprovement(RouterEvaluationSupport.Evaluated evaluated) {
        RouterEvaluationSupport.RouterMetrics oldMetrics = evaluated.existingRouterMetrics();
        RouterEvaluationSupport.RouterMetrics newMetrics = evaluated.redesignedRouterMetrics();
        ModerationThresholdCalibrator.BinaryMetrics oldHybrid =
                evaluated.existingHybridMetrics();
        ModerationThresholdCalibrator.BinaryMetrics newHybrid =
                evaluated.redesignedHybridMetrics();
        System.out.println();
        System.out.printf(Locale.ROOT,
                "Improvement vs frozen router: candidate recall=%+.2f pp, "
                        + "hybrid recall=%+.2f pp, routing rate=%+.2f pp%n",
                (newMetrics.candidateRecall() - oldMetrics.candidateRecall()) * 100.0,
                (newHybrid.recall() - oldHybrid.recall()) * 100.0,
                (newMetrics.routingRate() - oldMetrics.routingRate()) * 100.0);
        System.out.printf(Locale.ROOT,
                "Oracle-to-new-router recall loss: %.2f pp%n",
                (evaluated.oracleMetrics().recall() - newHybrid.recall()) * 100.0);
    }

    private static void printCostProjection(double existingRate, double redesignedRate) {
        System.out.println();
        System.out.println("Monthly Luna cost projection (historical $0.00014119/request)");
        System.out.printf("%-14s %16s %16s%n",
                "monthly chats", "frozen router", "new router");
        for (long volume : MONTHLY_VOLUMES) {
            System.out.printf(Locale.ROOT, "%,14d $%15.4f $%15.4f%n",
                    volume,
                    volume * existingRate * HISTORICAL_LUNA_COST_PER_REQUEST_USD,
                    volume * redesignedRate * HISTORICAL_LUNA_COST_PER_REQUEST_USD);
        }
    }

    private static ChatModerationService deterministicService() {
        return new DefaultChatModerationService(
                ValidationResources.loadKeywordsByReason(),
                ValidationResources.loadKeywordExceptions(),
                ValidationResources.loadAliasesByReason()
        );
    }
}
