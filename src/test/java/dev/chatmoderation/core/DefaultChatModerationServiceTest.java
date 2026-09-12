package dev.chatmoderation.core;

import dev.chatmoderation.core.model.ModerationAction;
import dev.chatmoderation.core.model.ModerationReason;
import dev.chatmoderation.core.model.ModerationResult;
import dev.chatmoderation.core.normalization.DefaultMessageNormalizer;
import dev.chatmoderation.core.normalization.MessageNormalizer;
import dev.chatmoderation.core.policy.DefaultModerationPolicy;
import dev.chatmoderation.core.policy.ModerationPolicy;
import dev.chatmoderation.core.rule.DefaultRuleFilter;
import dev.chatmoderation.core.rule.RuleMatch;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultChatModerationServiceTest {

    @Test
    void allowsNormalMessageAndReturnsCanonicalMessage() {
        ChatModerationService service = serviceWith("욕설");

        ModerationResult result = service.moderate("  정상 메시지  ");

        assertAllowed(result, ModerationAction.ALLOW);
        assertEquals("정상 메시지", result.outputMessage());
        assertTrue(result.reasons().isEmpty());
    }

    @Test
    void blocksProfanityAndOmitsOutput() {
        ChatModerationService service = serviceWith("욕설");

        ModerationResult result = service.moderate("여기에 욕설 포함");

        assertBlockedFor(result, ModerationReason.PROFANITY);
    }

    @Test
    void normalizesUppercaseKeywordBeforeBuildingMatcher() {
        ChatModerationService service = serviceWith("BADWORD");

        assertBlockedFor(service.moderate("contains badword"), ModerationReason.PROFANITY);
    }

    @Test
    void stripsKeywordBeforeBuildingMatcher() {
        ChatModerationService service = serviceWith("  badword  ");

        assertBlockedFor(service.moderate("contains BADWORD"), ModerationReason.PROFANITY);
    }

    @Test
    void matchesNfdInputAgainstNfcKeyword() {
        ChatModerationService service = serviceWith("카페");

        assertBlockedFor(service.moderate("카페"), ModerationReason.PROFANITY);
    }

    @Test
    void masksPhoneNumberUsingCanonicalRange() {
        ChatModerationService service = serviceWith("욕설");

        ModerationResult result = service.moderate("  전화 010-1234-5678 주세요  ");

        assertMaskedFor(result, ModerationReason.PERSONAL_INFORMATION);
        assertEquals("전화 ************* 주세요", result.outputMessage());
    }

    @Test
    void masksEmailUsingCanonicalRange() {
        ChatModerationService service = serviceWith("욕설");

        ModerationResult result = service.moderate("메일 user@example.com 입니다");

        assertMaskedFor(result, ModerationReason.PERSONAL_INFORMATION);
        assertEquals("메일 **************** 입니다", result.outputMessage());
    }

    @Test
    void blocksUrlAndOmitsOutput() {
        ChatModerationService service = serviceWith("욕설");

        ModerationResult result = service.moderate("https://example.com 방문");

        assertBlockedFor(result, ModerationReason.URL);
    }

    @Test
    void blocksRepeatedCharactersAndOmitsOutput() {
        ChatModerationService service = serviceWith("욕설");

        ModerationResult result = service.moderate("ㅋㅋㅋㅋㅋㅋㅋㅋ");

        assertBlockedFor(result, ModerationReason.SPAM);
    }

    @Test
    void blockTakesPriorityWhenProfanityAndPersonalInformationAreBothFound() {
        ChatModerationService service = serviceWith("욕설");

        ModerationResult result = service.moderate("욕설 010-1234-5678");

        assertFalse(result.allowed());
        assertEquals(ModerationAction.BLOCK, result.action());
        assertEquals(
                List.of(ModerationReason.PROFANITY, ModerationReason.PERSONAL_INFORMATION),
                result.reasons()
        );
        assertNull(result.outputMessage());
    }

    @Test
    void allowsEmptyCanonicalMessage() {
        ChatModerationService service = serviceWith("욕설");

        ModerationResult result = service.moderate(" \n\t ");

        assertAllowed(result, ModerationAction.ALLOW);
        assertEquals("", result.outputMessage());
    }

    @Test
    void reusesServiceAndPrebuiltMatcherAcrossMessages() {
        CountingNormalizer normalizer = new CountingNormalizer();
        ChatModerationService service = new DefaultChatModerationService(
                normalizer,
                List.of(" BAD ", "bad"),
                new DefaultRuleFilter(),
                new DefaultModerationPolicy()
        );
        assertEquals(2, normalizer.invocationCount());

        assertBlockedFor(service.moderate("bad"), ModerationReason.PROFANITY);
        assertAllowed(service.moderate("clean"), ModerationAction.ALLOW);

        assertEquals(4, normalizer.invocationCount());
    }

    @Test
    void passesNormalizedKeywordAsReasonOnlyAndKeepsRuleRangesCanonical() {
        AtomicReference<List<ModerationReason>> capturedReasonOnly = new AtomicReference<>();
        AtomicReference<List<RuleMatch>> capturedOriginalMatches = new AtomicReference<>();
        ModerationPolicy delegate = new DefaultModerationPolicy();
        ModerationPolicy capturingPolicy = (canonical, reasonOnly, originalMatches) -> {
            capturedReasonOnly.set(reasonOnly);
            capturedOriginalMatches.set(originalMatches);
            return delegate.decide(canonical, reasonOnly, originalMatches);
        };
        ChatModerationService service = new DefaultChatModerationService(
                new DefaultMessageNormalizer(),
                List.of("évil"),
                new DefaultRuleFilter(),
                capturingPolicy
        );
        String message = "e\u0301vil user@example.com";

        ModerationResult result = service.moderate(message);

        assertEquals(List.of(ModerationReason.PROFANITY), capturedReasonOnly.get());
        assertEquals(1, capturedOriginalMatches.get().size());
        RuleMatch emailMatch = capturedOriginalMatches.get().getFirst();
        assertEquals(ModerationReason.PERSONAL_INFORMATION, emailMatch.reason());
        assertEquals(message.indexOf("user@example.com"), emailMatch.startIndex());
        assertEquals("user@example.com", emailMatch.matchedText());
        assertBlockedFor(result, ModerationReason.PROFANITY, ModerationReason.PERSONAL_INFORMATION);
    }

    private ChatModerationService serviceWith(String keyword) {
        return new DefaultChatModerationService(List.of(keyword));
    }

    private void assertAllowed(ModerationResult result, ModerationAction action) {
        assertTrue(result.allowed());
        assertEquals(action, result.action());
    }

    private void assertMaskedFor(ModerationResult result, ModerationReason reason) {
        assertAllowed(result, ModerationAction.MASK);
        assertEquals(List.of(reason), result.reasons());
    }

    private void assertBlockedFor(ModerationResult result, ModerationReason... reasons) {
        assertFalse(result.allowed());
        assertEquals(ModerationAction.BLOCK, result.action());
        assertEquals(List.of(reasons), result.reasons());
        assertNull(result.outputMessage());
    }

    private static final class CountingNormalizer implements MessageNormalizer {
        private final MessageNormalizer delegate = new DefaultMessageNormalizer();
        private int invocationCount;

        @Override
        public String normalize(String message) {
            invocationCount++;
            return delegate.normalize(message);
        }

        int invocationCount() {
            return invocationCount;
        }
    }
}
