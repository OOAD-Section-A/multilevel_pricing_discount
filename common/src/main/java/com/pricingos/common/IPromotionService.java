package com.pricingos.common;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Promotion & Campaign Manager use-cases exposed to the rest of the subsystem.
 *
 * <p>Other teams (Inventory, Order Fulfillment) integrate through this interface
 * rather than coupling to the concrete implementation — SOLID DIP.
 *
 * <p>Component 5 in the Data Dictionary and Component Table.
 */
public interface IPromotionService {

    /**
     * Registers a new promotional campaign and returns the generated promo_id.
     *
     * @param name          human-readable campaign name (e.g., "Diwali Sale 2024")
     * @param couponCode    alphanumeric code customers enter (e.g., "DIWALI20")
     * @param discountType  mode of discount (PERCENTAGE_OFF / FIXED_AMOUNT / BUY_X_GET_Y)
     * @param discountValue numeric discount amount matching the type
     * @param startDate     first date the promo is valid
     * @param endDate       last date the promo is valid (inclusive)
     * @param eligibleSkuIds list of SKU IDs this promotion applies to
     * @param minCartValue  minimum cart value required for eligibility
     * @param maxUses       maximum number of redemptions allowed across all customers
     * @return generated promo_id
     */
    String createPromotion(String name, String couponCode, DiscountType discountType,
                           double discountValue, LocalDate startDate, LocalDate endDate,
                           List<String> eligibleSkuIds, double minCartValue, int maxUses);

    /**
     * Validates a coupon code against the current cart context.
     * Returns the discount value if valid, or throws a domain exception if not.
     *
     * @param couponCode    code entered by the customer
     * @param skuId         SKU being purchased
     * @param cartTotal     current cart subtotal before promo discount
     * @return discount amount or percentage to apply
     * @throws com.pricingos.pricing.promotion.InvalidPromoCodeException if invalid/expired/inapplicable
     */
    double validateAndGetDiscount(String couponCode, String skuId, double cartTotal);

    /**
     * Records a successful coupon redemption; increments current_use_count.
     * Call this only after the order is confirmed.
     */
    void recordRedemption(String couponCode);

    /**
     * Returns the active promotion catalog (all currently valid promotions).
     */
    List<String> getActivePromoCodes();

    /**
     * Returns the total redemption count for a coupon code.
     */
    int getRedemptionCount(String couponCode);

    /**
     * Expires all promotions whose end_date is before today.
     * Intended to be called by a scheduled job.
     */
    void expireStalePromotions();
}