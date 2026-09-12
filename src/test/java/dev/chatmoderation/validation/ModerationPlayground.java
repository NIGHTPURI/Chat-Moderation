package dev.chatmoderation.validation;

import dev.chatmoderation.core.ChatModerationService;
import dev.chatmoderation.core.DefaultChatModerationService;
import dev.chatmoderation.core.model.ModerationResult;

import java.util.Scanner;

public final class ModerationPlayground {
    private ModerationPlayground() {
    }

    public static void main(String[] args) {
        ChatModerationService service = new DefaultChatModerationService(
                ValidationResources.loadKeywords()
        );

        System.out.println("Moderation Playground (Ctrl-D to exit)");
        try (Scanner scanner = new Scanner(System.in)) {
            while (scanner.hasNextLine()) {
                ModerationResult result = service.moderate(scanner.nextLine());
                System.out.println("action: " + result.action());
                System.out.println("reasons: " + result.reasons());
                System.out.println("outputMessage: " + result.outputMessage());
            }
        }
    }
}
