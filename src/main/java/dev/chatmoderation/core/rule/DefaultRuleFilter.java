package dev.chatmoderation.core.rule;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class DefaultRuleFilter implements RuleFilter {
    private final List<MessageRule> rules;

    public DefaultRuleFilter() {
        this(List.of(
                new PhoneNumberRule(),
                new EmailRule(),
                new UrlRule(),
                new RepeatedCharacterRule()
        ));
    }

    public DefaultRuleFilter(List<MessageRule> rules) {
        Objects.requireNonNull(rules, "rules must not be null");
        this.rules = List.copyOf(rules);
    }

    @Override
    public List<RuleMatch> findAll(String message) {
        Objects.requireNonNull(message, "message must not be null");

        List<RuleMatch> matches = new ArrayList<>();
        for (MessageRule rule : rules) {
            matches.addAll(rule.findAll(message));
        }
        matches.sort(Comparator
                .comparingInt(RuleMatch::startIndex)
                .thenComparingInt(RuleMatch::endIndex));
        return List.copyOf(matches);
    }
}
