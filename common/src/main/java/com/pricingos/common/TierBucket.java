package com.pricingos.common;

// [Requirement 2 - Tier Distribution] Interface contract - implementation to be provided by consuming team
// Data source: customer_tier_cache.tier (VARCHAR(20)) - values match CustomerTier enum names
public record TierBucket(
    CustomerTier tier,      // the tier label; maps to customer_tier_cache.tier
    int count,              // number of customers in this tier
    double percentage       // share of total active customers (0.0-1.0)
) {}
