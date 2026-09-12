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
    void findsDomainWithoutScheme() {
        assertEquals(
                List.of(new RuleMatch(ModerationReason.URL, "example.com", 6, 17)),
                rule.findAll("visit example.com now")
        );
    }

    @Test
    void findsWwwDomainWithoutScheme() {
        assertEquals(
                List.of(new RuleMatch(ModerationReason.URL, "www.example.com", 0, 15)),
                rule.findAll("www.example.com으로 이동")
        );
    }

    @Test
    void doesNotMatchDomainInsideEmail() {
        assertTrue(rule.findAll("user@example.com").isEmpty());
    }

    @Test
    void allowsVersionAndUnlistedTopLevelDomain() {
        assertTrue(rule.findAll("version 1.2.3").isEmpty());
        assertTrue(rule.findAll("foo.bar가 일반 문자열인 경우").isEmpty());
    }

    @Test
    void requiresHostnameBoundary() {
        assertTrue(rule.findAll("접두사example.com은 일반 문자열입니다").isEmpty());
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
