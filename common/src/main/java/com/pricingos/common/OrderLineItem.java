package com.pricingos.common;

public final class OrderLineItem {

    private final String skuId;
    private final int quantity;
    private final String regionCode;
    private final String channel;
    private final String appliedCouponCode;

    public OrderLineItem(String skuId, int quantity, String regionCode, String channel) {
        this(skuId, quantity, regionCode, channel, null);
    }

    public OrderLineItem(String skuId, int quantity, String regionCode, String channel, String appliedCouponCode) {
        this.skuId = ValidationUtils.requireNonBlank(skuId, "skuId");
        this.quantity = ValidationUtils.requireAtLeast(quantity, 1, "quantity");
        this.regionCode = ValidationUtils.requireNonBlank(regionCode, "regionCode");
        this.channel = ValidationUtils.requireNonBlank(channel, "channel");
        this.appliedCouponCode = appliedCouponCode == null ? null : ValidationUtils.requireNonBlank(appliedCouponCode, "appliedCouponCode");
    }

    public String getSkuId() {
        return skuId;
    }

    public int getQuantity() {
        return quantity;
    }

    public String getRegionCode() {
        return regionCode;
    }

    public String getChannel() {
        return channel;
    }

    public String getAppliedCouponCode() {
        return appliedCouponCode;
    }

    public String getPromoCode() {
        return appliedCouponCode;
    }

    public double getLineSubtotal(double unitPrice) {
        return ValidationUtils.requireFiniteNonNegative(unitPrice, "unitPrice") * quantity;
    }

    public OrderLineItem withAppliedCoupon(String couponCode) {
        return new OrderLineItem(skuId, quantity, regionCode, channel, couponCode);
    }
}
