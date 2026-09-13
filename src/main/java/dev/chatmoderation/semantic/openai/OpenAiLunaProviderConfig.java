package dev.chatmoderation.semantic.openai;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record OpenAiLunaProviderConfig(
        URI endpoint,
        String apiKey,
        String model,
        Duration timeout
) {
    public static final String API_KEY_ENV = "OPENAI_API_KEY";
    public static final String MODEL_ENV = "OPENAI_SEMANTIC_MODEL";
    public static final String TIMEOUT_ENV = "OPENAI_SEMANTIC_TIMEOUT_MS";
    public static final URI DEFAULT_ENDPOINT = URI.create("https://api.openai.com/v1/responses");
    public static final String DEFAULT_MODEL = "gpt-5.6-luna";
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    public OpenAiLunaProviderConfig {
        Objects.requireNonNull(endpoint, "endpoint must not be null");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("apiKey must not be blank");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("model must not be blank");
        }
        Objects.requireNonNull(timeout, "timeout must not be null");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
    }

    public static OpenAiLunaProviderConfig luna(String apiKey, Duration timeout) {
        return new OpenAiLunaProviderConfig(
                DEFAULT_ENDPOINT, apiKey, DEFAULT_MODEL, timeout);
    }

    public static Optional<OpenAiLunaProviderConfig> fromEnvironment() {
        return fromEnvironment(System.getenv());
    }

    @Override
    public String toString() {
        return "OpenAiLunaProviderConfig[endpoint=" + endpoint
                + ", apiKey=<redacted>, model=" + model + ", timeout=" + timeout + "]";
    }

    static Optional<OpenAiLunaProviderConfig> fromEnvironment(Map<String, String> environment) {
        Objects.requireNonNull(environment, "environment must not be null");
        String apiKey = environment.get(API_KEY_ENV);
        if (apiKey == null || apiKey.isBlank()) {
            return Optional.empty();
        }
        String model = valueOrDefault(environment.get(MODEL_ENV), DEFAULT_MODEL);
        return Optional.of(new OpenAiLunaProviderConfig(
                DEFAULT_ENDPOINT, apiKey, model, parseTimeout(environment.get(TIMEOUT_ENV))));
    }

    private static Duration parseTimeout(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT_TIMEOUT;
        }
        try {
            long millis = Long.parseLong(value);
            if (millis <= 0) {
                throw new IllegalArgumentException(TIMEOUT_ENV + " must be positive");
            }
            return Duration.ofMillis(millis);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(TIMEOUT_ENV + " must be an integer", exception);
        }
    }

    private static String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
