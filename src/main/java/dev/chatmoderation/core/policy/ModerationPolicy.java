package dev.chatmoderation.core.policy;

import dev.chatmoderation.core.model.ModerationResult;
import dev.chatmoderation.core.rule.RuleMatch;

import java.util.List;

public interface ModerationPolicy {
    ModerationResult decide(String originalMessage, List<RuleMatch> matches);
}
