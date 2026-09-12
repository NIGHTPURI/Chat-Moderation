package dev.chatmoderation.semantic;

import dev.chatmoderation.core.model.ModerationResult;

public record SemanticHybridResult(
        ModerationResult result,
        SemanticRoutingDecision routingDecision,
        long semanticLatencyNanos,
        SemanticModerationResult.Status providerStatus
) {
    public boolean routedToSemanticProvider() {
        return routingDecision == SemanticRoutingDecision.NEEDS_SEMANTIC_REVIEW;
    }
}
