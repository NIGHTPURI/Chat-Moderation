package dev.chatmoderation.validation;

import dev.chatmoderation.calibration.ModerationThresholdCalibrator;
import dev.chatmoderation.core.ChatModerationService;
import dev.chatmoderation.core.model.ModerationAction;
import dev.chatmoderation.core.model.ModerationResult;
import dev.chatmoderation.router.RouterEvaluationCase;
import dev.chatmoderation.semantic.HighRecallRoutingDecision;
import dev.chatmoderation.semantic.HighRecallSemanticRouter;
import dev.chatmoderation.semantic.SemanticReviewRouter;
import dev.chatmoderation.semantic.SemanticRoutingDecision;

import java.util.ArrayList;
import java.util.List;

final class RouterEvaluationSupport {
    private RouterEvaluationSupport() {
    }

    static Evaluated evaluate(
            List<RouterEvaluationCase> cases,
            ChatModerationService deterministic,
            SemanticReviewRouter existing,
            HighRecallSemanticRouter redesigned
    ) {
        List<CaseResult> results = new ArrayList<>();
        for (RouterEvaluationCase testCase : cases) {
            ModerationResult local = deterministic.moderate(testCase.message());
            boolean existingRouted = existing.route(testCase.message(), local).decision()
                    == SemanticRoutingDecision.NEEDS_SEMANTIC_REVIEW;
            boolean redesignedRouted = redesigned.route(testCase.message(), local).decision()
                    == HighRecallRoutingDecision.NEEDS_SEMANTIC_REVIEW;
            results.add(new CaseResult(testCase, local, existingRouted, redesignedRouted));
        }
        return new Evaluated(List.copyOf(results));
    }

    record Evaluated(List<CaseResult> cases) {
        ModerationThresholdCalibrator.BinaryMetrics oracleMetrics() {
            return metrics(cases, item -> expectedBlock(item.testCase()));
        }

        ModerationThresholdCalibrator.BinaryMetrics existingHybridMetrics() {
            return metrics(cases, item -> hybridBlocks(item, item.existingRouted()));
        }

        ModerationThresholdCalibrator.BinaryMetrics redesignedHybridMetrics() {
            return metrics(cases, item -> hybridBlocks(item, item.redesignedRouted()));
        }

        RouterMetrics existingRouterMetrics() {
            return routerMetrics(cases, CaseResult::existingRouted);
        }

        RouterMetrics redesignedRouterMetrics() {
            return routerMetrics(cases, CaseResult::redesignedRouted);
        }

        private boolean hybridBlocks(CaseResult item, boolean routed) {
            return routed ? expectedBlock(item.testCase()) : positive(item.deterministic().action());
        }
    }

    record CaseResult(
            RouterEvaluationCase testCase,
            ModerationResult deterministic,
            boolean existingRouted,
            boolean redesignedRouted
    ) {
    }

    record RouterMetrics(
            int total,
            int routed,
            int semanticBlockCandidates,
            int routedBlocks,
            int missedBlocks,
            int routedAllows
    ) {
        double candidateRecall() {
            return ratio(routedBlocks, semanticBlockCandidates);
        }

        double routingRate() {
            return ratio(routed, total);
        }
    }

    private static RouterMetrics routerMetrics(
            List<CaseResult> cases,
            RouteSelector selector
    ) {
        int routed = 0;
        int semanticBlocks = 0;
        int routedBlocks = 0;
        int routedAllows = 0;
        for (CaseResult item : cases) {
            boolean selected = selector.routed(item);
            if (selected) {
                routed++;
            }
            boolean semanticBlock = expectedBlock(item.testCase())
                    && item.deterministic().action() == ModerationAction.ALLOW;
            if (semanticBlock) {
                semanticBlocks++;
                if (selected) {
                    routedBlocks++;
                }
            }
            if (!expectedBlock(item.testCase()) && selected) {
                routedAllows++;
            }
        }
        return new RouterMetrics(
                cases.size(), routed, semanticBlocks, routedBlocks,
                semanticBlocks - routedBlocks, routedAllows
        );
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
            else if (!expected) fp++;
            else fn++;
        }
        return new ModerationThresholdCalibrator.BinaryMetrics(tp, tn, fp, fn);
    }

    private static boolean expectedBlock(RouterEvaluationCase testCase) {
        return testCase.expectedAction() == ModerationAction.BLOCK;
    }

    private static boolean positive(ModerationAction action) {
        return action != ModerationAction.ALLOW;
    }

    private static double ratio(int numerator, int denominator) {
        return denominator == 0 ? 0.0 : (double) numerator / denominator;
    }

    @FunctionalInterface
    private interface RouteSelector {
        boolean routed(CaseResult item);
    }

    @FunctionalInterface
    private interface Prediction {
        boolean blocks(CaseResult item);
    }
}
