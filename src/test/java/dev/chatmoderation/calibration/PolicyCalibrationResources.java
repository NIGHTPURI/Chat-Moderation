package dev.chatmoderation.calibration;

import dev.chatmoderation.core.model.ModerationAction;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class PolicyCalibrationResources {
    private static final String CALIBRATION = "/moderation/policy-calibration.tsv";
    private static final String HOLDOUT = "/moderation/policy-holdout.tsv";

    private PolicyCalibrationResources() {
    }

    public static List<PolicyCalibrationCase> loadCalibration() {
        return load(CALIBRATION);
    }

    public static List<PolicyCalibrationCase> loadHoldout() {
        return load(HOLDOUT);
    }

    private static List<PolicyCalibrationCase> load(String resource) {
        InputStream stream = PolicyCalibrationResources.class.getResourceAsStream(resource);
        if (stream == null) {
            throw new IllegalStateException("missing test resource: " + resource);
        }
        List<PolicyCalibrationCase> cases = new ArrayList<>();
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
                cases.add(new PolicyCalibrationCase(
                        PolicyCalibrationCategory.valueOf(columns[0]),
                        PolicyContext.valueOf(columns[1]),
                        columns[2].replace("\\t", "\t").replace("\\n", "\n"),
                        ModerationAction.valueOf(columns[3])
                ));
            }
        } catch (IOException exception) {
            throw new IllegalStateException("failed to read test resource: " + resource, exception);
        }
        return List.copyOf(cases);
    }
}
