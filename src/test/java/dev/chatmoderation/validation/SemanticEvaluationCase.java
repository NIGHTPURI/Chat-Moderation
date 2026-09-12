package dev.chatmoderation.validation;

import dev.chatmoderation.core.model.ModerationAction;
import dev.chatmoderation.core.model.ModerationReason;

record SemanticEvaluationCase(
        SemanticEvaluationCategory category,
        String message,
        ModerationAction expectedAction,
        ModerationReason expectedReason
) {
}
