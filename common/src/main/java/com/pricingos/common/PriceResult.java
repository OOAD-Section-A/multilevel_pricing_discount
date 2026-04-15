package com.pricingos.common;

import java.util.Arrays;

public final class PriceResult {

    private final String skuId;
    private final double basePrice;
    private final double finalDiscountedPrice;
    private final String[] appliedDiscounts;
    private final boolean approved;

    public PriceResult(String skuId, double basePrice, double finalDiscountedPrice,
                       String[] appliedDiscounts, boolean approved) {
        this.skuId = ValidationUtils.requireNonBlank(skuId, "skuId");
        this.basePrice = ValidationUtils.requireFiniteNonNegative(basePrice, "basePrice");
        this.finalDiscountedPrice = ValidationUtils.requireFiniteNonNegative(finalDiscountedPrice, "finalDiscountedPrice");
        if (appliedDiscounts == null) {
            throw new IllegalArgumentException("appliedDiscounts cannot be null");
        }
        this.appliedDiscounts = Arrays.copyOf(appliedDiscounts, appliedDiscounts.length);
        this.approved = approved;
    }

    public String getSkuId() {
        return skuId;
    }

    public double getBasePrice() {
        return basePrice;
    }

    public double getFinalDiscountedPrice() {
        return finalDiscountedPrice;
    }

    public String[] getAppliedDiscounts() {
        return Arrays.copyOf(appliedDiscounts, appliedDiscounts.length);
    }

    public boolean isApproved() {
        return approved;
    }

    public double getDiscountPercentage() {
        if (basePrice == 0.0) {
            return 0.0;
        }
        return ((basePrice - finalDiscountedPrice) / basePrice) * 100.0;
    }

    public String generateReceiptString() {
        return "SKU=" + skuId
            + ", base=" + String.format("%.2f", basePrice)
            + ", final=" + String.format("%.2f", finalDiscountedPrice)
            + ", discountPct=" + String.format("%.2f", getDiscountPercentage())
            + ", applied=" + Arrays.toString(appliedDiscounts)
            + ", status=" + (approved ? "APPROVED" : "PENDING");
    }
}
