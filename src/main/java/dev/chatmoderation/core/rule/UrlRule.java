package dev.chatmoderation.core.rule;

import dev.chatmoderation.core.model.ModerationReason;

import java.util.regex.Pattern;

public final class UrlRule extends RegexMessageRule {
    private static final String HOST_LABEL =
            "[A-Z0-9](?:[A-Z0-9-]{0,61}[A-Z0-9])?";
    private static final String ANY_HOST = "(?:" + HOST_LABEL + "\\.)+[A-Z]{2,63}";
    private static final String SCHEMELESS_HOST =
            "(?:" + HOST_LABEL + "\\.)+(?:COM|NET|ORG|IO|DEV|AI|CO\\.KR)";
    private static final String PORT = "(?::\\d{1,5})?";
    private static final String PATH = "(?:/[A-Z0-9._~:/?#\\[\\]@!$&'()*+,;=%-]*)?";
    private static final Pattern URL = Pattern.compile(
            "(?<![A-Z0-9])HTTPS?://" + ANY_HOST + PORT + PATH
                    + "|(?<![\\p{L}\\p{N}@._%+-])"
                    + SCHEMELESS_HOST + PORT + PATH
                    + "(?![A-Z0-9-])",
            Pattern.CASE_INSENSITIVE
    );

    public UrlRule() {
        super(URL, ModerationReason.URL);
    }
}
