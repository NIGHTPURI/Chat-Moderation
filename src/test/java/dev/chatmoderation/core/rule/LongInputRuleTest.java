package dev.chatmoderation.core.rule;

import dev.chatmoderation.core.DefaultChatModerationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(15)
class LongInputRuleTest {
    private static final String LABEL = "[A-Z0-9](?:[A-Z0-9-]{0,61}[A-Z0-9])?";
    private static final String PORT = "(?::\\d{1,5})?";
    private static final String PATH = "(?:/[A-Z0-9._~:/?#\\[\\]@!$&'()*+,;=%-]*)?";
    // Pre-fix patterns are an oracle for ordinary-input compatibility only.
    private static final Pattern OLD_EMAIL = Pattern.compile(
            "(?<![A-Z0-9._%+-])[A-Z0-9]+(?:[._%+-][A-Z0-9]+)*@"
                    + LABEL + "(?:\\.[A-Z]{2,63})+(?![A-Z0-9_%+-])", Pattern.CASE_INSENSITIVE);
    private static final Pattern OLD_URL = Pattern.compile(
            "(?<![A-Z0-9])HTTPS?://(?:" + LABEL + "\\.)+[A-Z]{2,63}" + PORT + PATH
                    + "|(?<![\\p{L}\\p{N}@._%+-])(?:" + LABEL + "\\.)+(?:COM|NET|ORG|IO|DEV|AI|CO\\.KR)"
                    + PORT + PATH + "(?![A-Z0-9-])", Pattern.CASE_INSENSITIVE);

    @Test void longEmailLocalPartAndDomainDoNotOverflow() {
        var rule = new EmailRule();
        for (String email : List.of("a.".repeat(10000) + "a@example.com", "a@b" + ".ab".repeat(10000))) {
            var matches = assertDoesNotThrow(() -> rule.findAll(email));
            assertEquals(1, matches.size());
            assertEquals(email, matches.getFirst().matchedText());
            assertEquals(email.length(), matches.getFirst().endIndex());
        }
    }

    @Test void longUrlHostChainsPreserveTrailingPeriodAndKoreanTld() {
        var rule = new UrlRule();
        for (String url : List.of("http://" + "a.".repeat(10000) + "com",
                "a.".repeat(10000) + "co.kr", "https://example.com/" + "path/".repeat(10000))) {
            var matches = assertDoesNotThrow(() -> rule.findAll(url + "."));
            assertFalse(matches.isEmpty());
            // PATH permits periods; a hostname-only sentence period is excluded.
            assertEquals(url.contains("/path/") ? url + "." : url, matches.getFirst().matchedText());
        }
    }

    @Test void longNearMissesDoNotOverflowOrInventMatches() {
        assertTrue(assertDoesNotThrow(() -> new EmailRule().findAll("a.".repeat(10000) + "a")).isEmpty());
        assertTrue(assertDoesNotThrow(() -> new UrlRule().findAll("a.".repeat(10000) + "kr")).isEmpty());
    }

    @Test void emailMaskingPreservesUnicodeSurroundingTextAndOriginalOffsets() {
        String prefix = "한글🙂 ";
        String email = "a.".repeat(10000) + "a@example.com";
        var result = new DefaultChatModerationService(List.of("forbidden")).moderate(prefix + email + " 끝");
        assertTrue(result.allowed());
        assertEquals(prefix + "*".repeat(email.length()) + " 끝", result.outputMessage());
    }

    @Test void ordinaryMatchesAndOffsetsAgreeWithOriginalPatterns() {
        List<String> cases = List.of(
                "user.name+tag@example.co.kr", "a@example.com.ab1", "a@example.com.",
                "https://www.example.com/p?q=1#x", "http://example.com.", "example.co.kr",
                "naver.com에서", "example.com:123456", "a..b@example.com", "co.kr",
                "http://1.2.3.4/", "example.comx", "한글🙂 user@example.com 끝",
                "https://a.dev.co.kr/path#fragment", "http://a.co.kr:8080/a",
                "a@b.aa.ab_1", "a@b." + "a".repeat(64), "http://a." + "a".repeat(64),
                "a.com.co.kr", "a.co.kr.com", "example.com-x.net", "http://a.bb.c",
                "http://example." + "a".repeat(70), "a@b.ab.cd1", "a@example.com.1");
        for (String message : cases) compare(message);
        Random random = new Random(20261101L);
        String alphabet = "abcde012.-+_@/: 가";
        for (int n = 0; n < 5000; n++) {
            StringBuilder value = new StringBuilder();
            for (int i = 0, length = random.nextInt(60); i < length; i++)
                value.append(alphabet.charAt(random.nextInt(alphabet.length())));
            String text = value.toString();
            compare(text);
            compare("http://" + text + ".com.");
            compare("x@" + text + ".co.kr");
        }
    }

    private static void compare(String text) {
        assertEquals(ranges(OLD_EMAIL, text), ranges(new EmailRule(), text), "email: " + text);
        assertEquals(ranges(OLD_URL, text), ranges(new UrlRule(), text), "url: " + text);
    }

    private static List<String> ranges(Pattern pattern, String text) {
        var matcher = pattern.matcher(text);
        List<String> result = new ArrayList<>();
        while (matcher.find()) result.add(matcher.start() + ":" + matcher.end() + ":" + matcher.group());
        return result;
    }

    private static List<String> ranges(MessageRule rule, String text) {
        return rule.findAll(text).stream().map(m -> m.startIndex() + ":" + m.endIndex() + ":" + m.matchedText()).toList();
    }
}
