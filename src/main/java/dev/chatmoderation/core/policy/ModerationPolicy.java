package dev.chatmoderation.core.policy;

import dev.chatmoderation.core.model.ModerationResult;

public interface ModerationPolicy {
    ModerationResult decide(String originalMessage);
}
