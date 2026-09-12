package dev.chatmoderation.core.rule;

import dev.chatmoderation.core.model.ModerationReason;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class RepeatedCharacterRule implements MessageRule {
    public static final int DEFAULT_THRESHOLD = 8;

    private final int threshold;

    public RepeatedCharacterRule() {
        this(DEFAULT_THRESHOLD);
    }

    public RepeatedCharacterRule(int threshold) {
        if (threshold < 2) {
            throw new IllegalArgumentException("threshold must be at least 2");
        }
        this.threshold = threshold;
    }

    @Override
    public List<RuleMatch> findAll(String message) {
        Objects.requireNonNull(message, "message must not be null");
        if (message.isEmpty()) {
            return List.of();
        }

        List<RuleMatch> matches = new ArrayList<>();
        int runStart = 0;
        int runCodePoint = message.codePointAt(0);
        int runLength = 1;
        int index = Character.charCount(runCodePoint);

        while (index < message.length()) {
            int codePoint = message.codePointAt(index);
            if (codePoint == runCodePoint) {
                runLength++;
            } else {
                addMatchIfExcessive(message, matches, runStart, index, runLength);
                runStart = index;
                runCodePoint = codePoint;
                runLength = 1;
            }
            index += Character.charCount(codePoint);
        }

        addMatchIfExcessive(message, matches, runStart, message.length(), runLength);
        return List.copyOf(matches);
    }

    private void addMatchIfExcessive(
            String message,
            List<RuleMatch> matches,
            int startIndex,
            int endIndex,
            int runLength
    ) {
        int codePoint = message.codePointAt(startIndex);
        if (runLength >= threshold && !Character.isWhitespace(codePoint)) {
            matches.add(new RuleMatch(
                    ModerationReason.SPAM,
                    message.substring(startIndex, endIndex),
                    startIndex,
                    endIndex
            ));
        }
    }
}
