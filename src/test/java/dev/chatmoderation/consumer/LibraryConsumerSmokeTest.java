package dev.chatmoderation.consumer;

import dev.chatmoderation.core.ChatModerationService;
import dev.chatmoderation.core.ChatModerationServices;
import dev.chatmoderation.core.model.ModerationAction;
import dev.chatmoderation.core.model.ModerationReason;
import dev.chatmoderation.semantic.ProviderFailurePolicy;
import dev.chatmoderation.semantic.SemanticModerationProvider;
import dev.chatmoderation.semantic.SemanticModerationResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LibraryConsumerSmokeTest {
    @Test
    void externalPackageCanUseDeterministicDefaultsAndInjectFakeProvider() {
        ChatModerationService deterministic = ChatModerationServices.deterministic();
        SemanticModerationProvider fakeProvider = message -> SemanticModerationResult.block(
                List.of(ModerationReason.HARASSMENT), 0.99, "consumer-fake");
        ChatModerationService semantic = ChatModerationServices.withSemanticProvider(
                deterministic, fakeProvider, ProviderFailurePolicy.DETERMINISTIC_FALLBACK);

        assertEquals(ModerationAction.ALLOW, semantic.moderate("오늘 점심 먹자").action());
        assertEquals(ModerationAction.MASK,
                semantic.moderate("연락처는 010-1234-5678입니다").action());
        assertEquals(ModerationAction.BLOCK,
                semantic.moderate("너는 정말 한심한 사람이야").action());
    }
}
