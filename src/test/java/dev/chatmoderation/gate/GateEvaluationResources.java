package dev.chatmoderation.gate;

import dev.chatmoderation.core.model.ModerationAction;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class GateEvaluationResources {
    private static final String CALIBRATION = "/moderation/gate-calibration-templates.tsv";
    private static final String HOLDOUT = "/moderation/gate-holdout-templates.tsv";
    private static final String PRODUCTION = "/moderation/gate-production-templates.tsv";

    private GateEvaluationResources() {
    }

    public static List<GateEvaluationCase> loadCalibration() {
        return expand(CALIBRATION);
    }

    public static List<GateEvaluationCase> loadHoldout() {
        return expand(HOLDOUT);
    }

    public static List<GateEvaluationCase> loadProductionLike() {
        return expand(PRODUCTION);
    }

    private static List<GateEvaluationCase> expand(String resource) {
        InputStream stream = GateEvaluationResources.class.getResourceAsStream(resource);
        if (stream == null) {
            throw new IllegalStateException("missing confidence gate dataset: " + resource);
        }
        List<GateEvaluationCase> cases = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8)
        )) {
            List<String> lines = reader.lines().toList();
            for (int index = 0; index < lines.size(); index++) {
                String line = lines.get(index);
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] columns = line.split("\\t", -1);
                if (columns.length != 4) {
                    throw new IllegalArgumentException(resource + ":" + (index + 1)
                            + " must have 4 columns");
                }
                for (String left : columns[2].split("\\|", -1)) {
                    for (String right : columns[3].split("\\|", -1)) {
                        cases.add(new GateEvaluationCase(
                                GateEvaluationCategory.valueOf(columns[0]),
                                left.strip() + " " + right.strip(),
                                ModerationAction.valueOf(columns[1])
                        ));
                    }
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("failed to read gate dataset: " + resource, exception);
        }
        return List.copyOf(cases);
    }
}
