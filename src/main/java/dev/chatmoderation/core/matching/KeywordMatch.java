package dev.chatmoderation.core.matching;

/**
 * A keyword occurrence in the searched Java string.
 * The start index is inclusive and the end index is exclusive.
 */
public record KeywordMatch(
        String keyword,
        int startIndex,
        int endIndex
) {
}
