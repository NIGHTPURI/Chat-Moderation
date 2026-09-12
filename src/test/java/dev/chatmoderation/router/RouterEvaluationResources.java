package dev.chatmoderation.router;

import dev.chatmoderation.core.model.ModerationAction;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class RouterEvaluationResources {
    private static final String CALIBRATION =
            "/moderation/router-calibration-templates.tsv";
    private static final String HOLDOUT =
            "/moderation/router-holdout-templates.tsv";

    private RouterEvaluationResources() {
    }

    public static List<RouterEvaluationCase> loadCalibration() {
        return expand(CALIBRATION);
    }

    public static List<RouterEvaluationCase> loadHoldout() {
        return expand(HOLDOUT);
    }

    private static List<RouterEvaluationCase> expand(String resource) {
        InputStream stream = RouterEvaluationResources.class.getResourceAsStream(resource);
        if (stream == null) {
            throw new IllegalStateException("missing router dataset: " + resource);
        }
        List<RouterEvaluationCase> cases = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8)
        )) {
            List<String> lines = reader.lines().toList();
            for (int index = 0; index < lines.size(); index++) {
                String line = lines.get(index);
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                String[] columns = line.split("\t", -1);
                if (columns.length != 4) {
                    throw new IllegalArgumentException(resource + ":" + (index + 1)
                            + " must have 4 columns");
                }
                RouterEvaluationCategory category =
                        RouterEvaluationCategory.valueOf(columns[0]);
                ModerationAction action = ModerationAction.valueOf(columns[1]);
                for (String left : columns[2].split("\\|", -1)) {
                    for (String right : columns[3].split("\\|", -1)) {
                        cases.add(new RouterEvaluationCase(
                                category,
                                left.strip() + " " + right.strip(),
                                action
                        ));
                    }
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("failed to read router dataset: " + resource,
                    exception);
        }
        return List.copyOf(cases);
    }
}
