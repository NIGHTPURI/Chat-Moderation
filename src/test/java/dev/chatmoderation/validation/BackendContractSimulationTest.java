package dev.chatmoderation.validation;

import dev.chatmoderation.core.ChatModerationService;
import dev.chatmoderation.core.DefaultChatModerationService;
import dev.chatmoderation.core.model.ModerationResult;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackendContractSimulationTest {
    private final ChatModerationService service = new DefaultChatModerationService(
            ValidationResources.loadKeywordsByReason(),
            ValidationResources.loadKeywordExceptions(),
            ValidationResources.loadAliasesByReason()
    );

    @Test
    void blockHasNoContentForPersistence() {
        Optional<String> content = contentForPersistence(service.moderate("씨발"));

        assertTrue(content.isEmpty());
    }

    @Test
    void maskPersistsOutputInsteadOfOriginalPersonalInformation() {
        String original = "연락처 010-1234-5678";

        String persisted = contentForPersistence(service.moderate(original)).orElseThrow();

        assertEquals("연락처 *************", persisted);
        assertFalse(persisted.contains("010-1234-5678"));
    }

    @Test
    void allowPersistsCanonicalOutputAsReturned() {
        String persisted = contentForPersistence(service.moderate("  안녕하세요  ")).orElseThrow();

        assertEquals("안녕하세요", persisted);
    }

    private Optional<String> contentForPersistence(ModerationResult result) {
        if (!result.allowed()) {
            return Optional.empty();
        }
        return Optional.of(result.outputMessage());
    }
}
