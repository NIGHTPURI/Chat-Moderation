package dev.chatmoderation.core.rule;

import dev.chatmoderation.core.model.ModerationReason;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultRuleFilterTest {

    @Test
    void combinesIndependentRuleMatches() {
        RuleFilter filter = new DefaultRuleFilter();
        String message = "010-1234-5678 user@example.com https://example.com ㅋㅋㅋㅋㅋㅋㅋㅋ";

        assertEquals(
                List.of(
                        ModerationReason.PERSONAL_INFORMATION,
                        ModerationReason.PERSONAL_INFORMATION,
                        ModerationReason.URL,
                        ModerationReason.SPAM
                ),
                filter.findAll(message).stream().map(RuleMatch::reason).toList()
        );
    }

    @Test
    void returnsNoMatchesForNormalMessage() {
        RuleFilter filter = new DefaultRuleFilter();

        assertTrue(filter.findAll("2026-09-12 회의에는 42명이 참석했습니다").isEmpty());
    }
}
