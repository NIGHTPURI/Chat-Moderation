package dev.chatmoderation.core.rule;

import dev.chatmoderation.core.model.ModerationReason;

import java.util.regex.Pattern;

public final class UrlRule extends RegexMessageRule {
    private static final Pattern URL = Pattern.compile(
            "(?<![A-Z0-9])https?://"
                    + "(?:[A-Z0-9](?:[A-Z0-9-]{0,61}[A-Z0-9])?\\.)+"
                    + "[A-Z]{2,63}"
                    + "(?::\\d{1,5})?"
                    + "(?:/[A-Z0-9._~:/?#\\[\\]@!$&'()*+,;=%-]*)?",
            Pattern.CASE_INSENSITIVE
    );

    public UrlRule() {
        super(URL, ModerationReason.URL);
    }
}
