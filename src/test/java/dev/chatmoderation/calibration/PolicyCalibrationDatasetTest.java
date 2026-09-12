package dev.chatmoderation.calibration;

import dev.chatmoderation.core.model.ModerationAction;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PolicyCalibrationDatasetTest {

    @Test
    void calibrationHasTwoHundredIndependentBalancedCases() {
        List<PolicyCalibrationCase> cases = PolicyCalibrationResources.loadCalibration();

        assertEquals(220, cases.size());
        assertEquals(EnumSet.allOf(PolicyCalibrationCategory.class), categories(cases));
        assertEquals(100, countAction(cases, ModerationAction.BLOCK));
        assertEquals(120, countAction(cases, ModerationAction.ALLOW));
        assertEquals(cases.size(), messages(cases).size());
    }

    @Test
    void sealedHoldoutHasAtLeastOneHundredFiftyUniqueCases() {
        List<PolicyCalibrationCase> cases = PolicyCalibrationResources.loadHoldout();

        assertEquals(165, cases.size());
        assertEquals(EnumSet.allOf(PolicyCalibrationCategory.class), categories(cases));
        assertEquals(75, countAction(cases, ModerationAction.BLOCK));
        assertEquals(90, countAction(cases, ModerationAction.ALLOW));
        assertEquals(cases.size(), messages(cases).size());
    }

    @Test
    void calibrationAndHoldoutDoNotShareMessages() {
        Set<String> calibration = messages(PolicyCalibrationResources.loadCalibration());
        Set<String> holdout = messages(PolicyCalibrationResources.loadHoldout());

        assertTrue(java.util.Collections.disjoint(calibration, holdout));
    }

    @Test
    void newDatasetsDoNotCopyHistoricalSemanticMessages() {
        Set<String> historical = historicalSemanticMessages();

        assertTrue(java.util.Collections.disjoint(
                messages(PolicyCalibrationResources.loadCalibration()),
                historical
        ));
        assertTrue(java.util.Collections.disjoint(
                messages(PolicyCalibrationResources.loadHoldout()),
                historical
        ));
    }

    @Test
    void policyContextAgreesWithExpectedActionAndContainsNoPersonalInformation() {
        List<PolicyCalibrationCase> cases = new java.util.ArrayList<>();
        cases.addAll(PolicyCalibrationResources.loadCalibration());
        cases.addAll(PolicyCalibrationResources.loadHoldout());

        for (PolicyCalibrationCase testCase : cases) {
            ModerationAction expected = testCase.context() == PolicyContext.DIRECT_ABUSE
                    ? ModerationAction.BLOCK : ModerationAction.ALLOW;
            assertEquals(expected, testCase.expectedAction(), testCase.message());
            assertFalse(testCase.message().matches(".*\\b01[016789]-?\\d{3,4}-?\\d{4}\\b.*"));
            assertFalse(testCase.message().matches(".*[\\w.+-]+@[\\w.-]+\\.[A-Za-z]{2,}.*"));
        }
    }

    private Set<PolicyCalibrationCategory> categories(List<PolicyCalibrationCase> cases) {
        Set<PolicyCalibrationCategory> categories = EnumSet.noneOf(
                PolicyCalibrationCategory.class
        );
        cases.forEach(testCase -> categories.add(testCase.category()));
        return categories;
    }

    private long countAction(List<PolicyCalibrationCase> cases, ModerationAction action) {
        return cases.stream().filter(testCase -> testCase.expectedAction() == action).count();
    }

    private Set<String> messages(List<PolicyCalibrationCase> cases) {
        return new HashSet<>(cases.stream().map(PolicyCalibrationCase::message).toList());
    }

    private Set<String> historicalSemanticMessages() {
        var stream = getClass().getResourceAsStream("/moderation/semantic-evaluation.tsv");
        if (stream == null) {
            throw new IllegalStateException("missing historical semantic dataset");
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8)
        )) {
            Set<String> messages = new HashSet<>();
            reader.lines()
                    .filter(line -> !line.isBlank() && !line.startsWith("#"))
                    .map(line -> line.split("\t", -1)[1])
                    .forEach(messages::add);
            return messages;
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("failed to read historical semantic dataset", exception);
        }
    }
}
