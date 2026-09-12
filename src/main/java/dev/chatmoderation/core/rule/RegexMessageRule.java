package dev.chatmoderation.core.rule;

import dev.chatmoderation.core.model.ModerationReason;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

abstract class RegexMessageRule implements MessageRule {
    private final Pattern pattern;
    private final ModerationReason reason;

    RegexMessageRule(Pattern pattern, ModerationReason reason) {
        this.pattern = pattern;
        this.reason = reason;
    }

    @Override
    public final List<RuleMatch> findAll(String message) {
        Objects.requireNonNull(message, "message must not be null");

        List<RuleMatch> matches = new ArrayList<>();
        Matcher matcher = pattern.matcher(message);
        while (matcher.find()) {
            matches.add(new RuleMatch(
                    reason,
                    matcher.group(),
                    matcher.start(),
                    matcher.end()
            ));
        }
        return List.copyOf(matches);
    }
}
