package dev.chatmoderation.core.model;

import java.util.List;

public record ModerationResult(
        boolean allowed,
        ModerationAction action,
        List<ModerationReason> reasons,
        String outputMessage
) {
    public ModerationResult {
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
    }

    public static ModerationResult allow(String message) {
        return new ModerationResult(
                true,
                ModerationAction.ALLOW,
                List.of(),
                message
        );
    }

    public static ModerationResult block(List<ModerationReason> reasons) {
        return new ModerationResult(
                false,
                ModerationAction.BLOCK,
                reasons,
                null
        );
    }

    public static ModerationResult mask(String message, List<ModerationReason> reasons) {
        return new ModerationResult(
                true,
                ModerationAction.MASK,
                reasons,
                message
        );
    }
}
