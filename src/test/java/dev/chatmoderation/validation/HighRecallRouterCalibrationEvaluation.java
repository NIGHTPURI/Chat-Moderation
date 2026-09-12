package dev.chatmoderation.validation;

import dev.chatmoderation.calibration.ModerationThresholdCalibrator;
import dev.chatmoderation.core.ChatModerationService;
import dev.chatmoderation.core.DefaultChatModerationService;
import dev.chatmoderation.router.RouterEvaluationResources;
import dev.chatmoderation.semantic.HighRecallSemanticRouter;
import dev.chatmoderation.semantic.SemanticReviewRouter;

import java.util.Locale;

public final class HighRecallRouterCalibrationEvaluation {
    private HighRecallRouterCalibrationEvaluation() {
    }

    public static void main(String[] args) {
        ChatModerationService deterministic = deterministicService();
        RouterEvaluationSupport.Evaluated evaluated = RouterEvaluationSupport.evaluate(
                RouterEvaluationResources.loadCalibration(),
                deterministic,
                new SemanticReviewRouter(),
                new HighRecallSemanticRouter()
        );

        System.out.println("Phase 3.14 router calibration (not sealed holdout)");
        printRouter("existing frozen router", evaluated.existingRouterMetrics());
        printRouter("new high-recall router", evaluated.redesignedRouterMetrics());
        printMetrics("semantic-label oracle", evaluated.oracleMetrics());
        printMetrics("existing router + oracle", evaluated.existingHybridMetrics());
        printMetrics("new router + oracle", evaluated.redesignedHybridMetrics());
    }

    static void printRouter(String label, RouterEvaluationSupport.RouterMetrics metrics) {
        System.out.printf(Locale.ROOT,
                "%s: candidate recall=%.2f%% routing rate=%.2f%% "
                        + "routed BLOCK=%d missed BLOCK=%d routed ALLOW=%d (%d/%d routed)%n",
                label, metrics.candidateRecall() * 100.0, metrics.routingRate() * 100.0,
                metrics.routedBlocks(), metrics.missedBlocks(), metrics.routedAllows(),
                metrics.routed(), metrics.total());
    }

    static void printMetrics(
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

    private static ChatModerationService deterministicService() {
        return new DefaultChatModerationService(
                ValidationResources.loadKeywordsByReason(),
                ValidationResources.loadKeywordExceptions(),
                ValidationResources.loadAliasesByReason()
        );
    }
}
