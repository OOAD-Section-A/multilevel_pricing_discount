package com.pricingos.pricing.discount;

import com.pricingos.common.OrderLineItem;
import com.pricingos.common.IPromotionService;
import com.pricingos.pricing.promotion.InvalidPromoCodeException;

import java.util.Objects;

public class PromoCodeStrategy implements IDiscountStrategy {

    private final IPromotionService promoService;

    public PromoCodeStrategy(IPromotionService promoService) {
        this.promoService = Objects.requireNonNull(promoService, "promoService cannot be null");
    }

    @Override
    public boolean isEligible(OrderLineItem item, String customerId) {
        Objects.requireNonNull(item, "item cannot be null");
        Objects.requireNonNull(customerId, "customerId cannot be null");

        String promoCode = item.getPromoCode();
        return promoCode != null && !promoCode.trim().isEmpty();
    }

    @Override
    public double applyDiscount(double currentPrice, OrderLineItem item, String customerId) {
        if (!Double.isFinite(currentPrice) || currentPrice < 0)
            throw new IllegalArgumentException("currentPrice must be a non-negative finite number");

        try {

            double discountAmount = promoService.validateAndGetDiscount(
                item.getPromoCode(),
                item.getSkuId(),
                currentPrice
            );

            return Math.max(0, currentPrice - discountAmount);
        } catch (InvalidPromoCodeException e) {

            return currentPrice;
        }
    }

    @Override
    public String getStrategyName() {
        return "PROMO_CODE";
    }
}
