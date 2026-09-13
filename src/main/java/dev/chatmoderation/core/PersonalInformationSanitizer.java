package dev.chatmoderation.core;

import dev.chatmoderation.core.rule.EmailRule;
import dev.chatmoderation.core.rule.MessageRule;
import dev.chatmoderation.core.rule.PhoneNumberRule;
import dev.chatmoderation.core.rule.RuleMatch;

import java.util.List;

final class PersonalInformationSanitizer {
    private final List<MessageRule> rules = List.of(new PhoneNumberRule(), new EmailRule());

    String sanitize(String message) {
        char[] sanitized = message.toCharArray();
        for (MessageRule rule : rules) {
            for (RuleMatch match : rule.findAll(message)) {
                for (int index = match.startIndex(); index < match.endIndex(); index++) {
                    sanitized[index] = '*';
                }
            }
        }
        return new String(sanitized);
    }
}
