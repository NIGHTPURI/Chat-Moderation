package dev.chatmoderation.core.rule;

import java.util.List;

public interface RuleFilter {
    List<RuleMatch> findAll(String message);
}
