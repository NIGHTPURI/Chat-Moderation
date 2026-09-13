package dev.chatmoderation.core;

import dev.chatmoderation.core.model.ModerationAction;
import dev.chatmoderation.core.model.ModerationReason;
import dev.chatmoderation.core.model.ModerationResult;
import dev.chatmoderation.semantic.ProviderFailurePolicy;
import dev.chatmoderation.semantic.SemanticModerationProvider;
import dev.chatmoderation.semantic.SemanticModerationResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductionSemanticModerationServiceTest {
    @Test
    void keepsCertainResultsLocalAndRoutesSemanticRisk() {
        AtomicInteger calls = new AtomicInteger();
        SemanticModerationProvider provider = message -> {
            calls.incrementAndGet();
            return SemanticModerationResult.block(
                    List.of(ModerationReason.HARASSMENT), 0.98, "fake");
        };
        ChatModerationService service = ChatModerationServices.withSemanticProvider(
                provider, ProviderFailurePolicy.DETERMINISTIC_FALLBACK);

        assertEquals(ModerationAction.ALLOW, service.moderate("오늘 날씨가 좋다").action());
        assertEquals(ModerationAction.BLOCK, service.moderate("씨발 그만해").action());
        assertEquals(0, calls.get());

        ModerationResult semanticBlock = service.moderate("너는 정말 한심한 사람이야");
        assertEquals(ModerationAction.BLOCK, semanticBlock.action());
        assertEquals(List.of(ModerationReason.HARASSMENT), semanticBlock.reasons());
        assertEquals(1, calls.get());
    }

    @Test
    void semanticAllowCanRescueContextSensitiveDeterministicFalsePositive() {
        ChatModerationService service = ChatModerationServices.withSemanticProvider(
                message -> SemanticModerationResult.allow(0.99, "fake"),
                ProviderFailurePolicy.DETERMINISTIC_FALLBACK);

        ModerationResult result = service.moderate("공학 보고서에서 후장식 구조를 설명합니다");

        assertEquals(ModerationAction.ALLOW, result.action());
        assertEquals("공학 보고서에서 후장식 구조를 설명합니다", result.outputMessage());
    }

    @Test
    void routedMaskedMessageSendsOnlySanitizedContentAndAllowCannotRestoreIt() {
        AtomicReference<String> providerInput = new AtomicReference<>();
        ChatModerationService service = ChatModerationServices.withSemanticProvider(message -> {
            providerInput.set(message);
            return SemanticModerationResult.allow(0.97, "fake");
        }, ProviderFailurePolicy.DETERMINISTIC_FALLBACK);

        ModerationResult result = service.moderate("몸 사진은 010-1234-5678로 보내 줘");

        assertFalse(providerInput.get().contains("010-1234-5678"));
        assertEquals("몸 사진은 *************로 보내 줘", providerInput.get());
        assertEquals(ModerationAction.MASK, result.action());
        assertEquals("몸 사진은 *************로 보내 줘", result.outputMessage());
    }

    @Test
    void blockPrecedenceCannotExposeOrRestoreDetectedPersonalInformation() {
        AtomicReference<String> providerInput = new AtomicReference<>();
        ChatModerationService service = ChatModerationServices.withSemanticProvider(message -> {
            providerInput.set(message);
            return SemanticModerationResult.allow(0.99, "fake");
        }, ProviderFailurePolicy.DETERMINISTIC_FALLBACK);

        ModerationResult result = service.moderate(
                "공학 보고서에서 후장식 구조와 010-1234-5678을 설명합니다");

        assertFalse(providerInput.get().contains("010-1234-5678"));
        assertTrue(providerInput.get().contains("*************"));
        assertEquals(ModerationAction.MASK, result.action());
        assertFalse(result.outputMessage().contains("010-1234-5678"));
    }

    @Test
    void deterministicFallbackIsExplicitForTimeoutAndProviderException() {
        ChatModerationService timeout = ChatModerationServices.withSemanticProvider(
                message -> SemanticModerationResult.unavailable(
                        SemanticModerationResult.Status.TIMEOUT, "fake"),
                ProviderFailurePolicy.DETERMINISTIC_FALLBACK);
        ChatModerationService unavailable = ChatModerationServices.withSemanticProvider(
                message -> { throw new IllegalStateException("unavailable"); },
                ProviderFailurePolicy.DETERMINISTIC_FALLBACK);

        assertEquals(ModerationAction.ALLOW,
                timeout.moderate("너는 한심한 사람이야").action());
        assertEquals(ModerationAction.ALLOW,
                unavailable.moderate("너는 한심한 사람이야").action());
    }

    @Test
    void failClosedAndFailOpenAreConfigurableAndMaskIsAlwaysPreserved() {
        SemanticModerationProvider timeout = message -> SemanticModerationResult.unavailable(
                SemanticModerationResult.Status.TIMEOUT, "fake");
        ChatModerationService closed = ChatModerationServices.withSemanticProvider(
                timeout, ProviderFailurePolicy.FAIL_CLOSED);
        ChatModerationService open = ChatModerationServices.withSemanticProvider(
                timeout, ProviderFailurePolicy.FAIL_OPEN);

        ModerationResult closedResult = closed.moderate("너는 한심한 사람이야");
        assertEquals(ModerationAction.BLOCK, closedResult.action());
        assertEquals(List.of(ModerationReason.OTHER), closedResult.reasons());
        assertNull(closedResult.outputMessage());

        ModerationResult masked = open.moderate("몸 사진은 010-1234-5678로 보내 줘");
        assertEquals(ModerationAction.MASK, masked.action());
        assertFalse(masked.outputMessage().contains("010-1234-5678"));
    }

    @Test
    void defaultResourcesIncludeValidatedKeywordAndExceptionBehavior() {
        ChatModerationService deterministic = ChatModerationServices.deterministic();

        assertEquals(ModerationAction.BLOCK, deterministic.moderate("씨아발").action());
        assertEquals(ModerationAction.ALLOW, deterministic.moderate("새로운 시발점이다").action());
        assertTrue(deterministic.moderate("메일은 user@example.com").allowed());
    }
}
