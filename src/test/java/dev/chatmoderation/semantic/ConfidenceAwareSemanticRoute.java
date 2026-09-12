package dev.chatmoderation.semantic;

import dev.chatmoderation.core.model.ModerationResult;

import java.util.Objects;

public record ConfidenceAwareSemanticRoute(
        ConfidenceGateState state,
        ModerationResult deterministicResult,
        String semanticMessage
) {
    public ConfidenceAwareSemanticRoute {
        Objects.requireNonNull(state, "state must not be null");
        Objects.requireNonNull(deterministicResult, "deterministicResult must not be null");
        if ((state == ConfidenceGateState.NEEDS_SEMANTIC_REVIEW) != (semanticMessage != null)) {
            throw new IllegalArgumentException("semantic message must exist only for review state");
        }
    }

    public boolean routed() {
        return state == ConfidenceGateState.NEEDS_SEMANTIC_REVIEW;
    }
}
