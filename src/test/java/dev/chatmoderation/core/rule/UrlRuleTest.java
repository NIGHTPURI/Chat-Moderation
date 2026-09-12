package dev.chatmoderation.core.rule;

import dev.chatmoderation.core.model.ModerationReason;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UrlRuleTest {
    private final MessageRule rule = new UrlRule();

    @Test
    void findsHttpUrl() {
        assertEquals(
                List.of(new RuleMatch(
                        ModerationReason.URL,
                        "http://example.com",
                        3,
                        21
                )),
                rule.findAll("링크 http://example.com 확인")
        );
    }

    @Test
    void findsHttpsUrlWithPathAndQuery() {
        assertEquals(
                List.of(new RuleMatch(
                        ModerationReason.URL,
                        "https://sub.example.com/path?q=1",
                        0,
                        32
                )),
                rule.findAll("https://sub.example.com/path?q=1")
        );
    }

    @Test
    void allowsDomainWithoutScheme() {
        assertTrue(rule.findAll("example.com은 예시 도메인입니다").isEmpty());
    }

    @Test
    void allowsTextThatOnlyLooksLikeScheme() {
        assertTrue(rule.findAll("http example 또는 https 설명").isEmpty());
    }

    @Test
    void rejectsHostWithoutTopLevelDomain() {
        assertTrue(rule.findAll("http://localhost/test").isEmpty());
    }
}
