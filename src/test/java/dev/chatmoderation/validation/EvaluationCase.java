package dev.chatmoderation.validation;

import dev.chatmoderation.core.model.ModerationAction;
import dev.chatmoderation.core.model.ModerationReason;

record EvaluationCase(
        EvaluationCategory category,
        String message,
        ModerationAction expectedAction,
        ModerationReason expectedReason,
        EvaluationSupport support
) {
}
