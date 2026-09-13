package dev.chatmoderation.core;

import dev.chatmoderation.core.model.ModerationAction;
import dev.chatmoderation.core.model.ModerationReason;
import dev.chatmoderation.core.model.ModerationResult;
import dev.chatmoderation.semantic.ProviderFailurePolicy;
import dev.chatmoderation.semantic.SemanticModerationProvider;
import dev.chatmoderation.semantic.SemanticModerationResult;

import java.util.List;
import java.util.Objects;

final class DefaultSemanticChatModerationService implements ChatModerationService {
    private final ChatModerationService deterministicService;
    private final ConfidenceAwareSemanticGate gate;
    private final PersonalInformationSanitizer privacySanitizer;
    private final SemanticModerationProvider provider;
    private final ProviderFailurePolicy failurePolicy;

    DefaultSemanticChatModerationService(
            ChatModerationService deterministicService,
            SemanticModerationProvider provider,
            ProviderFailurePolicy failurePolicy
    ) {
        this.deterministicService = Objects.requireNonNull(
                deterministicService, "deterministicService must not be null");
        this.gate = new ConfidenceAwareSemanticGate();
        this.privacySanitizer = new PersonalInformationSanitizer();
        this.provider = Objects.requireNonNull(provider, "provider must not be null");
        this.failurePolicy = Objects.requireNonNull(failurePolicy, "failurePolicy must not be null");
    }

    @Override
    public ModerationResult moderate(String message) {
        Objects.requireNonNull(message, "message must not be null");
        String canonical = message.strip();
        ModerationResult deterministic = deterministicService.moderate(canonical);
        String sanitized = privacySanitizer.sanitize(canonical);
        ConfidenceAwareSemanticRoute route = route(canonical, sanitized, deterministic);
        if (!route.routed()) {
            return deterministic;
        }

        SemanticModerationResult semantic;
        try {
            semantic = Objects.requireNonNull(
                    provider.moderate(route.semanticMessage()),
                    "semantic provider result must not be null");
        } catch (RuntimeException exception) {
            return applyFailurePolicy(canonical, sanitized, deterministic);
        }
        if (semantic.status() != SemanticModerationResult.Status.SUCCESS
                || semantic.decision() == SemanticModerationResult.Decision.UNKNOWN) {
            return applyFailurePolicy(canonical, sanitized, deterministic);
        }
        return applySemanticDecision(canonical, sanitized, deterministic, semantic);
    }

    private ConfidenceAwareSemanticRoute route(
            String canonical,
            String sanitized,
            ModerationResult deterministic
    ) {
        ConfidenceAwareSemanticRoute route = gate.route(canonical, deterministic);
        if (route.routed()) {
            return new ConfidenceAwareSemanticRoute(
                    ConfidenceGateState.NEEDS_SEMANTIC_REVIEW,
                    deterministic,
                    sanitized);
        }
        if (deterministic.action() != ModerationAction.MASK) {
            return route;
        }

        ConfidenceAwareSemanticRoute sanitizedRoute = gate.route(
                sanitized, ModerationResult.allow(sanitized));
        return sanitizedRoute.routed()
                ? new ConfidenceAwareSemanticRoute(
                        ConfidenceGateState.NEEDS_SEMANTIC_REVIEW,
                        deterministic,
                        sanitized)
                : route;
    }

    private ModerationResult applySemanticDecision(
            String canonical,
            String sanitized,
            ModerationResult deterministic,
            SemanticModerationResult semantic
    ) {
        if (semantic.decision() == SemanticModerationResult.Decision.BLOCK) {
            List<ModerationReason> reasons = semantic.reasons().isEmpty()
                    ? List.of(ModerationReason.OTHER)
                    : semantic.reasons();
            return ModerationResult.block(reasons);
        }
        if (deterministic.action() == ModerationAction.MASK) {
            return deterministic;
        }
        if (!sanitized.equals(canonical)) {
            return ModerationResult.mask(
                    sanitized, List.of(ModerationReason.PERSONAL_INFORMATION));
        }
        if (deterministic.action() == ModerationAction.BLOCK) {
            return ModerationResult.allow(canonical);
        }
        return deterministic;
    }

    private ModerationResult applyFailurePolicy(
            String canonical,
            String sanitized,
            ModerationResult deterministic
    ) {
        return switch (failurePolicy) {
            case FAIL_OPEN -> {
                if (deterministic.action() == ModerationAction.MASK) yield deterministic;
                if (!sanitized.equals(canonical)) {
                    yield ModerationResult.mask(
                            sanitized, List.of(ModerationReason.PERSONAL_INFORMATION));
                }
                yield ModerationResult.allow(canonical);
            }
            case FAIL_CLOSED -> ModerationResult.block(List.of(ModerationReason.OTHER));
            case DETERMINISTIC_FALLBACK -> deterministic;
        };
    }
}
