package com.pricingos.pricing.promotion;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Domain object representing a Bundled Pricing promotion.
 *
 * <p>Component 5 — Promotion &amp; Campaign Manager, Bundled Pricing feature.
 * Supports: "Manages discounts for specific product combinations
 * (e.g., Buy a Printer and 3 Ink Cartridges, get 15% off)."
 *
 * <p>A bundle discount triggers when ALL required bundle SKUs are simultaneously
 * present in the customer's cart. Missing even one SKU means the discount does not apply.
 *
 * <p>Design patterns:
 * <ul>
 *   <li><b>Creational (Builder)</b>: safe construction with all validations at {@code build()} time,
 *       consistent with {@link Promotion.Builder} used across this module.</li>
 *   <li><b>GRASP Information Expert</b>: this class owns {@link #isApplicableTo} logic so bundle
 *       eligibility rules are not scattered across services.</li>
 * </ul>
 */
public final class BundledPromotion {

    private final String promoId;
    private final String name;

    /** All SKU IDs that MUST appear in the cart for the bundle to trigger. */
    private final Set<String> bundleSkuIds;

    /** Percentage discount applied to the full cart total when bundle is matched. 0–100. */
    private final double discountPct;

    private final LocalDate startDate;
    private final LocalDate endDate;

    /** Marks whether this promotion has been explicitly expired by the scheduler. */
    private volatile boolean expired;

    private BundledPromotion(Builder builder) {
        this.promoId       = builder.promoId;
        this.name          = builder.name;
        this.bundleSkuIds  = Collections.unmodifiableSet(new HashSet<>(builder.bundleSkuIds));
        this.discountPct   = builder.discountPct;
        this.startDate     = builder.startDate;
        this.endDate       = builder.endDate;
        this.expired       = false;
    }

    // ── Domain logic ──────────────────────────────────────────────────────────────

    /**
     * Returns {@code true} if this bundle is currently active and all required SKUs
     * are present in the given cart — GRASP Information Expert.
     *
     * @param cartSkuIds SKU IDs present in the current cart (set semantics; duplicates ignored)
     * @param today      the date to evaluate date-window validity against
     * @return true if the bundle discount should apply
     */
    public boolean isApplicableTo(Iterable<String> cartSkuIds, LocalDate today) {
        Objects.requireNonNull(cartSkuIds, "cartSkuIds cannot be null");
        Objects.requireNonNull(today, "today cannot be null");
        if (expired) return false;
        if (today.isBefore(startDate) || today.isAfter(endDate)) return false;

        // Build a normalised set from the cart for O(n) containsAll check.
        Set<String> normalizedCart = new HashSet<>();
        for (String sku : cartSkuIds) {
            if (sku != null) normalizedCart.add(sku.trim());
        }
        return normalizedCart.containsAll(bundleSkuIds);
    }

    /**
     * Computes the monetary discount amount to deduct from the cart total.
     *
     * @param cartTotal full cart subtotal before this bundle discount
     * @return {@code cartTotal × (discountPct / 100)}
     */
    public double computeDiscount(double cartTotal) {
        return cartTotal * (discountPct / 100.0);
    }

    /** Returns whether the promotion's date window has lapsed as of today. */
    public boolean isExpiredOn(LocalDate today) {
        return today.isAfter(endDate);
    }

    /** Marks this promotion as expired (called by the scheduled expiry job). */
    public synchronized void markExpired() { this.expired = true; }

    // ── Getters ───────────────────────────────────────────────────────────────────

    public String          getPromoId()       { return promoId; }
    public String          getName()          { return name; }
    public Set<String>     getBundleSkuIds()  { return bundleSkuIds; }
    public double          getDiscountPct()   { return discountPct; }
    public LocalDate       getStartDate()     { return startDate; }
    public LocalDate       getEndDate()       { return endDate; }
    public boolean         isExpired()        { return expired; }

    // ── Builder ───────────────────────────────────────────────────────────────────

    /** Entry point — promoId is assigned by the manager, not the caller. */
    static Builder builder(String promoId) { return new Builder(promoId); }

    public static final class Builder {
        private final String promoId;
        private String name;
        private Set<String> bundleSkuIds;
        private double discountPct;
        private LocalDate startDate;
        private LocalDate endDate;

        private Builder(String promoId) {
            this.promoId = requireNonBlank(promoId, "promoId");
        }

        public Builder name(String name) {
            this.name = requireNonBlank(name, "name"); return this;
        }
        public Builder bundleSkuIds(Set<String> skus) {
            Objects.requireNonNull(skus, "bundleSkuIds cannot be null");
            if (skus.isEmpty()) throw new IllegalArgumentException("bundleSkuIds cannot be empty");
            this.bundleSkuIds = skus; return this;
        }
        public Builder discountPct(double pct) {
            if (!Double.isFinite(pct) || pct <= 0 || pct > 100)
                throw new IllegalArgumentException("discountPct must be in (0, 100], got: " + pct);
            this.discountPct = pct; return this;
        }
        public Builder startDate(LocalDate d) {
            this.startDate = Objects.requireNonNull(d, "startDate cannot be null"); return this;
        }
        public Builder endDate(LocalDate d) {
            this.endDate = Objects.requireNonNull(d, "endDate cannot be null"); return this;
        }

        public BundledPromotion build() {
            Objects.requireNonNull(name,         "name is required");
            Objects.requireNonNull(bundleSkuIds, "bundleSkuIds is required");
            Objects.requireNonNull(startDate,    "startDate is required");
            Objects.requireNonNull(endDate,      "endDate is required");
            if (endDate.isBefore(startDate))
                throw new IllegalArgumentException("endDate cannot be before startDate");
            return new BundledPromotion(this);
        }

        private static String requireNonBlank(String v, String field) {
            Objects.requireNonNull(v, field + " cannot be null");
            if (v.trim().isEmpty()) throw new IllegalArgumentException(field + " cannot be blank");
            return v.trim();
        }
    }
}
