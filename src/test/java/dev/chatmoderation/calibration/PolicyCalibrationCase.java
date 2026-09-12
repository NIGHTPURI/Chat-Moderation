package dev.chatmoderation.calibration;

import dev.chatmoderation.core.model.ModerationAction;

import java.util.Objects;

public record PolicyCalibrationCase(
        PolicyCalibrationCategory category,
        PolicyContext context,
        String message,
        ModerationAction expectedAction
) {
    public PolicyCalibrationCase {
        Objects.requireNonNull(category, "category must not be null");
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(message, "message must not be null");
        Objects.requireNonNull(expectedAction, "expectedAction must not be null");
    }
}
