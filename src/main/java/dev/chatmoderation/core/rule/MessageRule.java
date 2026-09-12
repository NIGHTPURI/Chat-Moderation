package dev.chatmoderation.core.rule;

import java.util.List;

public interface MessageRule {
    List<RuleMatch> findAll(String message);
}
