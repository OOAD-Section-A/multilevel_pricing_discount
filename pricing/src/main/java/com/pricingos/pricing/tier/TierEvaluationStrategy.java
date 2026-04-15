package com.pricingos.pricing.tier;

import com.pricingos.common.CustomerTier;

public interface TierEvaluationStrategy {

    CustomerTier evaluate(String customerId, double annualSpend, int annualOrderCount);
}
