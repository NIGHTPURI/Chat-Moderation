package dev.chatmoderation.core.rule;

import dev.chatmoderation.core.model.ModerationReason;

import java.util.regex.Pattern;

public final class UrlRule extends RegexMessageRule {
    private static final String HOST_LABEL =
            "[A-Z0-9](?:[A-Z0-9-]{0,61}[A-Z0-9])?";
    private static final String PORT = "(?::\\d{1,5})?";
    private static final String PATH = "(?:/[A-Z0-9._~:/?#\\[\\]@!$&'()*+,;=%-]*)?";
    private static final Pattern URL = Pattern.compile(
            "(?<![A-Z0-9])HTTPS?://" + hostChainThen("[A-Z]{2,63}" + PORT + PATH)
                    + "|(?<![\\p{L}\\p{N}@._%+-])"
                    + hostChainThen("(?:COM|NET|ORG|IO|DEV|AI|CO\\.KR)" + PORT + PATH + "(?![A-Z0-9-])"),
            Pattern.CASE_INSENSITIVE
    );

    public UrlRule() {
        super(URL, ModerationReason.URL);
    }

    private static String hostChainThen(String tail) {
        // Keep the rightmost valid suffix without recursive label backtracking.
        // A plain possessive host chain would swallow co. in example.co.kr.
        String nonTerminal = "(?:(?!" + tail + ")" + HOST_LABEL + "\\.)";
        String possibleTerminal = "(?=" + tail + ")" + HOST_LABEL + "\\.";
        return "(?:" + nonTerminal + "*+" + possibleTerminal + "(?=" + nonTerminal + "*+" + tail + "))*+"
                + nonTerminal + "*+(?<=\\.)" + tail;
    }
}
