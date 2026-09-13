package dev.chatmoderation.semantic;

/** Explicit result policy when semantic review cannot produce a valid decision. */
public enum ProviderFailurePolicy {
    FAIL_OPEN,
    FAIL_CLOSED,
    DETERMINISTIC_FALLBACK
}
