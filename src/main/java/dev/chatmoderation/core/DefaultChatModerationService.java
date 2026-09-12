package dev.chatmoderation.core;

import dev.chatmoderation.core.matching.AhoCorasickKeywordMatcher;
import dev.chatmoderation.core.matching.KeywordMatch;
import dev.chatmoderation.core.matching.KeywordMatcher;
import dev.chatmoderation.core.model.ModerationReason;
import dev.chatmoderation.core.model.ModerationResult;
import dev.chatmoderation.core.normalization.DefaultMessageNormalizer;
import dev.chatmoderation.core.normalization.MessageNormalizer;
import dev.chatmoderation.core.policy.DefaultModerationPolicy;
import dev.chatmoderation.core.policy.ModerationPolicy;
import dev.chatmoderation.core.rule.DefaultRuleFilter;
import dev.chatmoderation.core.rule.RuleFilter;
import dev.chatmoderation.core.rule.RuleMatch;

import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class DefaultChatModerationService implements ChatModerationService {
    private final MessageNormalizer normalizer;
    private final KeywordMatcher keywordMatcher;
    private final Map<String, List<ModerationReason>> reasonsByKeyword;
    private final Map<String, Set<String>> invalidatedKeywordsByException;
    private final RuleFilter ruleFilter;
    private final ModerationPolicy policy;

    public DefaultChatModerationService(Collection<String> keywords) {
        this(
                new DefaultMessageNormalizer(),
                keywords,
                new DefaultRuleFilter(),
                new DefaultModerationPolicy()
        );
    }

    public DefaultChatModerationService(
            Map<ModerationReason, ? extends Collection<String>> keywordsByReason,
            Map<String, ? extends Collection<String>> exceptionsByKeyword
    ) {
        this(
                new DefaultMessageNormalizer(),
                keywordsByReason,
                exceptionsByKeyword,
                new DefaultRuleFilter(),
                new DefaultModerationPolicy()
        );
    }

    public DefaultChatModerationService(
            MessageNormalizer normalizer,
            Collection<String> keywords,
            RuleFilter ruleFilter,
            ModerationPolicy policy
    ) {
        this(
                normalizer,
                Map.of(ModerationReason.PROFANITY, keywords),
                Map.of(),
                ruleFilter,
                policy
        );
    }

    public DefaultChatModerationService(
            MessageNormalizer normalizer,
            Map<ModerationReason, ? extends Collection<String>> keywordsByReason,
            Map<String, ? extends Collection<String>> exceptionsByKeyword,
            RuleFilter ruleFilter,
            ModerationPolicy policy
    ) {
        this.normalizer = Objects.requireNonNull(normalizer, "normalizer must not be null");
        this.ruleFilter = Objects.requireNonNull(ruleFilter, "ruleFilter must not be null");
        this.policy = Objects.requireNonNull(policy, "policy must not be null");
        KeywordConfiguration configuration = createKeywordConfiguration(
                keywordsByReason,
                exceptionsByKeyword,
                normalizer
        );
        this.keywordMatcher = configuration.matcher();
        this.reasonsByKeyword = configuration.reasonsByKeyword();
        this.invalidatedKeywordsByException = configuration.invalidatedKeywordsByException();
    }

    @Override
    public ModerationResult moderate(String message) {
        Objects.requireNonNull(message, "message must not be null");

        String canonicalMessage = message.strip();
        String normalizedMessage = normalizer.normalize(canonicalMessage);
        List<KeywordMatch> keywordMatches = keywordMatcher.findAll(normalizedMessage);
        List<KeywordMatch> exceptionMatches = keywordMatches.stream()
                .filter(match -> invalidatedKeywordsByException.containsKey(match.keyword()))
                .toList();
        Set<ModerationReason> keywordFindings = new LinkedHashSet<>();
        for (KeywordMatch keywordMatch : keywordMatches) {
            List<ModerationReason> reasons = reasonsByKeyword.get(keywordMatch.keyword());
            if (reasons != null && !isCoveredByException(keywordMatch, exceptionMatches)) {
                keywordFindings.addAll(reasons);
            }
        }
        List<RuleMatch> originalMatches = ruleFilter.findAll(canonicalMessage);

        return policy.decide(canonicalMessage, List.copyOf(keywordFindings), originalMatches);
    }

    private static KeywordConfiguration createKeywordConfiguration(
            Map<ModerationReason, ? extends Collection<String>> keywordsByReason,
            Map<String, ? extends Collection<String>> exceptionsByKeyword,
            MessageNormalizer normalizer
    ) {
        Objects.requireNonNull(keywordsByReason, "keywordsByReason must not be null");
        Objects.requireNonNull(exceptionsByKeyword, "exceptionsByKeyword must not be null");

        Map<ModerationReason, Collection<String>> checkedKeywords = new EnumMap<>(
                ModerationReason.class
        );
        keywordsByReason.forEach((reason, keywords) -> checkedKeywords.put(
                Objects.requireNonNull(reason, "keyword reason must not be null"),
                Objects.requireNonNull(keywords, "keywords must not be null")
        ));

        Map<String, LinkedHashSet<ModerationReason>> mutableReasons = new LinkedHashMap<>();
        for (ModerationReason reason : ModerationReason.values()) {
            Collection<String> keywords = checkedKeywords.get(reason);
            if (keywords == null) {
                continue;
            }
            for (String keyword : keywords) {
                String normalizedKeyword = normalizeDictionaryEntry(keyword, normalizer, "keyword");
                mutableReasons.computeIfAbsent(
                        normalizedKeyword,
                        ignored -> new LinkedHashSet<>()
                ).add(reason);
            }
        }

        Map<String, List<ModerationReason>> reasonsByKeyword = new LinkedHashMap<>();
        mutableReasons.forEach((keyword, reasons) -> reasonsByKeyword.put(
                keyword,
                List.copyOf(reasons)
        ));

        Map<String, LinkedHashSet<String>> mutableInvalidatedKeywords = new LinkedHashMap<>();
        exceptionsByKeyword.forEach((keyword, exceptions) -> {
            String normalizedKeyword = normalizeDictionaryEntry(keyword, normalizer, "keyword");
            if (!reasonsByKeyword.containsKey(normalizedKeyword)) {
                throw new IllegalArgumentException(
                        "keyword exception target is not a blocking keyword: " + normalizedKeyword
                );
            }
            Objects.requireNonNull(exceptions, "keyword exceptions must not be null");
            for (String exception : exceptions) {
                String normalizedException = normalizeDictionaryEntry(
                        exception,
                        normalizer,
                        "keyword exception"
                );
                if (normalizedKeyword.equals(normalizedException)) {
                    throw new IllegalArgumentException(
                            "keyword exception must not equal its blocking keyword: "
                                    + normalizedKeyword
                    );
                }
                mutableInvalidatedKeywords.computeIfAbsent(
                        normalizedException,
                        ignored -> new LinkedHashSet<>()
                ).add(normalizedKeyword);
            }
        });
        Map<String, Set<String>> invalidatedKeywordsByException = new LinkedHashMap<>();
        mutableInvalidatedKeywords.forEach((exception, keywords) ->
                invalidatedKeywordsByException.put(exception, Set.copyOf(keywords))
        );

        Set<String> allPatterns = new LinkedHashSet<>(reasonsByKeyword.keySet());
        allPatterns.addAll(invalidatedKeywordsByException.keySet());
        return new KeywordConfiguration(
                new AhoCorasickKeywordMatcher(allPatterns),
                Map.copyOf(reasonsByKeyword),
                Map.copyOf(invalidatedKeywordsByException)
        );
    }

    private static String normalizeDictionaryEntry(
            String entry,
            MessageNormalizer normalizer,
            String entryName
    ) {
        String normalizedEntry = normalizer.normalize(
                Objects.requireNonNull(entry, entryName + " must not be null")
        );
        if (normalizedEntry.isEmpty()) {
            throw new IllegalArgumentException("normalized " + entryName + " must not be empty");
        }
        return normalizedEntry;
    }

    private boolean isCoveredByException(
            KeywordMatch keywordMatch,
            List<KeywordMatch> exceptionMatches
    ) {
        for (KeywordMatch exceptionMatch : exceptionMatches) {
            if (invalidatedKeywordsByException.get(exceptionMatch.keyword())
                    .contains(keywordMatch.keyword())
                    && exceptionMatch.startIndex() <= keywordMatch.startIndex()
                    && exceptionMatch.endIndex() >= keywordMatch.endIndex()) {
                return true;
            }
        }
        return false;
    }

    private record KeywordConfiguration(
            KeywordMatcher matcher,
            Map<String, List<ModerationReason>> reasonsByKeyword,
            Map<String, Set<String>> invalidatedKeywordsByException
    ) {
    }
}
