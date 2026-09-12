package dev.chatmoderation.core.rule;

import dev.chatmoderation.core.model.ModerationReason;

import java.util.regex.Pattern;

public final class PhoneNumberRule extends RegexMessageRule {
    private static final Pattern PHONE_NUMBER = Pattern.compile(
            "(?<!\\d)(?:01[016789]-\\d{3,4}-\\d{4}|01[016789]\\d{7,8})(?!\\d)"
    );

    public PhoneNumberRule() {
        super(PHONE_NUMBER, ModerationReason.PERSONAL_INFORMATION);
    }
}
