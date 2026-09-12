package dev.chatmoderation.validation;

import dev.chatmoderation.calibration.ModerationThresholdCalibrator;
import dev.chatmoderation.core.ChatModerationService;
import dev.chatmoderation.core.model.ModerationAction;
import dev.chatmoderation.core.model.ModerationResult;
import dev.chatmoderation.gate.GateEvaluationCase;
import dev.chatmoderation.semantic.ConfidenceAwareSemanticGate;
import dev.chatmoderation.semantic.HighRecallSemanticRouter;
import dev.chatmoderation.semantic.SemanticReviewRouter;

import java.util.ArrayList;
import java.util.List;

final class ConfidenceGateEvaluationSupport {
    private ConfidenceGateEvaluationSupport() {
    }

    static Evaluated evaluate(
            List<GateEvaluationCase> cases,
            ChatModerationService deterministic,
            SemanticReviewRouter frozen,
            HighRecallSemanticRouter phase314,
            ConfidenceAwareSemanticGate gate
    ) {
        List<CaseResult> results = new ArrayList<>();
        for (GateEvaluationCase testCase : cases) {
            ModerationResult local = deterministic.moderate(testCase.message());
            results.add(new CaseResult(
                    testCase,
                    local,
                    frozen.route(testCase.message(), local).semanticMessage() != null,
                    phase314.route(testCase.message(), local).routedToSemanticProvider(),
                    gate.route(testCase.message(), local).routed()
            ));
        }
        return new Evaluated(List.copyOf(results));
    }

    record Evaluated(List<CaseResult> cases) {
        ModerationThresholdCalibrator.BinaryMetrics oracleMetrics() {
            return metrics(cases, item -> expectedBlock(item.testCase()));
        }

        ModerationThresholdCalibrator.BinaryMetrics hybridMetrics(RouteVersion version) {
            return metrics(cases, item -> routed(item, version)
                    ? expectedBlock(item.testCase())
                    : localBlocks(item.deterministic()));
        }

        RoutingMetrics routingMetrics(RouteVersion version) {
            int routed = 0;
            int candidates = 0;
            int routedCandidateBlocks = 0;
            int routedBlocks = 0;
            int routedAllows = 0;
            int deterministicFalsePositives = 0;
            int rescued = 0;
            for (CaseResult item : cases) {
                boolean selected = routed(item, version);
                boolean block = expectedBlock(item.testCase());
                boolean candidate = block
                        && item.deterministic().action() == ModerationAction.ALLOW;
                boolean deterministicFalsePositive = !block
                        && item.deterministic().action() == ModerationAction.BLOCK;
                if (selected) routed++;
                if (selected && block) routedBlocks++;
                if (selected && !block) routedAllows++;
                if (candidate) {
                    candidates++;
                    if (selected) routedCandidateBlocks++;
                }
                if (deterministicFalsePositive) {
                    deterministicFalsePositives++;
                    if (selected) rescued++;
                }
            }
            return new RoutingMetrics(
                    cases.size(), routed, candidates, routedCandidateBlocks,
                    candidates - routedCandidateBlocks, routedBlocks, routedAllows,
                    deterministicFalsePositives, rescued,
                    deterministicFalsePositives - rescued
            );
        }

        private boolean routed(CaseResult item, RouteVersion version) {
            return switch (version) {
                case FROZEN_310 -> item.frozenRouted();
                case PHASE_314 -> item.phase314Routed();
                case PHASE_315 -> item.phase315Routed();
            };
        }
    }

    enum RouteVersion {
        FROZEN_310,
        PHASE_314,
        PHASE_315
    }

    record CaseResult(
            GateEvaluationCase testCase,
            ModerationResult deterministic,
            boolean frozenRouted,
            boolean phase314Routed,
            boolean phase315Routed
    ) {
    }

    record RoutingMetrics(
            int total,
            int routed,
            int semanticCandidates,
            int routedCandidates,
            int missedCandidates,
            int routedBlocks,
            int routedAllows,
            int deterministicFalsePositives,
            int rescuedFalsePositives,
            int locallyFinalizedFalsePositives
    ) {
        double candidateRecall() {
            return ratio(routedCandidates, semanticCandidates);
        }

        double routingRate() {
            return ratio(routed, total);
        }
    }

    private static ModerationThresholdCalibrator.BinaryMetrics metrics(
            List<CaseResult> cases,
            Prediction prediction
    ) {
        int tp = 0;
        int tn = 0;
        int fp = 0;
        int fn = 0;
        for (CaseResult item : cases) {
            boolean expected = expectedBlock(item.testCase());
            boolean actual = prediction.blocks(item);
            if (expected && actual) tp++;
            else if (!expected && !actual) tn++;
            else if (actual) fp++;
            else fn++;
        }
        return new ModerationThresholdCalibrator.BinaryMetrics(tp, tn, fp, fn);
    }

    private static boolean expectedBlock(GateEvaluationCase item) {
        return item.expectedAction() == ModerationAction.BLOCK;
    }

    private static boolean localBlocks(ModerationResult result) {
        return result.action() == ModerationAction.BLOCK;
    }

    private static double ratio(int numerator, int denominator) {
        return denominator == 0 ? 0.0 : (double) numerator / denominator;
    }

    @FunctionalInterface
    private interface Prediction {
        boolean blocks(CaseResult item);
    }
}
