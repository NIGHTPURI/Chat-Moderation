package dev.chatmoderation.core.rule;

import dev.chatmoderation.core.model.ModerationReason;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhoneNumberRuleTest {
    private final MessageRule rule = new PhoneNumberRule();

    @Test
    void findsHyphenatedMobileNumber() {
        assertEquals(
                List.of(new RuleMatch(
                        ModerationReason.PERSONAL_INFORMATION,
                        "010-1234-5678",
                        5,
                        18
                )),
                rule.findAll("연락처: 010-1234-5678 입니다")
        );
    }

    @Test
    void findsCompactMobileNumber() {
        assertEquals(
                List.of(new RuleMatch(
                        ModerationReason.PERSONAL_INFORMATION,
                        "01012345678",
                        0,
                        11
                )),
                rule.findAll("01012345678")
        );
    }

    @Test
    void allowsOrdinaryNumbers() {
        assertTrue(rule.findAll("오늘 참가자는 42명이고 점수는 100점입니다").isEmpty());
    }

    @Test
    void allowsDatesAndNumericIds() {
        assertTrue(rule.findAll("날짜 2026-09-12, 주문 ID 1234567890").isEmpty());
    }

    @Test
    void doesNotMatchMobileNumberInsideLongerNumber() {
        assertTrue(rule.findAll("ID 9010123456789").isEmpty());
    }
}
