package com.pricingos.pricing.discount;

import com.pricingos.common.OrderLineItem;
import com.pricingos.common.IVolumeDiscountService;

import java.util.Objects;

public class VolumeDiscountStrategy implements IDiscountStrategy {

    private final IVolumeDiscountService volumeService;

    public VolumeDiscountStrategy(IVolumeDiscountService volumeService) {
        this.volumeService = Objects.requireNonNull(volumeService, "volumeService cannot be null");
    }

    @Override
    public boolean isEligible(OrderLineItem item, String customerId) {
        Objects.requireNonNull(item, "item cannot be null");
        Objects.requireNonNull(customerId, "customerId cannot be null");

        return volumeService.hasVolumePromotion(item.getSkuId());
    }

    @Override
    public double applyDiscount(double currentPrice, OrderLineItem item, String customerId) {
        Objects.requireNonNull(item, "item cannot be null");
        Objects.requireNonNull(customerId, "customerId cannot be null");

        if (!Double.isFinite(currentPrice) || currentPrice < 0)
            throw new IllegalArgumentException("currentPrice must be a non-negative finite number");

        double discountedUnitPrice = volumeService.getDiscountedUnitPrice(
            item.getSkuId(),
            item.getQuantity(),
            currentPrice
        );

        return discountedUnitPrice;
    }

    @Override
    public String getStrategyName() {
        return "VOLUME_DISCOUNT";
    }
}
