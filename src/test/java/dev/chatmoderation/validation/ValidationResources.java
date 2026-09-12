package dev.chatmoderation.validation;

import dev.chatmoderation.core.model.ModerationAction;
import dev.chatmoderation.core.model.ModerationReason;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

final class ValidationResources {
    private static final String CORPUS_RESOURCE = "/moderation/corpus.tsv";
    private static final String KEYWORDS_RESOURCE = "/moderation/validation-keywords.txt";

    private ValidationResources() {
    }

    static List<String> loadKeywords() {
        return readLines(KEYWORDS_RESOURCE).stream()
                .filter(line -> !line.isBlank())
                .filter(line -> !line.startsWith("#"))
                .toList();
    }

    static List<CorpusCase> loadCorpus() {
        List<CorpusCase> cases = new ArrayList<>();
        List<String> lines = readLines(CORPUS_RESOURCE);
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }

            String[] columns = line.split("\t", -1);
            if (columns.length != 5) {
                throw new IllegalArgumentException(
                        CORPUS_RESOURCE + ":" + (index + 1) + " must have 5 columns"
                );
            }
            cases.add(new CorpusCase(
                    CorpusCategory.valueOf(columns[0]),
                    decode(columns[1]),
                    ModerationAction.valueOf(columns[2]),
                    parseReason(columns[3]),
                    parseExpectedOutput(columns[4])
            ));
        }
        return List.copyOf(cases);
    }

    private static ModerationReason parseReason(String value) {
        return "-".equals(value) ? null : ModerationReason.valueOf(value);
    }

    private static ExpectedOutput parseExpectedOutput(String value) {
        return switch (value) {
            case "-" -> ExpectedOutput.notAsserted();
            case "<NULL>" -> ExpectedOutput.nullOutput();
            case "<EMPTY>" -> ExpectedOutput.exact("");
            default -> ExpectedOutput.exact(decode(value));
        };
    }

    private static String decode(String value) {
        return value.replace("\\t", "\t").replace("\\n", "\n");
    }

    private static List<String> readLines(String resourceName) {
        InputStream stream = ValidationResources.class.getResourceAsStream(resourceName);
        if (stream == null) {
            throw new IllegalStateException("missing test resource: " + resourceName);
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8)
        )) {
            return reader.lines().toList();
        } catch (IOException exception) {
            throw new IllegalStateException("failed to read test resource: " + resourceName, exception);
        }
    }
}
