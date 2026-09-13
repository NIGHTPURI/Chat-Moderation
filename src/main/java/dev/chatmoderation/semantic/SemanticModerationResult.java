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
        if (!Double.isFinite(confidence) || confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence must be between 0 and 1");
        }
        Objects.requireNonNull(status, "status must not be null");
        if (provider == null || provider.isBlank()) {
            throw new IllegalArgumentException("provider must not be blank");
        }
        if (status == Status.SUCCESS && decision == Decision.UNKNOWN) {
            throw new IllegalArgumentException("successful result must contain a decision");
        }
    }

    public static SemanticModerationResult allow(double confidence, String provider) {
        return new SemanticModerationResult(
                Decision.ALLOW, List.of(), confidence, Status.SUCCESS, provider);
    }

    public static SemanticModerationResult block(
            List<ModerationReason> reasons,
            double confidence,
            String provider
    ) {
        return new SemanticModerationResult(
                Decision.BLOCK, reasons, confidence, Status.SUCCESS, provider);
    }

    public static SemanticModerationResult unavailable(Status status, String provider) {
        if (status == Status.SUCCESS) {
            throw new IllegalArgumentException("unavailable result cannot be successful");
        }
        return new SemanticModerationResult(
                Decision.UNKNOWN, List.of(), 0.0, status, provider);
    }

    public enum Decision {
        ALLOW,
        BLOCK,
        UNKNOWN
    }

    public enum Status {
        SUCCESS,
        ERROR,
        TIMEOUT,
        UNAVAILABLE
    }
}
