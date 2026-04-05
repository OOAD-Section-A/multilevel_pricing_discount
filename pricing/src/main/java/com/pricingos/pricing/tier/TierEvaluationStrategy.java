package com.pricingos.pricing.tier;

import com.pricingos.common.CustomerTier;

/**
 * Behavioral pattern (Strategy): encapsulates customer tier evaluation rules.
 */
public interface TierEvaluationStrategy {

    CustomerTier evaluate(String customerId, double annualSpend, int annualOrderCount);
}
