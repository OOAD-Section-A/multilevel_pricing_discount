package com.pricingos.pricing.discount;

/**
 * Strategy interface for applying a specific type of discount to an order line item.
 * Different discount types (tier, volume, promo code) implement this contract.
 *
 * <p>Implements the Strategy behavioral pattern — allows discount logic to be plugged in
 * without modifying the core discount engine.
 */
public interface IDiscountStrategy {

    /**
     * Determines whether this discount strategy is applicable to the given order line item
     * and customer context.
     *
     * @param item       the order line item to evaluate
     * @param customerId the customer ID for context-aware eligibility
     * @return true if this strategy should be applied; false otherwise
     */
    boolean isEligible(OrderLineItem item, String customerId);

    /**
     * Applies this discount strategy to the current price.
     * Returns the net price after applying this specific discount.
     *
     * @param currentPrice the current price (may have other discounts already applied)
     * @param item         the order line item context
     * @param customerId   the customer ID
     * @return the new price after applying this discount
     */
    double applyDiscount(double currentPrice, OrderLineItem item, String customerId);

    /**
     * Returns a human-readable name for this strategy.
     * Used for logging, reporting, and building the applied discounts list.
     *
     * @return strategy name (e.g., "TIER_DISCOUNT", "VOLUME_DISCOUNT", "PROMO_CODE")
     */
    String getStrategyName();
}
