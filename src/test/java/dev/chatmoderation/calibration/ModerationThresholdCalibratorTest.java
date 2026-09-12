package dev.chatmoderation.calibration;

import dev.chatmoderation.core.model.ModerationAction;
import dev.chatmoderation.semantic.openai.moderation.OpenAiModerationObservation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModerationThresholdCalibratorTest {

    @Test
    void choosesHighestRecallThresholdPairWithinFprConstraint() {
        List<ModerationThresholdCalibrator.Sample> samples = List.of(
                sample("direct insult", ModerationAction.BLOCK, 0.80, 0.01),
                sample("subtle insult", ModerationAction.BLOCK, 0.30, 0.01),
                sample("sexual request", ModerationAction.BLOCK, 0.01, 0.70),
                sample("benign one", ModerationAction.ALLOW, 0.20, 0.02),
                sample("benign two", ModerationAction.ALLOW, 0.10, 0.10),
                sample("benign three", ModerationAction.ALLOW, 0.05, 0.20)
        );

        ModerationThresholdCalibrator.Selection selected =
                new ModerationThresholdCalibrator(0.0).select(samples);

        assertEquals(0.30, selected.thresholds().abuse());
        assertEquals(0.70, selected.thresholds().sexual());
        assertEquals(1.0, selected.metrics().recall());
        assertEquals(0.0, selected.metrics().falsePositiveRate());
    }

    @Test
    void scorePolicyUsesProviderCategoryPrefixesAndIgnoresUnrelatedScores() {
        ModerationScorePolicy policy = new ModerationScorePolicy(
                new ModerationScoreThresholds(0.4, 0.6)
        );

        assertTrue(policy.blocks(observation(Map.of("harassment/threatening", 0.5))));
        assertTrue(policy.blocks(observation(Map.of("sexual", 0.7))));
        assertFalse(policy.blocks(observation(Map.of("violence", 0.99, "hate", 0.1))));
    }

    private ModerationThresholdCalibrator.Sample sample(
            String message,
            ModerationAction action,
            double harassment,
            double sexual
    ) {
        PolicyCalibrationCase testCase = new PolicyCalibrationCase(
                action == ModerationAction.BLOCK
                        ? PolicyCalibrationCategory.DIRECT_INSULT
                        : PolicyCalibrationCategory.EDUCATIONAL,
                action == ModerationAction.BLOCK
                        ? PolicyContext.DIRECT_ABUSE : PolicyContext.MENTION_OR_REPORT,
                message,
                action
        );
        return new ModerationThresholdCalibrator.Sample(
                testCase,
                observation(Map.of("harassment", harassment, "sexual", sexual))
        );
    }

    private OpenAiModerationObservation observation(Map<String, Double> scores) {
        return new OpenAiModerationObservation(
                "test",
                false,
                scores.keySet().stream().collect(java.util.stream.Collectors.toMap(
                        key -> key,
                        ignored -> false
                )),
                scores
        );
    }
}
