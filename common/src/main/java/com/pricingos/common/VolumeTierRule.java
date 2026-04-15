package com.pricingos.common;

import java.util.Objects;

public final class VolumeTierRule {

    private final int minQty;

    private final int maxQty;

    private final double discountPct;

    public VolumeTierRule(int minQty, int maxQty, double discountPct) {
        if (minQty < 1)
            throw new IllegalArgumentException("minQty must be >= 1, got: " + minQty);
        if (maxQty != 0 && maxQty < minQty)
            throw new IllegalArgumentException(
                "maxQty must be >= minQty or 0 (unlimited), got maxQty=" + maxQty + " minQty=" + minQty);
        if (!Double.isFinite(discountPct) || discountPct < 0 || discountPct > 100)
            throw new IllegalArgumentException(
                "discountPct must be in [0, 100], got: " + discountPct);
        this.minQty      = minQty;
        this.maxQty      = maxQty;
        this.discountPct = discountPct;
    }

    public boolean appliesToQuantity(int qty) {
        if (qty < minQty) return false;
        return maxQty == 0 || qty <= maxQty;
    }

    public double computeDiscountedUnitPrice(double baseUnitPrice) {
        return baseUnitPrice * (1.0 - discountPct / 100.0);
    }

    public int    getMinQty()      { return minQty; }
    public int    getMaxQty()      { return maxQty; }
    public double getDiscountPct() { return discountPct; }

    @Override
    public String toString() {
        String upper = (maxQty == 0) ? "∞" : String.valueOf(maxQty);
        return "VolumeTierRule{qty=" + minQty + "–" + upper + ", discount=" + discountPct + "%}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VolumeTierRule that)) return false;
        return minQty == that.minQty && maxQty == that.maxQty
            && Double.compare(discountPct, that.discountPct) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(minQty, maxQty, discountPct);
    }
}
