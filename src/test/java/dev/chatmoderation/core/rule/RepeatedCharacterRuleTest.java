package dev.chatmoderation.core.rule;

import dev.chatmoderation.core.model.ModerationReason;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepeatedCharacterRuleTest {
    private final MessageRule rule = new RepeatedCharacterRule();

    @Test
    void findsLongKoreanCharacterRun() {
        assertEquals(
                List.of(new RuleMatch(
                        ModerationReason.SPAM,
                        "ㅋㅋㅋㅋㅋㅋㅋㅋ",
                        0,
                        8
                )),
                rule.findAll("ㅋㅋㅋㅋㅋㅋㅋㅋ")
        );
    }

    @Test
    void findsLongEnglishCharacterRun() {
        assertEquals(
                List.of(new RuleMatch(
                        ModerationReason.SPAM,
                        "aaaaaaaaaa",
                        2,
                        12
                )),
                rule.findAll("x aaaaaaaaaa 끝")
        );
    }

    @Test
    void allowsShortRepeatedCharacters() {
        assertTrue(rule.findAll("ㅋㅋ 좋아요 ㅎㅎㅎ").isEmpty());
    }

    @Test
    void allowsOrdinarySentence() {
        assertTrue(rule.findAll("반복되지 않는 정상 문장입니다").isEmpty());
    }

    @Test
    void allowsLongWhitespaceRun() {
        assertTrue(rule.findAll("앞          뒤").isEmpty());
    }

    @Test
    void countsSupplementaryCharactersByCodePoint() {
        String emojis = "😀".repeat(8);

        assertEquals(
                List.of(new RuleMatch(ModerationReason.SPAM, emojis, 0, 16)),
                rule.findAll(emojis)
        );
    }

    @Test
    void configurableThresholdIsKeptInOneRuleInstance() {
        MessageRule stricterRule = new RepeatedCharacterRule(10);

        assertTrue(stricterRule.findAll("aaaaaaaaa").isEmpty());
        assertEquals(1, stricterRule.findAll("aaaaaaaaaa").size());
        assertThrows(IllegalArgumentException.class, () -> new RepeatedCharacterRule(1));
    }
}
