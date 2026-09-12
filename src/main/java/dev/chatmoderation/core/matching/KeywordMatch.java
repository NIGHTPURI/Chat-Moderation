package dev.chatmoderation.core.matching;

public record KeywordMatch(
        String keyword,
        int startIndex,
        int endIndex
) {
}
