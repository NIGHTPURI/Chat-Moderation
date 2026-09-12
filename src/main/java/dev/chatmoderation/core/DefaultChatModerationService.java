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
    private final Set<String> collapsibleKeywordPairs;
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
                Map.of(),
                new DefaultRuleFilter(),
                new DefaultModerationPolicy()
        );
    }

    public DefaultChatModerationService(
            Map<ModerationReason, ? extends Collection<String>> keywordsByReason,
            Map<String, ? extends Collection<String>> exceptionsByKeyword,
            Map<ModerationReason, ? extends Collection<String>> aliasesByReason
    ) {
        this(
                new DefaultMessageNormalizer(),
                keywordsByReason,
                exceptionsByKeyword,
                aliasesByReason,
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
        this(
                normalizer,
                keywordsByReason,
                exceptionsByKeyword,
                Map.of(),
                ruleFilter,
                policy
        );
    }

    public DefaultChatModerationService(
            MessageNormalizer normalizer,
            Map<ModerationReason, ? extends Collection<String>> keywordsByReason,
            Map<String, ? extends Collection<String>> exceptionsByKeyword,
            Map<ModerationReason, ? extends Collection<String>> aliasesByReason,
            RuleFilter ruleFilter,
            ModerationPolicy policy
    ) {
        this.normalizer = Objects.requireNonNull(normalizer, "normalizer must not be null");
        this.ruleFilter = Objects.requireNonNull(ruleFilter, "ruleFilter must not be null");
        this.policy = Objects.requireNonNull(policy, "policy must not be null");
        KeywordConfiguration configuration = createKeywordConfiguration(
                keywordsByReason,
                exceptionsByKeyword,
                aliasesByReason,
                normalizer
        );
        this.keywordMatcher = configuration.matcher();
        this.reasonsByKeyword = configuration.reasonsByKeyword();
        this.invalidatedKeywordsByException = configuration.invalidatedKeywordsByException();
        this.collapsibleKeywordPairs = configuration.collapsibleKeywordPairs();
    }

    @Override
    public ModerationResult moderate(String message) {
        Objects.requireNonNull(message, "message must not be null");

        String canonicalMessage = message.strip();
        String normalizedMessage = normalizer.normalize(canonicalMessage);
        Set<ModerationReason> keywordFindings = new LinkedHashSet<>(
                findKeywordReasons(normalizedMessage)
        );
        String separatorCollapsedView = createSeparatorCollapsedView(normalizedMessage);
        if (!separatorCollapsedView.equals(normalizedMessage)) {
            keywordFindings.addAll(findKeywordReasons(separatorCollapsedView));
        }
        List<RuleMatch> originalMatches = ruleFilter.findAll(canonicalMessage);

        return policy.decide(canonicalMessage, List.copyOf(keywordFindings), originalMatches);
    }

    private List<ModerationReason> findKeywordReasons(String detectionView) {
        List<KeywordMatch> keywordMatches = keywordMatcher.findAll(detectionView);
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
        return List.copyOf(keywordFindings);
    }

    private static KeywordConfiguration createKeywordConfiguration(
            Map<ModerationReason, ? extends Collection<String>> keywordsByReason,
            Map<String, ? extends Collection<String>> exceptionsByKeyword,
            Map<ModerationReason, ? extends Collection<String>> aliasesByReason,
            MessageNormalizer normalizer
    ) {
        Objects.requireNonNull(keywordsByReason, "keywordsByReason must not be null");
        Objects.requireNonNull(exceptionsByKeyword, "exceptionsByKeyword must not be null");
        Objects.requireNonNull(aliasesByReason, "aliasesByReason must not be null");

        Map<ModerationReason, Collection<String>> checkedKeywords = checkCategories(
                keywordsByReason,
                "keywords"
        );
        Map<ModerationReason, Collection<String>> checkedAliases = checkCategories(
                aliasesByReason,
                "aliases"
        );
        Map<String, LinkedHashSet<ModerationReason>> mutableReasons = new LinkedHashMap<>();
        Set<String> collapsibleKeywordPairs = new LinkedHashSet<>();
        addCategorizedPatterns(
                checkedKeywords,
                normalizer,
                "keyword",
                mutableReasons,
                collapsibleKeywordPairs
        );
        addCategorizedPatterns(
                checkedAliases,
                normalizer,
                "alias",
                mutableReasons,
                null
        );

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
                Map.copyOf(invalidatedKeywordsByException),
                Set.copyOf(collapsibleKeywordPairs)
        );
    }

    private static Map<ModerationReason, Collection<String>> checkCategories(
            Map<ModerationReason, ? extends Collection<String>> categorizedPatterns,
            String patternName
    ) {
        Map<ModerationReason, Collection<String>> checked = new EnumMap<>(
                ModerationReason.class
        );
        categorizedPatterns.forEach((reason, patterns) -> checked.put(
                Objects.requireNonNull(reason, patternName + " reason must not be null"),
                Objects.requireNonNull(patterns, patternName + " must not be null")
        ));
        return checked;
    }

    private static void addCategorizedPatterns(
            Map<ModerationReason, Collection<String>> categorizedPatterns,
            MessageNormalizer normalizer,
            String patternName,
            Map<String, LinkedHashSet<ModerationReason>> reasonsByPattern,
            Set<String> collapsiblePairs
    ) {
        for (ModerationReason reason : ModerationReason.values()) {
            Collection<String> patterns = categorizedPatterns.get(reason);
            if (patterns == null) {
                continue;
            }
            for (String pattern : patterns) {
                String normalizedPattern = normalizeDictionaryEntry(
                        pattern,
                        normalizer,
                        patternName
                );
                reasonsByPattern.computeIfAbsent(
                        normalizedPattern,
                        ignored -> new LinkedHashSet<>()
                ).add(reason);
                if (collapsiblePairs != null) {
                    addCollapsiblePairs(normalizedPattern, collapsiblePairs);
                }
            }
        }
    }

    private static void addCollapsiblePairs(String keyword, Set<String> collapsiblePairs) {
        int[] codePoints = keyword.codePoints().toArray();
        for (int index = 1; index < codePoints.length; index++) {
            if (isHangulSyllable(codePoints[index - 1]) && isHangulSyllable(codePoints[index])) {
                collapsiblePairs.add(codePointPair(codePoints[index - 1], codePoints[index]));
            }
        }
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

    private String createSeparatorCollapsedView(String normalizedMessage) {
        StringBuilder view = new StringBuilder(normalizedMessage.length());
        int index = 0;
        while (index < normalizedMessage.length()) {
            int codePoint = normalizedMessage.codePointAt(index);
            if (!isSupportedSeparator(codePoint)) {
                view.appendCodePoint(codePoint);
                index += Character.charCount(codePoint);
                continue;
            }

            int separatorStart = index;
            while (index < normalizedMessage.length()) {
                int separator = normalizedMessage.codePointAt(index);
                if (!isSupportedSeparator(separator)) {
                    break;
                }
                index += Character.charCount(separator);
            }
            int left = view.isEmpty() ? -1 : view.codePointBefore(view.length());
            int right = index < normalizedMessage.length()
                    ? normalizedMessage.codePointAt(index)
                    : -1;
            if (!collapsibleKeywordPairs.contains(codePointPair(left, right))) {
                view.append(normalizedMessage, separatorStart, index);
            }
        }
        return view.toString();
    }

    private static boolean isSupportedSeparator(int codePoint) {
        return Character.isWhitespace(codePoint)
                || codePoint >= '0' && codePoint <= '9'
                || codePoint == '.'
                || codePoint == '-'
                || codePoint == '_'
                || codePoint == '*'
                || codePoint == '/';
    }

    private static boolean isHangulSyllable(int codePoint) {
        return codePoint >= 0xAC00 && codePoint <= 0xD7A3;
    }

    private static String codePointPair(int left, int right) {
        if (left < 0 || right < 0) {
            return "";
        }
        return new String(new int[]{left, right}, 0, 2);
    }

    private record KeywordConfiguration(
            KeywordMatcher matcher,
            Map<String, List<ModerationReason>> reasonsByKeyword,
            Map<String, Set<String>> invalidatedKeywordsByException,
            Set<String> collapsibleKeywordPairs
    ) {
    }
}
