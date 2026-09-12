package dev.chatmoderation.core;

import dev.chatmoderation.core.model.ModerationResult;

public interface ChatModerationService {
    ModerationResult moderate(String message);
}
