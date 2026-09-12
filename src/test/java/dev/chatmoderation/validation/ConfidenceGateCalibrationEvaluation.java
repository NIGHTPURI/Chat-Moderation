package dev.chatmoderation.validation;

import dev.chatmoderation.core.ChatModerationService;
import dev.chatmoderation.core.DefaultChatModerationService;
import dev.chatmoderation.gate.GateEvaluationResources;
import dev.chatmoderation.semantic.ConfidenceAwareSemanticGate;
import dev.chatmoderation.semantic.HighRecallSemanticRouter;
import dev.chatmoderation.semantic.SemanticReviewRouter;

import java.util.Locale;

public final class ConfidenceGateCalibrationEvaluation {
    private static final double HISTORICAL_COST_PER_REQUEST_USD = 0.00014119;
    private static final java.util.List<Long> MONTHLY_VOLUMES = java.util.List.of(
            10_000L, 100_000L, 1_000_000L, 10_000_000L);
    private ConfidenceGateCalibrationEvaluation() {
    }

    public static void main(String[] args) {
        System.out.println("Phase 3.15 confidence-aware gate calibration (not sealed holdout)");
        printDataset("balanced calibration", evaluate(GateEvaluationResources.loadCalibration()));
        printDataset("production-like cost dataset",
                evaluate(GateEvaluationResources.loadProductionLike()));
        printOfflineCostProjection(evaluate(GateEvaluationResources.loadProductionLike()));
    }

    private static void printOfflineCostProjection(
            ConfidenceGateEvaluationSupport.Evaluated evaluated
    ) {
        System.out.println("\nOffline monthly projection using Phase 3.13 historical "
                + "average $0.00014119/request");
        System.out.printf("%-14s %14s %14s %14s%n", "messages", "Luna 100%", "Phase 3.14", "Phase 3.15");
        double phase314 = evaluated.routingMetrics(
                ConfidenceGateEvaluationSupport.RouteVersion.PHASE_314).routingRate();
        double phase315 = evaluated.routingMetrics(
                ConfidenceGateEvaluationSupport.RouteVersion.PHASE_315).routingRate();
        for (long volume : MONTHLY_VOLUMES) {
            System.out.printf(java.util.Locale.ROOT, "%,14d $%13.4f $%13.4f $%13.4f%n",
                    volume,
                    volume * HISTORICAL_COST_PER_REQUEST_USD,
                    volume * phase314 * HISTORICAL_COST_PER_REQUEST_USD,
                    volume * phase315 * HISTORICAL_COST_PER_REQUEST_USD);
        }
    }

    static ConfidenceGateEvaluationSupport.Evaluated evaluate(
            java.util.List<dev.chatmoderation.gate.GateEvaluationCase> cases
    ) {
        ChatModerationService deterministic = new DefaultChatModerationService(
                ValidationResources.loadKeywordsByReason(),
                ValidationResources.loadKeywordExceptions(),
                ValidationResources.loadAliasesByReason()
        );
        return ConfidenceGateEvaluationSupport.evaluate(
                cases, deterministic, new SemanticReviewRouter(),
                new HighRecallSemanticRouter(), new ConfidenceAwareSemanticGate()
        );
    }

    static void printDataset(
            String label,
            ConfidenceGateEvaluationSupport.Evaluated evaluated
    ) {
        System.out.println();
        System.out.println(label + " (" + evaluated.cases().size() + " cases)");
        HighRecallRouterCalibrationEvaluation.printMetrics(
                "semantic oracle", evaluated.oracleMetrics());
        printVersion("frozen Phase 3.10 router", evaluated,
                ConfidenceGateEvaluationSupport.RouteVersion.FROZEN_310);
        printVersion("Phase 3.14 router", evaluated,
                ConfidenceGateEvaluationSupport.RouteVersion.PHASE_314);
        printVersion("Phase 3.15 gate", evaluated,
                ConfidenceGateEvaluationSupport.RouteVersion.PHASE_315);
        printMissedCandidates(evaluated);
    }

    static void printMissedCandidates(ConfidenceGateEvaluationSupport.Evaluated evaluated) {
        System.out.println("Phase 3.15 missed semantic BLOCK candidates:");
        boolean found = false;
        for (ConfidenceGateEvaluationSupport.CaseResult item : evaluated.cases()) {
            if (item.testCase().expectedAction()
                    == dev.chatmoderation.core.model.ModerationAction.BLOCK
                    && item.deterministic().action()
                    == dev.chatmoderation.core.model.ModerationAction.ALLOW
                    && !item.phase315Routed()) {
                found = true;
                System.out.println(item.testCase().category() + " | " + item.testCase().message());
            }
        }
        if (!found) System.out.println("none");
    }

    static void printVersion(
            String label,
            ConfidenceGateEvaluationSupport.Evaluated evaluated,
            ConfidenceGateEvaluationSupport.RouteVersion version
    ) {
        ConfidenceGateEvaluationSupport.RoutingMetrics routing =
                evaluated.routingMetrics(version);
        System.out.printf(Locale.ROOT,
                "%s: candidate recall=%.2f%% routing rate=%.2f%% routed BLOCK=%d "
                        + "missed BLOCK=%d routed ALLOW=%d FP rescued=%d FP local=%d%n",
                label, routing.candidateRecall() * 100.0, routing.routingRate() * 100.0,
                routing.routedBlocks(), routing.missedCandidates(), routing.routedAllows(),
                routing.rescuedFalsePositives(), routing.locallyFinalizedFalsePositives());
        HighRecallRouterCalibrationEvaluation.printMetrics(
                label + " + oracle", evaluated.hybridMetrics(version));
    }
}
