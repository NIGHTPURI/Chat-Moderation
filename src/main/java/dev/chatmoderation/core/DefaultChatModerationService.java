package dev.chatmoderation.core;

import dev.chatmoderation.core.matching.AhoCorasickKeywordMatcher;
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public final class DefaultChatModerationService implements ChatModerationService {
    private final MessageNormalizer normalizer;
    private final KeywordMatcher keywordMatcher;
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
            MessageNormalizer normalizer,
            Collection<String> keywords,
            RuleFilter ruleFilter,
            ModerationPolicy policy
    ) {
        this.normalizer = Objects.requireNonNull(normalizer, "normalizer must not be null");
        this.ruleFilter = Objects.requireNonNull(ruleFilter, "ruleFilter must not be null");
        this.policy = Objects.requireNonNull(policy, "policy must not be null");
        this.keywordMatcher = new AhoCorasickKeywordMatcher(
                normalizeKeywords(keywords, normalizer)
        );
    }

    @Override
    public ModerationResult moderate(String message) {
        Objects.requireNonNull(message, "message must not be null");

        String canonicalMessage = message.strip();
        String normalizedMessage = normalizer.normalize(canonicalMessage);
        List<ModerationReason> keywordFindings = keywordMatcher.findAll(normalizedMessage).isEmpty()
                ? List.of()
                : List.of(ModerationReason.PROFANITY);
        List<RuleMatch> originalMatches = ruleFilter.findAll(canonicalMessage);

        return policy.decide(canonicalMessage, keywordFindings, originalMatches);
    }

    private static List<String> normalizeKeywords(
            Collection<String> keywords,
            MessageNormalizer normalizer
    ) {
        Objects.requireNonNull(keywords, "keywords must not be null");

        List<String> normalizedKeywords = new ArrayList<>(keywords.size());
        for (String keyword : keywords) {
            String normalizedKeyword = normalizer.normalize(
                    Objects.requireNonNull(keyword, "keyword must not be null")
            );
            if (normalizedKeyword.isEmpty()) {
                throw new IllegalArgumentException("normalized keyword must not be empty");
            }
            normalizedKeywords.add(normalizedKeyword);
        }
        return List.copyOf(normalizedKeywords);
    }
}
