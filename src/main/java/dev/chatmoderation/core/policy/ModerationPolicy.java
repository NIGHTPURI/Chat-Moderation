package dev.chatmoderation.core.policy;

import dev.chatmoderation.core.model.ModerationResult;
import dev.chatmoderation.core.model.ModerationReason;
import dev.chatmoderation.core.rule.RuleMatch;

import java.util.List;

public interface ModerationPolicy {
    /**
     * Decides the result from findings without a source range and matches whose ranges
     * belong to the canonical message.
     */
    ModerationResult decide(
            String canonicalMessage,
            List<ModerationReason> reasonOnlyFindings,
            List<RuleMatch> originalMatches
    );

    default ModerationResult decide(String canonicalMessage, List<RuleMatch> originalMatches) {
        return decide(canonicalMessage, List.of(), originalMatches);
    }
}
