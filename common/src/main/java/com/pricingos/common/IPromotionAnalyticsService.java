package com.pricingos.common;

import java.util.Map;

/**
 * Analytics service for tracking promotion performance and redemption patterns.
 * Records discount application events and provides aggregated metrics.
 */
public interface IPromotionAnalyticsService {

    /**
     * Records a promotion redemption event for analytics and reporting.
     * Called after a promotional discount has been successfully applied to an order.
     *
     * @param promoId         the promotion ID being redeemed
     * @param skuId           the SKU on which the discount was applied
     * @param discountApplied the monetary discount amount (in base currency units)
     * @param customerId      the customer who received the discount
     */
    void recordRedemption(String promoId, String skuId, double discountApplied, String customerId);

    /**
     * Returns the total cumulative discount amount given across all redemptions
     * of the specified promotion.
     *
     * @param promoId the promotion ID to query
     * @return total discount amount, or 0.0 if promo has no redemptions
     */
    double getTotalDiscountGiven(String promoId);

    /**
     * Returns the number of times the specified promotion has been redeemed.
     *
     * @param promoId the promotion ID to query
     * @return redemption count, or 0 if promo has no redemptions
     */
    int getRedemptionCount(String promoId);

    /**
     * Returns a breakdown of redemptions by sales channel (e.g., "ONLINE", "STORE", "PHONE").
     * This provides visibility into which channels are driving promotion engagement.
     *
     * @param promoId the promotion ID to query
     * @return map of channel names to total discount given on that channel;
     *         empty map if no channel data is available
     */
    Map<String, Double> getRedemptionsByChannel(String promoId);
}
