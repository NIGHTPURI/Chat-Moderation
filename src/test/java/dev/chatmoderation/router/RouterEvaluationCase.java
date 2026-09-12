package dev.chatmoderation.router;

import dev.chatmoderation.core.model.ModerationAction;

import java.util.Objects;

public record RouterEvaluationCase(
        RouterEvaluationCategory category,
        String message,
        ModerationAction expectedAction
) {
    public RouterEvaluationCase {
        Objects.requireNonNull(category, "category must not be null");
        Objects.requireNonNull(message, "message must not be null");
        Objects.requireNonNull(expectedAction, "expectedAction must not be null");
    }
}
