package dev.chatmoderation.semantic;

public record HighRecallSemanticRoute(
        HighRecallRoutingDecision decision,
        String semanticMessage
) {
    public boolean routedToSemanticProvider() {
        return decision == HighRecallRoutingDecision.NEEDS_SEMANTIC_REVIEW;
    }
}
