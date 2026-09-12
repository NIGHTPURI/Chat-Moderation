package dev.chatmoderation.router;

import dev.chatmoderation.core.model.ModerationAction;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouterHoldoutDatasetTest {

    @Test
    void sealedHoldoutIsBalancedUniqueAndCoversEveryCategory() {
        List<RouterEvaluationCase> cases = RouterEvaluationResources.loadHoldout();

        assertEquals(224, cases.size());
        assertEquals(112, count(cases, ModerationAction.BLOCK));
        assertEquals(112, count(cases, ModerationAction.ALLOW));
        assertEquals(EnumSet.allOf(RouterEvaluationCategory.class), categories(cases));
        assertEquals(cases.size(), messages(cases).size());
    }

    @Test
    void sealedHoldoutIsDisjointFromCalibrationAndEveryHistoricalDataset() {
        Set<String> excluded = messages(RouterEvaluationResources.loadCalibration());
        excluded.addAll(messages("/moderation/semantic-evaluation.tsv", 1));
        excluded.addAll(messages("/moderation/policy-calibration.tsv", 2));
        excluded.addAll(messages("/moderation/policy-holdout.tsv", 2));
        Set<String> overlap = messages(RouterEvaluationResources.loadHoldout());
        overlap.retainAll(excluded);

        assertTrue(overlap.isEmpty(), overlap.toString());
    }

    @Test
    void sealedHoldoutContainsNoPersonalInformation() {
        for (RouterEvaluationCase testCase : RouterEvaluationResources.loadHoldout()) {
            assertFalse(testCase.message().matches(
                    ".*\\b01[016789]-?\\d{3,4}-?\\d{4}\\b.*"
            ));
            assertFalse(testCase.message().matches(
                    ".*[\\w.+-]+@[\\w.-]+\\.[A-Za-z]{2,}.*"
            ));
        }
    }

    private long count(List<RouterEvaluationCase> cases, ModerationAction action) {
        return cases.stream().filter(item -> item.expectedAction() == action).count();
    }

    private Set<RouterEvaluationCategory> categories(List<RouterEvaluationCase> cases) {
        Set<RouterEvaluationCategory> result = EnumSet.noneOf(RouterEvaluationCategory.class);
        cases.forEach(item -> result.add(item.category()));
        return result;
    }

    private Set<String> messages(List<RouterEvaluationCase> cases) {
        return new HashSet<>(cases.stream().map(RouterEvaluationCase::message).toList());
    }

    private Set<String> messages(String resource, int messageColumn) {
        InputStream stream = getClass().getResourceAsStream(resource);
        if (stream == null) {
            throw new IllegalStateException("missing dataset: " + resource);
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8)
        )) {
            Set<String> result = new HashSet<>();
            reader.lines().filter(line -> !line.isBlank() && !line.startsWith("#"))
                    .map(line -> line.split("\t", -1)[messageColumn])
                    .forEach(result::add);
            return result;
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
