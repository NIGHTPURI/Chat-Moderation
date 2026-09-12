package dev.chatmoderation.semantic.openai.moderation;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record OpenAiModerationApiConfig(
        URI endpoint,
        String apiKey,
        Duration timeout
) {
    public static final String API_KEY_ENV = "OPENAI_API_KEY";
    public static final String MODEL = "omni-moderation-latest";

    private static final URI MODERATIONS_ENDPOINT =
            URI.create("https://api.openai.com/v1/moderations");
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    public OpenAiModerationApiConfig {
        Objects.requireNonNull(endpoint, "endpoint must not be null");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("apiKey must not be blank");
        }
        Objects.requireNonNull(timeout, "timeout must not be null");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
    }

    public static Optional<OpenAiModerationApiConfig> fromEnvironment() {
        return fromEnvironment(System.getenv());
    }

    static Optional<OpenAiModerationApiConfig> fromEnvironment(Map<String, String> environment) {
        String apiKey = environment.get(API_KEY_ENV);
        if (apiKey == null || apiKey.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new OpenAiModerationApiConfig(
                MODERATIONS_ENDPOINT,
                apiKey,
                DEFAULT_TIMEOUT
        ));
    }
}
