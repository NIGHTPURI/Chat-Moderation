package dev.chatmoderation.semantic.openai.moderation;

import java.util.Map;
import java.util.Objects;

public record OpenAiModerationObservation(
        String responseModel,
        boolean flagged,
        Map<String, Boolean> categories,
        Map<String, Double> categoryScores
) {
    public OpenAiModerationObservation {
        Objects.requireNonNull(responseModel, "responseModel must not be null");
        categories = Map.copyOf(Objects.requireNonNull(categories, "categories must not be null"));
        categoryScores = Map.copyOf(Objects.requireNonNull(
                categoryScores,
                "categoryScores must not be null"
        ));
    }
}
