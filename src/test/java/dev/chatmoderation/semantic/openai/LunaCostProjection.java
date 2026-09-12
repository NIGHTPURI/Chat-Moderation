package dev.chatmoderation.semantic.openai;

public record LunaCostProjection(
        double fullCoverageCostPerRequestUsd,
        double routedCostPerRequestUsd,
        double operationalRoutingRate
) {
    public LunaCostProjection {
        if (!validCost(fullCoverageCostPerRequestUsd)
                || !validCost(routedCostPerRequestUsd)) {
            throw new IllegalArgumentException("cost per request must be finite and non-negative");
        }
        if (!Double.isFinite(operationalRoutingRate)
                || operationalRoutingRate < 0.0 || operationalRoutingRate > 1.0) {
            throw new IllegalArgumentException("operationalRoutingRate must be between 0 and 1");
        }
    }

    public MonthlyEstimate estimate(long monthlyChats) {
        if (monthlyChats < 0) {
            throw new IllegalArgumentException("monthlyChats must not be negative");
        }
        return new MonthlyEstimate(
                monthlyChats,
                monthlyChats * fullCoverageCostPerRequestUsd,
                monthlyChats * operationalRoutingRate * routedCostPerRequestUsd
        );
    }

    private static boolean validCost(double value) {
        return Double.isFinite(value) && value >= 0.0;
    }

    public record MonthlyEstimate(
            long monthlyChats,
            double fullCoverageUsd,
            double currentRouterUsd
    ) {
    }
}
