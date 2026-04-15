package com.pricingos.common;

/**
 * Central rules engine for calculating final prices after applying all eligible discounts.
 * Orchestrates discount strategies, validates compliance, and enforces floor prices.
 */
public interface IDiscountRulesEngine {

    /**
     * Calculates the final discounted price for each line item in the cart.
     * Applies all eligible discount strategies in priority order, validates compliance,
     * and enforces minimum floor prices.
     *
     * <p>Note: This interface depends on discount module classes like OrderLineItem and PriceResult
     * which are not defined in common; the concrete implementation in the pricing module
     * declares the actual method signature with concrete types.
     *
     * @param customerId the customer for whom pricing is being calculated
     * @return computed final prices with applied discounts
     */
    Object calculateFinalPrice(Object cart, String customerId);

    /**
     * Submits a pricing override request for approval.
     * Delegates to the approval workflow engine for human review.
     *
     * @param request the approval request containing override details
     * @return true if the request was successfully submitted; false otherwise
     */
    boolean submitPricingOverride(Object request);
}
