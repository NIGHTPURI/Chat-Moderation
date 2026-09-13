package dev.chatmoderation.core;

import dev.chatmoderation.semantic.ProviderFailurePolicy;
import dev.chatmoderation.semantic.SemanticModerationProvider;

import java.util.Objects;

/** Public construction entry points for the deterministic and semantic-enabled library. */
public final class ChatModerationServices {
    private ChatModerationServices() {
    }

    public static ChatModerationService deterministic() {
        return new DefaultChatModerationService(
                DefaultModerationResources.keywordsByReason(),
                DefaultModerationResources.exceptionsByKeyword(),
                DefaultModerationResources.aliasesByReason());
    }

    public static ChatModerationService withSemanticProvider(
            SemanticModerationProvider provider,
            ProviderFailurePolicy failurePolicy
    ) {
        return withSemanticProvider(deterministic(), provider, failurePolicy);
    }

    public static ChatModerationService withSemanticProvider(
            ChatModerationService deterministicService,
            SemanticModerationProvider provider,
            ProviderFailurePolicy failurePolicy
    ) {
        return new DefaultSemanticChatModerationService(
                Objects.requireNonNull(deterministicService,
                        "deterministicService must not be null"),
                Objects.requireNonNull(provider, "provider must not be null"),
                Objects.requireNonNull(failurePolicy, "failurePolicy must not be null"));
    }
}
