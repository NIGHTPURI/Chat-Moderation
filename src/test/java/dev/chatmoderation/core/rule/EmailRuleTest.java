package dev.chatmoderation.core.rule;

import dev.chatmoderation.core.model.ModerationReason;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmailRuleTest {
    private final MessageRule rule = new EmailRule();

    @Test
    void findsValidEmail() {
        assertEquals(
                List.of(new RuleMatch(
                        ModerationReason.PERSONAL_INFORMATION,
                        "user.name+chat@example.co.kr",
                        3,
                        31
                )),
                rule.findAll("메일 user.name+chat@example.co.kr 주세요")
        );
    }

    @Test
    void findsEmailBeforeSentencePeriod() {
        assertEquals(
                List.of(new RuleMatch(
                        ModerationReason.PERSONAL_INFORMATION,
                        "user@example.com",
                        0,
                        16
                )),
                rule.findAll("user@example.com.")
        );
    }

    @Test
    void rejectsAddressWithoutTopLevelDomain() {
        assertTrue(rule.findAll("user@example").isEmpty());
    }

    @Test
    void rejectsAddressWithConsecutiveDots() {
        assertTrue(rule.findAll("user..name@example.com").isEmpty());
    }

    @Test
    void allowsOrdinaryText() {
        assertTrue(rule.findAll("사용자 이름과 도메인을 입력하세요").isEmpty());
    }
}
