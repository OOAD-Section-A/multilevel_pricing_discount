package com.pricingos.common;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface IPromotionService {

    String createPromotion(String name, String couponCode, DiscountType discountType,
                           double discountValue, LocalDate startDate, LocalDate endDate,
                           List<String> eligibleSkuIds, double minCartValue, int maxUses);

    double validateAndGetDiscount(String couponCode, String skuId, double cartTotal);

    void recordRedemption(String couponCode);

    List<String> getActivePromoCodes();

    int getRedemptionCount(String couponCode);

    void expireStalePromotions();
}
