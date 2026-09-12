package dev.chatmoderation.validation;

import dev.chatmoderation.core.ChatModerationService;
import dev.chatmoderation.core.DefaultChatModerationService;
import dev.chatmoderation.core.model.ModerationAction;
import dev.chatmoderation.core.model.ModerationReason;
import dev.chatmoderation.core.model.ModerationResult;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ModerationEvaluation {
    private static final List<ModerationReason> EVALUATED_REASONS = List.of(
            ModerationReason.PROFANITY,
            ModerationReason.SEXUAL_CONTENT,
            ModerationReason.PERSONAL_INFORMATION,
            ModerationReason.URL,
            ModerationReason.SPAM
    );

    private ModerationEvaluation() {
    }

    public static void main(String[] args) {
        ChatModerationService service = new DefaultChatModerationService(
                ValidationResources.loadKeywordsByReason(),
                ValidationResources.loadKeywordExceptions(),
                ValidationResources.loadAliasesByReason()
        );
        System.out.println("Moderation accuracy evaluation; not a CI pass/fail criterion");
        evaluate(
                "Calibration dataset: evaluation.tsv",
                ValidationResources.loadEvaluation(),
                300,
                service
        );
        evaluate(
                "Holdout dataset: evaluation-holdout.tsv",
                ValidationResources.loadEvaluationHoldout(),
                150,
                service
        );
    }

    private static void evaluate(
            String label,
            List<EvaluationCase> cases,
            int minimumCases,
            ChatModerationService service
    ) {
        if (cases.size() < minimumCases) {
            throw new IllegalStateException(label + " must contain at least " + minimumCases + " cases");
        }
        List<EvaluatedCase> evaluated = cases.stream()
                .map(testCase -> new EvaluatedCase(testCase, service.moderate(testCase.message())))
                .toList();

        System.out.println();
        System.out.println("============================================================");
        System.out.println(label);
        System.out.println("============================================================");
        printOverall(evaluated);
        printActionConfusionMatrix(evaluated);
        printReasonMetrics(evaluated);
        printCategoryMetrics(evaluated);
        printUnsupportedObfuscationMetrics(evaluated);
        printFailures(evaluated);
    }

    private static void printOverall(List<EvaluatedCase> evaluated) {
        int correct = count(evaluated, EvaluatedCase::correct);
        int truePositive = count(evaluated, result -> result.expectedPositive()
                && result.actualPositive());
        int trueNegative = count(evaluated, result -> !result.expectedPositive()
                && !result.actualPositive());
        int falsePositive = count(evaluated, result -> !result.expectedPositive()
                && result.actualPositive());
        int falseNegative = count(evaluated, result -> result.expectedPositive()
                && !result.actualPositive());

        System.out.println("total cases: " + evaluated.size());
        System.out.println("correct: " + correct);
        System.out.println("incorrect: " + (evaluated.size() - correct));
        printRate("accuracy", correct, evaluated.size());
        System.out.println("true positive: " + truePositive);
        System.out.println("true negative: " + trueNegative);
        System.out.println("false positive: " + falsePositive);
        System.out.println("false negative: " + falseNegative);
        printRate("precision", truePositive, truePositive + falsePositive);
        printRate("recall", truePositive, truePositive + falseNegative);
        printF1(truePositive, falsePositive, falseNegative);
        printRate("false positive rate", falsePositive, falsePositive + trueNegative);
        printRate("false negative rate", falseNegative, falseNegative + truePositive);
    }

    private static void printActionConfusionMatrix(List<EvaluatedCase> evaluated) {
        int[][] matrix = new int[ModerationAction.values().length][ModerationAction.values().length];
        for (EvaluatedCase result : evaluated) {
            matrix[result.testCase().expectedAction().ordinal()][result.result().action().ordinal()]++;
        }

        System.out.println();
        System.out.println("Action confusion matrix (Expected \\ Actual)");
        System.out.printf("%-10s %8s %8s %8s%n", "", "ALLOW", "MASK", "BLOCK");
        for (ModerationAction expected : ModerationAction.values()) {
            System.out.printf(
                    Locale.ROOT,
                    "%-10s %8d %8d %8d%n",
                    expected,
                    matrix[expected.ordinal()][ModerationAction.ALLOW.ordinal()],
                    matrix[expected.ordinal()][ModerationAction.MASK.ordinal()],
                    matrix[expected.ordinal()][ModerationAction.BLOCK.ordinal()]
            );
        }
    }

    private static void printReasonMetrics(List<EvaluatedCase> evaluated) {
        System.out.println();
        System.out.println("Reason metrics");
        System.out.printf("%-24s %10s %10s %10s %6s%n", "reason", "precision", "recall", "f1", "support");
        for (ModerationReason reason : EVALUATED_REASONS) {
            int truePositive = count(evaluated, result -> result.expects(reason)
                    && result.hasReason(reason));
            int falsePositive = count(evaluated, result -> !result.expects(reason)
                    && result.hasReason(reason));
            int falseNegative = count(evaluated, result -> result.expects(reason)
                    && !result.hasReason(reason));
            int support = truePositive + falseNegative;
            System.out.printf(
                    Locale.ROOT,
                    "%-24s %9.2f%% %9.2f%% %9.2f%% %6d%n",
                    reason,
                    percent(truePositive, truePositive + falsePositive),
                    percent(truePositive, support),
                    f1Percent(truePositive, falsePositive, falseNegative),
                    support
            );
        }
    }

    private static void printCategoryMetrics(List<EvaluatedCase> evaluated) {
        Map<EvaluationCategory, List<EvaluatedCase>> byCategory = new EnumMap<>(
                EvaluationCategory.class
        );
        for (EvaluatedCase result : evaluated) {
            byCategory.computeIfAbsent(
                    result.testCase().category(),
                    ignored -> new ArrayList<>()
            ).add(result);
        }

        System.out.println();
        System.out.println("Dataset category accuracy");
        for (EvaluationCategory category : EvaluationCategory.values()) {
            List<EvaluatedCase> categoryCases = byCategory.getOrDefault(category, List.of());
            int correct = count(categoryCases, EvaluatedCase::correct);
            System.out.printf(
                    Locale.ROOT,
                    "%-24s %3d/%-3d %6.2f%%%n",
                    category,
                    correct,
                    categoryCases.size(),
                    percent(correct, categoryCases.size())
            );
        }
    }

    private static void printUnsupportedObfuscationMetrics(List<EvaluatedCase> evaluated) {
        List<EvaluatedCase> unsupported = evaluated.stream()
                .filter(result -> result.testCase().category() == EvaluationCategory.OBFUSCATION)
                .filter(result -> result.testCase().support() == EvaluationSupport.UNSUPPORTED)
                .toList();
        int detected = count(unsupported, EvaluatedCase::actualPositive);

        System.out.println();
        System.out.println("Unsupported obfuscation cases: " + unsupported.size());
        printRate("unsupported obfuscation dataset share", unsupported.size(), evaluated.size());
        printRate("unsupported obfuscation detected", detected, unsupported.size());
        printRate(
                "unsupported obfuscation false negative rate",
                unsupported.size() - detected,
                unsupported.size()
        );
    }

    private static void printFailures(List<EvaluatedCase> evaluated) {
        System.out.println();
        System.out.println("Incorrect cases");
        for (EvaluatedCase evaluatedCase : evaluated) {
            if (evaluatedCase.correct()) {
                continue;
            }
            System.out.println(evaluatedCase.failureType());
            System.out.println("category: " + evaluatedCase.testCase().category());
            System.out.println("message: " + evaluatedCase.testCase().message());
            System.out.println("expected: " + evaluatedCase.testCase().expectedAction()
                    + " / " + evaluatedCase.testCase().expectedReason());
            System.out.println("actual: " + evaluatedCase.result().action()
                    + " / " + evaluatedCase.result().reasons());
            System.out.println();
        }
    }

    private static int count(
            List<EvaluatedCase> evaluated,
            java.util.function.Predicate<EvaluatedCase> predicate
    ) {
        return (int) evaluated.stream().filter(predicate).count();
    }

    private static void printRate(String label, int numerator, int denominator) {
        System.out.printf(Locale.ROOT, "%s: %.2f%%%n", label, percent(numerator, denominator));
    }

    private static void printF1(int truePositive, int falsePositive, int falseNegative) {
        System.out.printf(
                Locale.ROOT,
                "f1: %.2f%%%n",
                f1Percent(truePositive, falsePositive, falseNegative)
        );
    }

    private static double f1Percent(int truePositive, int falsePositive, int falseNegative) {
        int denominator = 2 * truePositive + falsePositive + falseNegative;
        return denominator == 0 ? 0.0 : 200.0 * truePositive / denominator;
    }

    private static double percent(int numerator, int denominator) {
        return denominator == 0 ? 0.0 : 100.0 * numerator / denominator;
    }

    private record EvaluatedCase(EvaluationCase testCase, ModerationResult result) {
        boolean correct() {
            return testCase.expectedAction() == result.action()
                    && (testCase.expectedReason() == null
                    || result.reasons().contains(testCase.expectedReason()));
        }

        boolean expectedPositive() {
            return testCase.expectedAction() != ModerationAction.ALLOW;
        }

        boolean actualPositive() {
            return result.action() != ModerationAction.ALLOW;
        }

        boolean expects(ModerationReason reason) {
            return testCase.expectedReason() == reason;
        }

        boolean hasReason(ModerationReason reason) {
            return result.reasons().contains(reason);
        }

        String failureType() {
            if (!expectedPositive() && actualPositive()) {
                return "FALSE POSITIVE";
            }
            if (expectedPositive() && !actualPositive()) {
                return "FALSE NEGATIVE";
            }
            if (testCase.expectedAction() != result.action()) {
                return "ACTION MISMATCH";
            }
            return "REASON MISMATCH";
        }
    }
}
