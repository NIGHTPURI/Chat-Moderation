package dev.chatmoderation.gate;

import dev.chatmoderation.core.model.ModerationAction;

import java.util.Objects;

public record GateEvaluationCase(
        GateEvaluationCategory category,
        String message,
        ModerationAction expectedAction
) {
    public GateEvaluationCase {
        Objects.requireNonNull(category, "category must not be null");
        Objects.requireNonNull(message, "message must not be null");
        Objects.requireNonNull(expectedAction, "expectedAction must not be null");
    }
}
