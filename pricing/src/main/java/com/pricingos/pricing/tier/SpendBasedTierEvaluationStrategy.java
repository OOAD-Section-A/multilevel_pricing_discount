package com.pricingos.pricing.tier;

import com.pricingos.common.CustomerTier;
import java.util.Objects;

public class SpendBasedTierEvaluationStrategy implements TierEvaluationStrategy {

    private static final double PLATINUM_MIN = 100000;
    private static final double GOLD_MIN = 50000;
    private static final double SILVER_MIN = 10000;

    @Override
    public CustomerTier evaluate(String customerId, double annualSpend, int annualOrderCount) {
        Objects.requireNonNull(customerId, "customerId cannot be null");
        if (customerId.trim().isEmpty()) {
            throw new IllegalArgumentException("customerId cannot be blank");
        }
        if (!Double.isFinite(annualSpend) || annualSpend < 0.0) {
            throw new IllegalArgumentException("annualSpend must be a non-negative finite number");
        }
        if (annualOrderCount < 0) {
            throw new IllegalArgumentException("annualOrderCount must be >= 0");
        }

        if (annualSpend >= PLATINUM_MIN) {
            return CustomerTier.PLATINUM;
        }
        if (annualSpend >= GOLD_MIN) {
            return CustomerTier.GOLD;
        }
        if (annualSpend >= SILVER_MIN) {
            return CustomerTier.SILVER;
        }
        return CustomerTier.STANDARD;
    }
}
