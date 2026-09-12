package dev.chatmoderation.core.policy;

import dev.chatmoderation.core.model.ModerationAction;
import dev.chatmoderation.core.model.ModerationReason;
import dev.chatmoderation.core.model.ModerationResult;
import dev.chatmoderation.core.rule.RuleMatch;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultModerationPolicyTest {
    private final ModerationPolicy policy = new DefaultModerationPolicy();

    @Test
    void allowsMessageWithoutMatches() {
        ModerationResult result = policy.decide("정상 메시지", List.of());

        assertTrue(result.allowed());
        assertEquals(ModerationAction.ALLOW, result.action());
        assertEquals("정상 메시지", result.outputMessage());
        assertTrue(result.reasons().isEmpty());
    }

    @Test
    void blocksProfanity() {
        ModerationResult result = policy.decide(
                "bad word",
                List.of(new RuleMatch(ModerationReason.PROFANITY, "bad", 0, 3))
        );

        assertFalse(result.allowed());
        assertEquals(ModerationAction.BLOCK, result.action());
        assertEquals(List.of(ModerationReason.PROFANITY), result.reasons());
        assertNull(result.outputMessage());
    }

    @Test
    void blocksReasonOnlyProfanityWithoutOriginalRange() {
        ModerationResult result = policy.decide(
                "BAD word",
                List.of(ModerationReason.PROFANITY),
                List.of()
        );

        assertEquals(ModerationAction.BLOCK, result.action());
    }

    @Test
    void blocksSexualContentByDefault() {
        ModerationResult result = policy.decide(
                "sexual content",
                List.of(ModerationReason.SEXUAL_CONTENT),
                List.of()
        );

        assertFalse(result.allowed());
        assertEquals(ModerationAction.BLOCK, result.action());
        assertEquals(List.of(ModerationReason.SEXUAL_CONTENT), result.reasons());
    }

    @Test
    void canConfigureSexualContentToAllow() {
        ModerationPolicy configured = new DefaultModerationPolicy(Map.of(
                ModerationReason.SEXUAL_CONTENT,
                ModerationAction.ALLOW
        ));

        ModerationResult result = configured.decide(
                "sexual content",
                List.of(ModerationReason.SEXUAL_CONTENT),
                List.of()
        );

        assertTrue(result.allowed());
        assertEquals(ModerationAction.ALLOW, result.action());
        assertEquals(List.of(ModerationReason.SEXUAL_CONTENT), result.reasons());
    }

    @Test
    void rejectsMaskForReasonOnlyFindingWithoutOriginalRange() {
        ModerationPolicy configured = new DefaultModerationPolicy(Map.of(
                ModerationReason.PROFANITY,
                ModerationAction.MASK
        ));

        assertThrows(
                IllegalArgumentException.class,
                () -> configured.decide(
                        "BAD word",
                        List.of(ModerationReason.PROFANITY),
                        List.of()
                )
        );
    }

    @Test
    void masksPersonalInformation() {
        ModerationResult result = policy.decide(
                "call 010-1234-5678 now",
                List.of(new RuleMatch(
                        ModerationReason.PERSONAL_INFORMATION,
                        "010-1234-5678",
                        5,
                        18
                ))
        );

        assertTrue(result.allowed());
        assertEquals(ModerationAction.MASK, result.action());
        assertEquals("call ************* now", result.outputMessage());
    }

    @Test
    void blocksUrl() {
        ModerationResult result = policy.decide(
                "go https://example.com",
                List.of(new RuleMatch(ModerationReason.URL, "https://example.com", 3, 22))
        );

        assertFalse(result.allowed());
        assertEquals(ModerationAction.BLOCK, result.action());
    }

    @Test
    void blocksRepeatedCharacterSpam() {
        ModerationResult result = policy.decide(
                "aaaaaaaa",
                List.of(new RuleMatch(ModerationReason.SPAM, "aaaaaaaa", 0, 8))
        );

        assertFalse(result.allowed());
        assertEquals(ModerationAction.BLOCK, result.action());
    }

    @Test
    void blockTakesPriorityOverMask() {
        ModerationResult result = policy.decide(
                "01012345678 https://example.com",
                List.of(
                        new RuleMatch(
                                ModerationReason.PERSONAL_INFORMATION,
                                "01012345678",
                                0,
                                11
                        ),
                        new RuleMatch(
                                ModerationReason.URL,
                                "https://example.com",
                                12,
                                31
                        )
                )
        );

        assertEquals(ModerationAction.BLOCK, result.action());
        assertEquals(
                List.of(ModerationReason.PERSONAL_INFORMATION, ModerationReason.URL),
                result.reasons()
        );
    }

    @Test
    void canConfigurePersonalInformationToBlock() {
        ModerationPolicy configured = new DefaultModerationPolicy(Map.of(
                ModerationReason.PERSONAL_INFORMATION,
                ModerationAction.BLOCK
        ));

        ModerationResult result = configured.decide(
                "01012345678",
                List.of(new RuleMatch(
                        ModerationReason.PERSONAL_INFORMATION,
                        "01012345678",
                        0,
                        11
                ))
        );

        assertEquals(ModerationAction.BLOCK, result.action());
    }

    @Test
    void canConfigureUrlToAllow() {
        ModerationPolicy configured = new DefaultModerationPolicy(Map.of(
                ModerationReason.URL,
                ModerationAction.ALLOW
        ));

        ModerationResult result = configured.decide(
                "https://example.com",
                List.of(new RuleMatch(
                        ModerationReason.URL,
                        "https://example.com",
                        0,
                        19
                ))
        );

        assertTrue(result.allowed());
        assertEquals(ModerationAction.ALLOW, result.action());
        assertEquals(List.of(ModerationReason.URL), result.reasons());
    }
}
