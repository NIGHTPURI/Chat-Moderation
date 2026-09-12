package dev.chatmoderation.semantic;

import dev.chatmoderation.core.model.ModerationResult;

public record SemanticRoute(
        SemanticRoutingDecision decision,
        ModerationResult deterministicResult,
        String semanticMessage
) {
}
