package dev.chatmoderation.core.rule;

import dev.chatmoderation.core.model.ModerationReason;

import java.util.regex.Pattern;

public final class EmailRule extends RegexMessageRule {
    private static final Pattern EMAIL = Pattern.compile(
            "(?<![A-Z0-9._%+-])"
                    + "[A-Z0-9]+(?:[._%+-][A-Z0-9]+)*@"
                    + "[A-Z0-9](?:[A-Z0-9-]{0,61}[A-Z0-9])?"
                    + "(?:\\.[A-Z]{2,63})+"
                    + "(?![A-Z0-9_%+-])",
            Pattern.CASE_INSENSITIVE
    );

    public EmailRule() {
        super(EMAIL, ModerationReason.PERSONAL_INFORMATION);
    }
}
