package dev.chatmoderation.semantic;

/** Supplies a semantic moderation decision for an already privacy-sanitized message. */
@FunctionalInterface
public interface SemanticModerationProvider {
    SemanticModerationResult moderate(String message);
}
