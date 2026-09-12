package dev.chatmoderation.core.normalization;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DefaultMessageNormalizerTest {
    private final MessageNormalizer normalizer = new DefaultMessageNormalizer();

    @Test
    void normalizesUnicodeToNfc() {
        assertEquals("카페", normalizer.normalize("  카페  "));
    }

    @Test
    void lowercasesEnglishUsingLocaleIndependentRules() {
        assertEquals("hello world", normalizer.normalize("  HELLO World  "));
    }

    @Test
    void preservesWhitespaceInsideMessage() {
        assertEquals("씨 발", normalizer.normalize(" 씨 발 "));
    }

    @Test
    void preservesCharactersUsedToEvadeKeywordMatching() {
        assertEquals("시1발", normalizer.normalize("시1발"));
    }

    @Test
    void rejectsNullMessage() {
        assertThrows(NullPointerException.class, () -> normalizer.normalize(null));
    }
}
