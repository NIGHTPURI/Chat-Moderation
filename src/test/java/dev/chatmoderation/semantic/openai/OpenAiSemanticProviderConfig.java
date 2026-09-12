package dev.chatmoderation.semantic.openai;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record OpenAiSemanticProviderConfig(
        URI endpoint,
        String apiKey,
        String model,
        Duration timeout,
        Optional<TokenPricing> pricing
) {
    public static final String API_KEY_ENV = "OPENAI_API_KEY";
    public static final String MODEL_ENV = "OPENAI_SEMANTIC_MODEL";
    public static final String TIMEOUT_ENV = "OPENAI_SEMANTIC_TIMEOUT_MS";
    public static final String INPUT_PRICE_ENV = "OPENAI_SEMANTIC_INPUT_USD_PER_MILLION";
    public static final String CACHED_INPUT_PRICE_ENV =
            "OPENAI_SEMANTIC_CACHED_INPUT_USD_PER_MILLION";
    public static final String OUTPUT_PRICE_ENV = "OPENAI_SEMANTIC_OUTPUT_USD_PER_MILLION";

    private static final URI RESPONSES_ENDPOINT =
            URI.create("https://api.openai.com/v1/responses");
    private static final String DEFAULT_MODEL = "gpt-5.6-luna";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    public OpenAiSemanticProviderConfig {
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
        pricing = Objects.requireNonNull(pricing, "pricing must not be null");
    }

    public static Optional<OpenAiSemanticProviderConfig> fromEnvironment() {
        return fromEnvironment(System.getenv());
    }

    static Optional<OpenAiSemanticProviderConfig> fromEnvironment(Map<String, String> environment) {
        String apiKey = environment.get(API_KEY_ENV);
        if (apiKey == null || apiKey.isBlank()) {
            return Optional.empty();
        }
        String model = valueOrDefault(environment.get(MODEL_ENV), DEFAULT_MODEL);
        Duration timeout = parseTimeout(environment.get(TIMEOUT_ENV));
        Optional<TokenPricing> pricing = parsePricing(environment);
        return Optional.of(new OpenAiSemanticProviderConfig(
                RESPONSES_ENDPOINT,
                apiKey,
                model,
                timeout,
                pricing
        ));
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

    private static Optional<TokenPricing> parsePricing(Map<String, String> environment) {
        String input = environment.get(INPUT_PRICE_ENV);
        String cachedInput = environment.get(CACHED_INPUT_PRICE_ENV);
        String output = environment.get(OUTPUT_PRICE_ENV);
        if (isBlank(input) && isBlank(cachedInput) && isBlank(output)) {
            return Optional.empty();
        }
        if (isBlank(input) || isBlank(cachedInput) || isBlank(output)) {
            throw new IllegalArgumentException("all three semantic token prices must be provided");
        }
        return Optional.of(new TokenPricing(
                parsePrice(INPUT_PRICE_ENV, input),
                parsePrice(CACHED_INPUT_PRICE_ENV, cachedInput),
                parsePrice(OUTPUT_PRICE_ENV, output)
        ));
    }

    private static double parsePrice(String name, String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(name + " must be numeric", exception);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String valueOrDefault(String value, String defaultValue) {
        return isBlank(value) ? defaultValue : value;
    }

    public record TokenPricing(
            double inputUsdPerMillion,
            double cachedInputUsdPerMillion,
            double outputUsdPerMillion
    ) {
        public TokenPricing {
            if (inputUsdPerMillion < 0 || cachedInputUsdPerMillion < 0
                    || outputUsdPerMillion < 0) {
                throw new IllegalArgumentException("token prices must not be negative");
            }
        }

        public double estimateUsd(long inputTokens, long cachedInputTokens, long outputTokens) {
            long uncachedInputTokens = Math.max(0, inputTokens - cachedInputTokens);
            return (uncachedInputTokens * inputUsdPerMillion
                    + cachedInputTokens * cachedInputUsdPerMillion
                    + outputTokens * outputUsdPerMillion) / 1_000_000.0;
        }
    }
}
