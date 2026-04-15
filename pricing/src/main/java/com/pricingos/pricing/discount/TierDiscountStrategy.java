package com.pricingos.pricing.discount;

import com.pricingos.common.CustomerTier;
import com.pricingos.common.ICustomerTierService;
import com.pricingos.common.OrderLineItem;

import java.util.Objects;

public class TierDiscountStrategy implements IDiscountStrategy {

    private final ICustomerTierService tierService;

    public TierDiscountStrategy(ICustomerTierService tierService) {
        this.tierService = Objects.requireNonNull(tierService, "tierService cannot be null");
    }

    @Override
    public boolean isEligible(OrderLineItem item, String customerId) {
        Objects.requireNonNull(item, "item cannot be null");
        Objects.requireNonNull(customerId, "customerId cannot be null");

        CustomerTier tier = tierService.getTier(customerId.trim());
        return tier != CustomerTier.STANDARD;
    }

    @Override
    public double applyDiscount(double currentPrice, OrderLineItem item, String customerId) {
        if (!Double.isFinite(currentPrice) || currentPrice < 0)
            throw new IllegalArgumentException("currentPrice must be a non-negative finite number");

        double discountRate = tierService.getDiscountRate(customerId.trim());
        return currentPrice * (1.0 - discountRate);
    }

    @Override
    public String getStrategyName() {
        return "TIER_DISCOUNT";
    }
}
