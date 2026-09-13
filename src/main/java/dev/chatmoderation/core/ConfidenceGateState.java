package dev.chatmoderation.core;

enum ConfidenceGateState {
    CERTAIN_ALLOW,
    CERTAIN_BLOCK,
    CERTAIN_MASK,
    NEEDS_SEMANTIC_REVIEW
}
