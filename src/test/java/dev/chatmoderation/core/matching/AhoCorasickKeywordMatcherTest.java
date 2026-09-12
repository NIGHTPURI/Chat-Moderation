package dev.chatmoderation.core.matching;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AhoCorasickKeywordMatcherTest {

    @Test
    void findsSinglePattern() {
        KeywordMatcher matcher = new AhoCorasickKeywordMatcher(List.of("bad"));

        assertEquals(List.of(new KeywordMatch("bad", 1, 4)), matcher.findAll("xbad"));
    }

    @Test
    void findsSeveralPatterns() {
        KeywordMatcher matcher = new AhoCorasickKeywordMatcher(
                List.of("he", "she", "his", "hers")
        );

        assertEquals(
                List.of(
                        new KeywordMatch("she", 1, 4),
                        new KeywordMatch("he", 2, 4),
                        new KeywordMatch("hers", 2, 6)
                ),
                matcher.findAll("ushers")
        );
    }

    @Test
    void findsKoreanPattern() {
        KeywordMatcher matcher = new AhoCorasickKeywordMatcher(List.of("금칙어", "욕설"));

        assertEquals(
                List.of(
                        new KeywordMatch("욕설", 6, 8),
                        new KeywordMatch("금칙어", 11, 14)
                ),
                matcher.findAll("이 문장에 욕설 및 금칙어 포함")
        );
    }

    @Test
    void findsSameKeywordAtSeveralPositions() {
        KeywordMatcher matcher = new AhoCorasickKeywordMatcher(List.of("bad"));

        assertEquals(
                List.of(
                        new KeywordMatch("bad", 0, 3),
                        new KeywordMatch("bad", 7, 10)
                ),
                matcher.findAll("bad xx bad")
        );
    }

    @Test
    void findsOverlappingPatterns() {
        KeywordMatcher matcher = new AhoCorasickKeywordMatcher(List.of("aba", "bab"));

        assertEquals(
                List.of(
                        new KeywordMatch("aba", 0, 3),
                        new KeywordMatch("bab", 1, 4),
                        new KeywordMatch("aba", 2, 5)
                ),
                matcher.findAll("ababa")
        );
    }

    @Test
    void findsPatternsWithPrefixRelationship() {
        KeywordMatcher matcher = new AhoCorasickKeywordMatcher(List.of("he", "her", "hers"));

        assertEquals(
                List.of(
                        new KeywordMatch("he", 0, 2),
                        new KeywordMatch("her", 0, 3),
                        new KeywordMatch("hers", 0, 4)
                ),
                matcher.findAll("hers")
        );
    }

    @Test
    void returnsEmptyListWhenNoPatternMatches() {
        KeywordMatcher matcher = new AhoCorasickKeywordMatcher(List.of("bad"));

        assertTrue(matcher.findAll("a normal message").isEmpty());
    }

    @Test
    void returnsEmptyListForEmptyMessage() {
        KeywordMatcher matcher = new AhoCorasickKeywordMatcher(List.of("bad"));

        assertTrue(matcher.findAll("").isEmpty());
    }

    @Test
    void matcherWithNoKeywordsNeverMatches() {
        KeywordMatcher matcher = new AhoCorasickKeywordMatcher(List.of());

        assertTrue(matcher.findAll("any message").isEmpty());
    }

    @Test
    void duplicateKeywordInputProducesOneMatch() {
        KeywordMatcher matcher = new AhoCorasickKeywordMatcher(List.of("bad", "bad", "bad"));

        assertEquals(List.of(new KeywordMatch("bad", 0, 3)), matcher.findAll("bad"));
    }

    @Test
    void findsPatternInLongMessage() {
        KeywordMatcher matcher = new AhoCorasickKeywordMatcher(List.of("needle"));
        String message = "a".repeat(100_000) + "needle";

        assertEquals(
                List.of(new KeywordMatch("needle", 100_000, 100_006)),
                matcher.findAll(message)
        );
    }

    @Test
    void reusesSameMatcherAcrossMessages() {
        KeywordMatcher matcher = new AhoCorasickKeywordMatcher(List.of("bad", "욕설"));

        assertEquals(List.of(new KeywordMatch("bad", 0, 3)), matcher.findAll("bad day"));
        assertEquals(List.of(new KeywordMatch("욕설", 0, 2)), matcher.findAll("욕설 없음"));
        assertTrue(matcher.findAll("clean").isEmpty());
    }

    @Test
    void rejectsEmptyKeywordBecauseItHasNoFiniteMatchPosition() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new AhoCorasickKeywordMatcher(List.of(""))
        );
    }

    @Test
    void rejectsNullInputs() {
        assertThrows(NullPointerException.class, () -> new AhoCorasickKeywordMatcher(null));
        assertThrows(
                NullPointerException.class,
                () -> new AhoCorasickKeywordMatcher(java.util.Arrays.asList("bad", null))
        );

        KeywordMatcher matcher = new AhoCorasickKeywordMatcher(List.of("bad"));
        assertThrows(NullPointerException.class, () -> matcher.findAll(null));
    }
}
