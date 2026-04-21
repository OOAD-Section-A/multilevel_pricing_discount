package com.pricingos.common;

// [Requirement 2 - Tier Distribution] Interface contract - implementation to be provided by consuming team
public interface ICustomerTierDistributionService {

    // Groups all provided customers by tier and computes global average discount.
    // Data source: customer_tier_cache.tier for grouping, customer_tier_overrides.override_tier
    //   takes precedence if present. Discount rates from CustomerTier.getDiscountRate().
    TierDistributionResult groupCustomersByTier(String[] customerIds);

    // Returns mean of getDiscountRate() across all provided customers.
    // Data source: customer_tier_cache.tier (or override if present),
    //   then CustomerTier.getDiscountRate() for each resolved tier.
    double getGlobalAverageDiscountRate(String[] customerIds);
}
