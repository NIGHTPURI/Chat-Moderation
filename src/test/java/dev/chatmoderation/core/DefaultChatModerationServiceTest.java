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
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @Test
    void reportsKeywordCategoryFromSingleDictionaryConfiguration() {
        ChatModerationService service = categorizedService();

        assertBlockedFor(service.moderate("씨발아"), ModerationReason.PROFANITY);
        assertBlockedFor(service.moderate("포르노"), ModerationReason.SEXUAL_CONTENT);
    }

    @Test
    void reportsBothCategoriesWhenBothAreFound() {
        ChatModerationService service = categorizedService();

        assertBlockedFor(
                service.moderate("씨발 섹스"),
                ModerationReason.PROFANITY,
                ModerationReason.SEXUAL_CONTENT
        );
    }

    @Test
    void exceptionInvalidatesOnlyTheCoveredKeywordMatch() {
        ChatModerationService service = categorizedService();

        assertAllowed(service.moderate("문제의 시발점은 여기다"), ModerationAction.ALLOW);
        assertBlockedFor(
                service.moderate("시발점에서 시작했지만 시발아"),
                ModerationReason.PROFANITY
        );
    }

    @Test
    void exceptionDoesNotSuppressProfanityWithDifferentSuffixes() {
        ChatModerationService service = categorizedService();

        assertBlockedFor(service.moderate("시발"), ModerationReason.PROFANITY);
        assertBlockedFor(service.moderate("시발아"), ModerationReason.PROFANITY);
        assertBlockedFor(service.moderate("시발놈"), ModerationReason.PROFANITY);
    }

    @Test
    void exceptionDoesNotSuppressAnotherKeywordInsideTheSameText() {
        ChatModerationService service = new DefaultChatModerationService(
                Map.of(ModerationReason.PROFANITY, List.of("시발", "발점")),
                Map.of("시발", List.of("시발점"))
        );

        assertBlockedFor(service.moderate("시발점"), ModerationReason.PROFANITY);
    }

    @Test
    void rejectsExceptionThatEqualsBlockingKeyword() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new DefaultChatModerationService(
                        Map.of(ModerationReason.PROFANITY, List.of("시발")),
                        Map.of("시발", List.of(" 시발 "))
                )
        );
    }

    @Test
    void detectsNumericInsertionByKeywordCategory() {
        ChatModerationService service = obfuscationService();

        assertBlockedFor(service.moderate("씨1발"), ModerationReason.PROFANITY);
        assertBlockedFor(service.moderate("지1랄"), ModerationReason.PROFANITY);
        assertBlockedFor(service.moderate("병1신"), ModerationReason.PROFANITY);
        assertBlockedFor(service.moderate("섹1스"), ModerationReason.SEXUAL_CONTENT);
    }

    @Test
    void detectsSupportedSeparatorsOnlyBetweenKeywordSyllables() {
        ChatModerationService service = obfuscationService();

        for (String message : List.of(
                "씨 발",
                "씨.발",
                "씨-발",
                "씨_발",
                "씨*발",
                "씨/발"
        )) {
            assertBlockedFor(service.moderate(message), ModerationReason.PROFANITY);
        }
        assertBlockedFor(service.moderate("섹 스"), ModerationReason.SEXUAL_CONTENT);
        assertBlockedFor(service.moderate("섹/스"), ModerationReason.SEXUAL_CONTENT);
    }

    @Test
    void detectsOnlyExplicitShorthandAndKnownVariantAliases() {
        ChatModerationService service = obfuscationService();

        for (String message : List.of("ㅅㅂ", "ㅆㅂ", "ㅈㄴ", "씨아발")) {
            assertBlockedFor(service.moderate(message), ModerationReason.PROFANITY);
        }
    }

    @Test
    void detectsCalibratedExplicitAliasesButAllowsWithheldShivaTerm() {
        ChatModerationService service = calibratedAliasService();

        for (String message : List.of("ㅅ ㅂ", "ㅆ.ㅂ", "ㅈ-ㄴ", "씨바", "시팔", "지럴", "븅신")) {
            assertBlockedFor(service.moderate(message), ModerationReason.PROFANITY);
        }
        assertAllowed(service.moderate("시바 신화를 공부한다"), ModerationAction.ALLOW);
        assertAllowed(service.moderate("시바견을 산책시킨다"), ModerationAction.ALLOW);
    }

    @Test
    void slashCollapseDoesNotChangeUnrelatedTechnicalText() {
        ChatModerationService service = obfuscationService();

        for (String message : List.of("A/B 테스트", "2026/09/12", "input/output", "path/to/file")) {
            assertAllowed(service.moderate(message), ModerationAction.ALLOW);
        }
    }

    @Test
    void appliesExceptionInsideSeparatorCollapsedViewOnlyToCoveredMatch() {
        ChatModerationService service = obfuscationService();

        assertAllowed(service.moderate("시 발점"), ModerationAction.ALLOW);
        assertAllowed(service.moderate("시.발점"), ModerationAction.ALLOW);
        assertBlockedFor(service.moderate("시 발아"), ModerationReason.PROFANITY);
        assertBlockedFor(
                service.moderate("시 발점 이후 씨 발"),
                ModerationReason.PROFANITY
        );
    }

    @Test
    void keepsCanonicalOutputAndOriginalRuleRangesWhenUsingCollapsedView() {
        ChatModerationService service = obfuscationService();

        ModerationResult result = service.moderate("  시 발점 user@example.com  ");

        assertMaskedFor(result, ModerationReason.PERSONAL_INFORMATION);
        assertEquals("시 발점 ****************", result.outputMessage());
    }

    @Test
    void preservesUnrelatedNumbersAndSeparators() {
        ChatModerationService service = obfuscationService();

        for (String message : List.of(
                "2026년",
                "Java21",
                "GPT5",
                "오늘 1시에 출발",
                "버전 1.2.3",
                "A-B 테스트",
                "snake_case"
        )) {
            assertAllowed(service.moderate(message), ModerationAction.ALLOW);
        }
    }

    private ChatModerationService categorizedService() {
        return new DefaultChatModerationService(
                Map.of(
                        ModerationReason.PROFANITY, List.of("시발", "씨발"),
                        ModerationReason.SEXUAL_CONTENT, List.of("섹스", "포르노")
                ),
                Map.of("시발", List.of("시발점"))
        );
    }

    private ChatModerationService obfuscationService() {
        return new DefaultChatModerationService(
                Map.of(
                        ModerationReason.PROFANITY, List.of("시발", "씨발", "지랄", "병신"),
                        ModerationReason.SEXUAL_CONTENT, List.of("섹스")
                ),
                Map.of("시발", List.of("시발점")),
                Map.of(
                        ModerationReason.PROFANITY,
                        List.of("ㅅㅂ", "ㅆㅂ", "ㅈㄴ", "씨아발")
                )
        );
    }

    private ChatModerationService calibratedAliasService() {
        return new DefaultChatModerationService(
                Map.of(ModerationReason.PROFANITY, List.of("시발")),
                Map.of("시발", List.of("시발점")),
                Map.of(
                        ModerationReason.PROFANITY,
                        List.of("ㅅ ㅂ", "ㅆ.ㅂ", "ㅈ-ㄴ", "씨바", "시팔", "지럴", "븅신")
                )
        );
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
