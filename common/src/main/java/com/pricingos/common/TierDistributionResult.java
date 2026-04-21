package com.pricingos.common;

// [Requirement 2 - Tier Distribution] Interface contract - implementation to be provided by consuming team
// All data derived from customer_tier_cache table and CustomerTier.getDiscountRate()
public record TierDistributionResult(
    TierBucket[] buckets,               // one entry per tier present in customer_tier_cache
    double globalAverageDiscount        // mean of CustomerTier.getDiscountRate() across all provided customers
) {}
