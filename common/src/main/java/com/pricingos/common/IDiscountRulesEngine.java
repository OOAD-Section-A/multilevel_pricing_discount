package com.pricingos.common;

/**
 * Central rules engine for calculating final prices after applying all eligible discounts.
 * Orchestrates discount strategies, validates compliance, and enforces floor prices.
 */
public interface IDiscountRulesEngine {

    PriceResult[] calculateFinalPrice(OrderLineItem[] cart, String customerId);

    boolean submitPricingOverride(PricingOverrideRequest request);
}
