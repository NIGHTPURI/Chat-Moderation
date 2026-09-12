package dev.chatmoderation.core.normalization;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;

public final class DefaultMessageNormalizer implements MessageNormalizer {

    @Override
    public String normalize(String message) {
        Objects.requireNonNull(message, "message must not be null");

        return Normalizer.normalize(message, Normalizer.Form.NFC)
                .toLowerCase(Locale.ROOT)
                .strip();
    }
}
