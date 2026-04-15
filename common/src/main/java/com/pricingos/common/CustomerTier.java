package com.pricingos.common;

public enum CustomerTier {
    STANDARD(0.0),
    SILVER(0.05),
    GOLD(0.10),
    PLATINUM(0.15);

    private final double discountRate;

    CustomerTier(double discountRate) {
        this.discountRate = discountRate;
    }

    public double getDiscountRate() {
        return discountRate;
    }
}
