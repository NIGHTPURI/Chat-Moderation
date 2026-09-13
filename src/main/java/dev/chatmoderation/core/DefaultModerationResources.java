package dev.chatmoderation.core;

import dev.chatmoderation.core.model.ModerationReason;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class DefaultModerationResources {
    private static final String ROOT = "/dev/chatmoderation/defaults/";

    private DefaultModerationResources() {
    }

    static Map<ModerationReason, Collection<String>> keywordsByReason() {
        Map<ModerationReason, Collection<String>> keywords = new EnumMap<>(ModerationReason.class);
        keywords.put(ModerationReason.PROFANITY, read("profanity-keywords.txt"));
        keywords.put(ModerationReason.SEXUAL_CONTENT, read("sexual-keywords.txt"));
        return Map.copyOf(keywords);
    }

    static Map<String, Collection<String>> exceptionsByKeyword() {
        Map<String, Collection<String>> exceptions = new LinkedHashMap<>();
        for (String line : read("keyword-exceptions.tsv")) {
            String[] columns = line.split("\\t", -1);
            requireColumns(columns, 2, "keyword-exceptions.tsv");
            exceptions.computeIfAbsent(columns[0], ignored -> new ArrayList<>()).add(columns[1]);
        }
        return Map.copyOf(exceptions);
    }

    static Map<ModerationReason, Collection<String>> aliasesByReason() {
        Map<ModerationReason, Collection<String>> aliases = new EnumMap<>(ModerationReason.class);
        for (String line : read("obfuscation-aliases.tsv")) {
            String[] columns = line.split("\\t", -1);
            requireColumns(columns, 2, "obfuscation-aliases.tsv");
            ModerationReason reason = ModerationReason.valueOf(columns[1]);
            aliases.computeIfAbsent(reason, ignored -> new ArrayList<>()).add(columns[0]);
        }
        return Map.copyOf(aliases);
    }

    private static List<String> read(String name) {
        String resource = ROOT + name;
        InputStream stream = DefaultModerationResources.class.getResourceAsStream(resource);
        if (stream == null) {
            throw new IllegalStateException("missing production moderation resource: " + resource);
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            return reader.lines()
                    .filter(line -> !line.isBlank())
                    .filter(line -> !line.startsWith("#"))
                    .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("failed to read production resource: " + resource,
                    exception);
        }
    }

    private static void requireColumns(String[] columns, int count, String resource) {
        if (columns.length != count || columns[0].isBlank() || columns[1].isBlank()) {
            throw new IllegalArgumentException(resource + " contains an invalid entry");
        }
    }
}
