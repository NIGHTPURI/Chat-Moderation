package dev.chatmoderation.semantic;

public interface SemanticModerationProvider {
    SemanticModerationResult moderate(String message);
}
