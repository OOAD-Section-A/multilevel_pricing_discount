package com.pricingos.pricing.promotion;

import com.pricingos.common.VolumeTierRule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Domain object representing a volume (tiered) discount schedule for a single SKU.
 *
 * <p>Component 5 — Promotion &amp; Campaign Manager, Volume &amp; Tiered Discounts feature.
 * Supports: "Automatically applies lower per-unit prices as order quantities increase."
 *
 * <p>Holds an ordered, validated list of {@link VolumeTierRule}s. Rules are sorted by
 * {@code minQty} ascending so the first matching rule is always the correct tier.
 *
 * <p>GRASP Information Expert: owns the logic for selecting the applicable tier.
 */
public final class VolumePricingPromotion {

    private final String promoId;
    private final String skuId;

    /**
     * Sorted (ascending by minQty) list of tier rules. Maintained in order so that
     * {@link #findTierForQty} can scan from the lowest tier upward.
     */
    private final List<VolumeTierRule> tiers;

    /**
     * Constructs a volume pricing promotion after validating tier consistency.
     *
     * @param promoId generated ID
     * @param skuId   the SKU this schedule applies to
     * @param tiers   raw tier list — will be sorted and validated
     * @throws IllegalArgumentException if tiers overlap, have gaps, or no unlimited top tier
     */
    VolumePricingPromotion(String promoId, String skuId, List<VolumeTierRule> tiers) {
        this.promoId = Objects.requireNonNull(promoId, "promoId cannot be null");
        this.skuId   = Objects.requireNonNull(skuId,   "skuId cannot be null");
        Objects.requireNonNull(tiers, "tiers cannot be null");
        if (tiers.isEmpty()) throw new IllegalArgumentException("tiers list cannot be empty");

        // Sort tiers ascending by minQty.
        List<VolumeTierRule> sorted = new ArrayList<>(tiers);
        sorted.sort((a, b) -> Integer.compare(a.getMinQty(), b.getMinQty()));

        validateTiers(sorted);
        this.tiers = Collections.unmodifiableList(sorted);
    }

    // ── Domain logic ──────────────────────────────────────────────────────────────

    /**
     * Finds and returns the {@link VolumeTierRule} that matches the given quantity.
     * Returns {@code null} if no tier covers the quantity (should not happen if tiers are
     * well-formed, but callers must handle the null gracefully).
     *
     * @param quantity the order quantity to match
     * @return the matching tier rule, or null if none applies
     */
    public VolumeTierRule findTierForQty(int quantity) {
        for (VolumeTierRule tier : tiers) {
            if (tier.appliesToQuantity(quantity)) return tier;
        }
        return null;
    }

    /**
     * Returns the discounted unit price for the given base unit price and quantity.
     * If no tier matches, returns {@code baseUnitPrice} unchanged.
     *
     * @param baseUnitPrice catalogue price per unit
     * @param quantity      order quantity
     * @return net unit price after the applicable tier discount
     */
    public double computeDiscountedUnitPrice(double baseUnitPrice, int quantity) {
        VolumeTierRule tier = findTierForQty(quantity);
        return (tier != null) ? tier.computeDiscountedUnitPrice(baseUnitPrice) : baseUnitPrice;
    }

    // ── Getters ───────────────────────────────────────────────────────────────────

    public String             getPromoId() { return promoId; }
    public String             getSkuId()   { return skuId; }
    public List<VolumeTierRule> getTiers() { return tiers; }

    // ── Private helpers ───────────────────────────────────────────────────────────

    /**
     * Validates that the sorted tier list is non-overlapping, covers qty=1 upward,
     * and has exactly one unlimited (maxQty=0) top tier.
     */
    private static void validateTiers(List<VolumeTierRule> sorted) {
        int unlimitedCount = 0;
        for (int i = 0; i < sorted.size(); i++) {
            VolumeTierRule t = sorted.get(i);
            if (t.getMaxQty() == 0) {
                unlimitedCount++;
                if (i != sorted.size() - 1)
                    throw new IllegalArgumentException(
                        "The unlimited tier (maxQty=0) must be the last tier.");
            }
            if (i > 0) {
                VolumeTierRule prev = sorted.get(i - 1);
                // Tiers must be contiguous: prev.maxQty + 1 == this.minQty
                if (prev.getMaxQty() != 0 && prev.getMaxQty() + 1 != t.getMinQty())
                    throw new IllegalArgumentException(
                        "Tiers must be contiguous. Gap found between tier ending at "
                            + prev.getMaxQty() + " and tier starting at " + t.getMinQty());
            }
        }
        if (unlimitedCount == 0)
            throw new IllegalArgumentException(
                "Volume promotion must have exactly one open-ended top tier (maxQty=0).");
        if (sorted.get(0).getMinQty() != 1)
            throw new IllegalArgumentException(
                "The first tier must start at minQty=1, got: " + sorted.get(0).getMinQty());
    }
}
