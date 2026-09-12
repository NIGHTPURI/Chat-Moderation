package dev.chatmoderation.semantic;

import dev.chatmoderation.core.model.ModerationReason;

import java.util.List;
import java.util.Objects;

public record SemanticModerationResult(
        Decision decision,
        List<ModerationReason> reasons,
        double confidence,
        Status status,
        String provider
) {
    public SemanticModerationResult {
        Objects.requireNonNull(decision, "decision must not be null");
        reasons = List.copyOf(Objects.requireNonNull(reasons, "reasons must not be null"));
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence must be between 0 and 1");
        }
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(provider, "provider must not be null");
    }

    public enum Decision {
        ALLOW,
        BLOCK,
        UNKNOWN
    }

    public enum Status {
        SUCCESS,
        ERROR,
        TIMEOUT
    }
}
