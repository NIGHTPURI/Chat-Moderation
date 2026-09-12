package dev.chatmoderation.validation;

import dev.chatmoderation.core.ChatModerationService;
import dev.chatmoderation.core.DefaultChatModerationService;
import dev.chatmoderation.core.model.ModerationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CorpusModerationTest {
    private static final List<CorpusCase> CORPUS = ValidationResources.loadCorpus();
    private static final ChatModerationService SERVICE = new DefaultChatModerationService(
            ValidationResources.loadKeywordsByReason(),
            ValidationResources.loadKeywordExceptions()
    );

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("corpusCases")
    void matchesExpectedCorpusResult(CorpusCase corpusCase) {
        ModerationResult result = SERVICE.moderate(corpusCase.message());

        assertEquals(corpusCase.expectedAction(), result.action());
        if (corpusCase.expectedReason() != null) {
            assertTrue(result.reasons().contains(corpusCase.expectedReason()));
        }
        switch (corpusCase.expectedOutput().kind()) {
            case EXACT -> assertEquals(corpusCase.expectedOutput().value(), result.outputMessage());
            case NULL -> assertNull(result.outputMessage());
            case NOT_ASSERTED -> {
                // Action and optional reason are the intended assertions for this case.
            }
        }
    }

    @Test
    void corpusHasRequiredScaleAndCategories() {
        assertTrue(CORPUS.size() >= 200, "corpus must contain at least 200 reviewed cases");
        assertEquals(
                EnumSet.allOf(CorpusCategory.class),
                CORPUS.stream()
                        .map(CorpusCase::category)
                        .collect(() -> EnumSet.noneOf(CorpusCategory.class), EnumSet::add, EnumSet::addAll)
        );
        assertTrue(
                Arrays.stream(CorpusCategory.values())
                        .allMatch(category -> CORPUS.stream().anyMatch(test -> test.category() == category))
        );
    }

    static Stream<CorpusCase> corpusCases() {
        return CORPUS.stream();
    }
}
