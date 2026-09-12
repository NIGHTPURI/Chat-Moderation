package dev.chatmoderation.core.rule;

import dev.chatmoderation.core.model.ModerationReason;

import java.util.Objects;

/**
 * A rule occurrence in the searched Java string.
 * The start index is inclusive and the end index is exclusive.
 */
public record RuleMatch(
        ModerationReason reason,
        String matchedText,
        int startIndex,
        int endIndex
) {
    public RuleMatch {
        Objects.requireNonNull(reason, "reason must not be null");
        Objects.requireNonNull(matchedText, "matchedText must not be null");
        if (startIndex < 0 || endIndex < startIndex) {
            throw new IllegalArgumentException("invalid match range");
        }
        if (matchedText.length() != endIndex - startIndex) {
            throw new IllegalArgumentException("matchedText length must equal match range");
        }
    }
}
