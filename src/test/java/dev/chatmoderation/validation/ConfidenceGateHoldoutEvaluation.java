package dev.chatmoderation.validation;

import dev.chatmoderation.core.model.ModerationAction;
import dev.chatmoderation.gate.GateEvaluationCategory;
import dev.chatmoderation.gate.GateEvaluationResources;

import java.util.List;
import java.util.Locale;

public final class ConfidenceGateHoldoutEvaluation {
    private static final double REQUIRED_RECALL = 0.95;

    private ConfidenceGateHoldoutEvaluation() {
    }

    public static void main(String[] args) {
        ConfidenceGateEvaluationSupport.Evaluated evaluated =
                ConfidenceGateCalibrationEvaluation.evaluate(
                        GateEvaluationResources.loadHoldout());
        System.out.println("Phase 3.15 sealed confidence-aware gate holdout");
        System.out.println("Gate frozen before this dataset was evaluated; semantic oracle only");
        ConfidenceGateCalibrationEvaluation.printDataset("sealed holdout", evaluated);
        printCategoryMetrics(evaluated);
        printMissedBlocks(evaluated);
        ConfidenceGateEvaluationSupport.RoutingMetrics gate = evaluated.routingMetrics(
                ConfidenceGateEvaluationSupport.RouteVersion.PHASE_315);
        System.out.printf(Locale.ROOT, "%nPASS threshold: %.2f%%; actual: %.2f%%; result: %s%n",
                REQUIRED_RECALL * 100.0, gate.candidateRecall() * 100.0,
                gate.candidateRecall() >= REQUIRED_RECALL ? "PASS" : "FAIL");
    }

    private static void printCategoryMetrics(
            ConfidenceGateEvaluationSupport.Evaluated evaluated
    ) {
        System.out.printf("%n%-24s %5s %10s %10s %10s%n",
                "category", "n", "frozen", "3.14", "3.15");
        for (GateEvaluationCategory category : GateEvaluationCategory.values()) {
            List<ConfidenceGateEvaluationSupport.CaseResult> subset = evaluated.cases().stream()
                    .filter(item -> item.testCase().category() == category).toList();
            if (subset.isEmpty()) continue;
            System.out.printf(Locale.ROOT, "%-24s %5d %9.2f%% %9.2f%% %9.2f%%%n",
                    category, subset.size(),
                    categoryAccuracy(subset, ConfidenceGateEvaluationSupport.RouteVersion.FROZEN_310),
                    categoryAccuracy(subset, ConfidenceGateEvaluationSupport.RouteVersion.PHASE_314),
                    categoryAccuracy(subset, ConfidenceGateEvaluationSupport.RouteVersion.PHASE_315));
        }
    }

    private static double categoryAccuracy(
            List<ConfidenceGateEvaluationSupport.CaseResult> cases,
            ConfidenceGateEvaluationSupport.RouteVersion version
    ) {
        long correct = cases.stream().filter(item -> {
            boolean routed = switch (version) {
                case FROZEN_310 -> item.frozenRouted();
                case PHASE_314 -> item.phase314Routed();
                case PHASE_315 -> item.phase315Routed();
            };
            boolean predicted = routed
                    ? item.testCase().expectedAction() == ModerationAction.BLOCK
                    : item.deterministic().action() == ModerationAction.BLOCK;
            return predicted == (item.testCase().expectedAction() == ModerationAction.BLOCK);
        }).count();
        return 100.0 * correct / cases.size();
    }

    private static void printMissedBlocks(
            ConfidenceGateEvaluationSupport.Evaluated evaluated
    ) {
        System.out.println("\nPhase 3.15 missed semantic BLOCK candidates:");
        boolean found = false;
        for (ConfidenceGateEvaluationSupport.CaseResult item : evaluated.cases()) {
            if (item.testCase().expectedAction() == ModerationAction.BLOCK
                    && item.deterministic().action() == ModerationAction.ALLOW
                    && !item.phase315Routed()) {
                found = true;
                System.out.println(item.testCase().category() + " | " + item.testCase().message());
            }
        }
        if (!found) System.out.println("none");
    }
}
