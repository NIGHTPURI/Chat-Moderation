package dev.chatmoderation.validation;

import dev.chatmoderation.core.model.ModerationAction;
import dev.chatmoderation.core.model.ModerationReason;

record CorpusCase(
        CorpusCategory category,
        String message,
        ModerationAction expectedAction,
        ModerationReason expectedReason,
        ExpectedOutput expectedOutput
) {
    @Override
    public String toString() {
        return category + ": " + message;
    }
}
