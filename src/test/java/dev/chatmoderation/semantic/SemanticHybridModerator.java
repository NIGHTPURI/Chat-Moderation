package dev.chatmoderation.semantic;

import dev.chatmoderation.core.ChatModerationService;
import dev.chatmoderation.core.model.ModerationAction;
import dev.chatmoderation.core.model.ModerationReason;
import dev.chatmoderation.core.model.ModerationResult;

import java.util.List;
import java.util.Objects;

public final class SemanticHybridModerator {
    private final ChatModerationService deterministicService;
    private final SemanticReviewRouter router;
    private final SemanticModerationProvider provider;
    private final ProviderFailurePolicy failurePolicy;

    public SemanticHybridModerator(
            ChatModerationService deterministicService,
            SemanticReviewRouter router,
            SemanticModerationProvider provider,
            ProviderFailurePolicy failurePolicy
    ) {
        this.deterministicService = Objects.requireNonNull(
                deterministicService,
                "deterministicService must not be null"
        );
        this.router = Objects.requireNonNull(router, "router must not be null");
        this.provider = Objects.requireNonNull(provider, "provider must not be null");
        this.failurePolicy = Objects.requireNonNull(failurePolicy, "failurePolicy must not be null");
    }

    public SemanticHybridResult moderate(String message) {
        ModerationResult deterministicResult = deterministicService.moderate(message);
        SemanticRoute route = router.route(message, deterministicResult);
        if (route.decision() != SemanticRoutingDecision.NEEDS_SEMANTIC_REVIEW) {
            return new SemanticHybridResult(deterministicResult, route.decision(), 0, null);
        }

        long startedAt = System.nanoTime();
        SemanticModerationResult semanticResult;
        try {
            semanticResult = Objects.requireNonNull(
                    provider.moderate(route.semanticMessage()),
                    "semantic provider result must not be null"
            );
        } catch (RuntimeException exception) {
            long latency = System.nanoTime() - startedAt;
            return new SemanticHybridResult(
                    applyFailurePolicy(message, route.deterministicResult()),
                    route.decision(),
                    latency,
                    SemanticModerationResult.Status.ERROR
            );
        }
        long latency = System.nanoTime() - startedAt;
        ModerationResult result = semanticResult.status() == SemanticModerationResult.Status.SUCCESS
                ? applySemanticDecision(message, route.deterministicResult(), semanticResult)
                : applyFailurePolicy(message, route.deterministicResult());
        return new SemanticHybridResult(
                result,
                route.decision(),
                latency,
                semanticResult.status()
        );
    }

    private ModerationResult applySemanticDecision(
            String message,
            ModerationResult deterministicResult,
            SemanticModerationResult semanticResult
    ) {
        if (semanticResult.decision() == SemanticModerationResult.Decision.BLOCK) {
            List<ModerationReason> reasons = semanticResult.reasons().isEmpty()
                    ? List.of(ModerationReason.OTHER)
                    : semanticResult.reasons();
            return ModerationResult.block(reasons);
        }
        if (semanticResult.decision() == SemanticModerationResult.Decision.ALLOW) {
            return deterministicResult.allowed()
                    ? deterministicResult
                    : ModerationResult.allow(message.strip());
        }
        return applyFailurePolicy(message, deterministicResult);
    }

    private ModerationResult applyFailurePolicy(
            String message,
            ModerationResult deterministicResult
    ) {
        return switch (failurePolicy) {
            case FAIL_OPEN -> deterministicResult.action() == ModerationAction.MASK
                    ? deterministicResult
                    : ModerationResult.allow(message.strip());
            case FAIL_CLOSED -> ModerationResult.block(List.of(ModerationReason.OTHER));
            case DETERMINISTIC_FALLBACK -> deterministicResult;
        };
    }
}
