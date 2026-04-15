package com.pricingos.pricing.discount;

import com.pricingos.common.OrderLineItem;
import com.pricingos.common.IVolumeDiscountService;

import java.util.Objects;

/**
 * Discount strategy that applies volume/tiered quantity discounts.
 * Implements IDiscountStrategy.
 *
 * <p>Used for: "Buy 100+ units = 10% off, buy 500+ units = 20% off" type promotions.
 */
public class VolumeDiscountStrategy implements IDiscountStrategy {

    private final IVolumeDiscountService volumeService;

    /**
     * Constructs the strategy with a volume discount service dependency.
     *
     * @param volumeService service for retrieving volume discount schedules
     */
    public VolumeDiscountStrategy(IVolumeDiscountService volumeService) {
        this.volumeService = Objects.requireNonNull(volumeService, "volumeService cannot be null");
    }

    /**
     * {@inheritDoc}
     *
     * <p>Returns true if a volume promotion schedule has been registered for this SKU.
     */
    @Override
    public boolean isEligible(OrderLineItem item, String customerId) {
        Objects.requireNonNull(item, "item cannot be null");
        Objects.requireNonNull(customerId, "customerId cannot be null");

        return volumeService.hasVolumePromotion(item.getSkuId());
    }

    /**
     * {@inheritDoc}
     *
     * <p>Applies the volume discount for the item's SKU and quantity.
     * The returned price is the per-unit price after volume discount is applied.
     * Multiply by quantity to get line total.
     */
    @Override
    public double applyDiscount(double currentPrice, OrderLineItem item, String customerId) {
        Objects.requireNonNull(item, "item cannot be null");
        Objects.requireNonNull(customerId, "customerId cannot be null");

        if (!Double.isFinite(currentPrice) || currentPrice < 0)
            throw new IllegalArgumentException("currentPrice must be a non-negative finite number");

        // volumeService.getDiscountedUnitPrice returns the unit price after volume discount
        double discountedUnitPrice = volumeService.getDiscountedUnitPrice(
            item.getSkuId(),
            item.getQuantity(),
            currentPrice
        );

        return discountedUnitPrice;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getStrategyName() {
        return "VOLUME_DISCOUNT";
    }
}
