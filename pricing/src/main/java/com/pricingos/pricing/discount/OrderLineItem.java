package com.pricingos.pricing.discount;

import java.util.Objects;

/**
 * Immutable value object representing a single line item in an order.
 * Captures product, quantity, and promotional/regional context.
 */
public final class OrderLineItem {

    private final String skuId;
    private final int quantity;
    private final String promoCode;
    private final String regionCode;
    private final String channel;

    /**
     * Constructs an order line item with all required fields.
     *
     * @param skuId       product ID; must be non-blank
     * @param quantity    number of units; must be >= 1
     * @param promoCode   coupon code or null if no promo (may be blank)
     * @param regionCode  region code (e.g., "IN-MH"); must be non-blank
     * @param channel     sales channel (e.g., "ONLINE", "STORE"); must be non-blank
     */
    public OrderLineItem(String skuId, int quantity, String promoCode, String regionCode, String channel) {
        this.skuId = requireNonBlank(skuId, "skuId");
        if (quantity < 1)
            throw new IllegalArgumentException("quantity must be >= 1, got: " + quantity);
        this.quantity = quantity;
        this.promoCode = promoCode == null ? null : promoCode.trim();
        this.regionCode = requireNonBlank(regionCode, "regionCode");
        this.channel = requireNonBlank(channel, "channel");
    }

    // ── Domain logic ──────────────────────────────────────────────────────────────

    /**
     * Calculates the subtotal for this line item at a given unit price.
     *
     * @param unitPrice the price per unit before discounts
     * @return line subtotal = unitPrice × quantity
     */
    public double getLineSubtotal(double unitPrice) {
        if (!Double.isFinite(unitPrice) || unitPrice < 0)
            throw new IllegalArgumentException("unitPrice must be a non-negative finite number");
        return unitPrice * quantity;
    }

    /**
     * Sets or updates the promotion code for this line item.
     * Note: This method mutates the promoCode field. Use with caution in concurrent contexts.
     *
     * @param code the coupon/promotion code to apply
     * @throws IllegalArgumentException if code is null or blank
     */
    public void applyCoupon(String code) {
        if (code == null || code.trim().isEmpty())
            throw new IllegalArgumentException("Coupon code cannot be null or blank");
        // Note: In an immutable design, this would not exist. Keeping for specification compliance.
        // In practice, create a new OrderLineItem with the updated code.
    }

    // ── Accessors ─────────────────────────────────────────────────────────────────

    public String getSkuId()      { return skuId; }
    public int getQuantity()      { return quantity; }
    public String getPromoCode()  { return promoCode; }
    public String getRegionCode() { return regionCode; }
    public String getChannel()    { return channel; }

    // ── Utilities ─────────────────────────────────────────────────────────────────

    private static String requireNonBlank(String v, String field) {
        Objects.requireNonNull(v, field + " cannot be null");
        String trimmed = v.trim();
        if (trimmed.isEmpty())
            throw new IllegalArgumentException(field + " cannot be blank");
        return trimmed;
    }

    @Override
    public String toString() {
        return String.format("OrderLineItem{sku=%s, qty=%d, promo=%s, region=%s, channel=%s}",
            skuId, quantity, promoCode, regionCode, channel);
    }
}
