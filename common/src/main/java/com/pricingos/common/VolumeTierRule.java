package com.pricingos.common;

import java.util.Objects;

/**
 * Immutable value object representing a single tier in a volume-based discount schedule.
 *
 * <p>Component 5 — Promotion &amp; Campaign Manager, Volume &amp; Tiered Discounts feature.
 *
 * <p>Used by {@link IVolumeDiscountService} to define quantity bands, e.g.:
 * <ul>
 *   <li>Tier 1: qty 1–100  → 0% off (full price)</li>
 *   <li>Tier 2: qty 101–500 → 10% off</li>
 *   <li>Tier 3: qty 501+   → 20% off (maxQty = 0 means unlimited)</li>
 * </ul>
 *
 * <p>Placed in {@code common} so other teams can build volume-tier-aware integrations
 * without depending on the concrete implementation — SOLID DIP.
 */
public final class VolumeTierRule {

    /** Inclusive lower bound for this tier. Must be ≥ 1. */
    private final int minQty;

    /** Inclusive upper bound; {@code 0} means unlimited (open-ended top tier). */
    private final int maxQty;

    /** Percentage discount applied to the base unit price when this tier matches. 0–100. */
    private final double discountPct;

    /**
     * Constructs a volume tier rule with validation.
     *
     * @param minQty      inclusive lower bound (must be ≥ 1)
     * @param maxQty      inclusive upper bound (0 = unlimited)
     * @param discountPct percentage discount 0–100
     */
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

    // ── Domain logic ──────────────────────────────────────────────────────────────

    /**
     * Returns {@code true} if this tier applies to the given quantity.
     *
     * @param qty the order quantity to test
     * @return true if minQty ≤ qty ≤ maxQty (or maxQty == 0 and qty ≥ minQty)
     */
    public boolean appliesToQuantity(int qty) {
        if (qty < minQty) return false;
        return maxQty == 0 || qty <= maxQty;
    }

    /**
     * Returns the discounted unit price for this tier.
     *
     * @param baseUnitPrice the base price per unit before any volume discount
     * @return the net unit price after applying this tier's discountPct
     */
    public double computeDiscountedUnitPrice(double baseUnitPrice) {
        return baseUnitPrice * (1.0 - discountPct / 100.0);
    }

    // ── Accessors ─────────────────────────────────────────────────────────────────

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
