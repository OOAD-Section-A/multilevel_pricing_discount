package com.pricingos.pricing.discount;

import com.pricingos.common.CustomerTier;
import com.pricingos.common.ICustomerTierService;

import java.util.Objects;

/**
 * Discount strategy that applies customer tier-based discounts (SILVER, GOLD, PLATINUM).
 * Implements IDiscountStrategy.
 *
 * <p>GRASP Creator: TierDiscountStrategy creates the tier-based price calculation
 * using the injected ICustomerTierService.
 */
public class TierDiscountStrategy implements IDiscountStrategy {

    private final ICustomerTierService tierService;

    /**
     * Constructs the strategy with a tier service dependency.
     *
     * @param tierService service for retrieving customer tier information
     */
    public TierDiscountStrategy(ICustomerTierService tierService) {
        this.tierService = Objects.requireNonNull(tierService, "tierService cannot be null");
    }

    /**
     * {@inheritDoc}
     *
     * <p>Returns true if the customer's tier is not STANDARD (i.e., has a discount rate > 0).
     */
    @Override
    public boolean isEligible(OrderLineItem item, String customerId) {
        Objects.requireNonNull(item, "item cannot be null");
        Objects.requireNonNull(customerId, "customerId cannot be null");

        CustomerTier tier = tierService.getTier(customerId.trim());
        return tier != CustomerTier.STANDARD;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Applies the customer's tier discount rate to the current price.
     * Formula: currentPrice × (1 - tier discount rate)
     */
    @Override
    public double applyDiscount(double currentPrice, OrderLineItem item, String customerId) {
        if (!Double.isFinite(currentPrice) || currentPrice < 0)
            throw new IllegalArgumentException("currentPrice must be a non-negative finite number");

        double discountRate = tierService.getDiscountRate(customerId.trim());
        return currentPrice * (1.0 - discountRate);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getStrategyName() {
        return "TIER_DISCOUNT";
    }
}
