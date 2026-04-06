package com.pricingos.pricing.promotion;

import com.pricingos.common.DiscountType;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Domain object representing a single promotional campaign.
 *
 * <p>Maps directly to the Component 5 fields in the Data Dictionary:
 * promo_id, promo_name, coupon_code, discount_type, discount_value,
 * start_date, end_date, eligible_sku_ids, min_cart_value, max_uses, current_use_count.
 *
 * <p>Creational pattern (Builder): safe, readable construction of Promotion objects
 * with validation rules enforced at build time — mirrors the Contract.Builder approach
 * used by the contract team for consistency across the codebase.
 *
 * <p>GRASP Information Expert: this class owns the logic for determining its own
 * applicability (isApplicableTo) rather than scattering that logic across services.
 */
public class Promotion {

    private final String promoId;
    private final String promoName;
    private final String couponCode;
    private final DiscountType discountType;
    private final double discountValue;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private final List<String> eligibleSkuIds;
    private final double minCartValue;
    private final int maxUses;

    // Mutable state: incremented on each successful redemption.
    private int currentUseCount;

    // Tracks whether this promo has been explicitly expired by the system.
    private boolean expired;

    private Promotion(Builder builder) {
        this.promoId        = builder.promoId;
        this.promoName      = builder.promoName;
        this.couponCode     = builder.couponCode;
        this.discountType   = builder.discountType;
        this.discountValue  = builder.discountValue;
        this.startDate      = builder.startDate;
        this.endDate        = builder.endDate;
        this.eligibleSkuIds = Collections.unmodifiableList(builder.eligibleSkuIds);
        this.minCartValue   = builder.minCartValue;
        this.maxUses        = builder.maxUses;
        this.currentUseCount = 0;
        this.expired         = false;
    }

    // ── Domain logic ─────────────────────────────────────────────────────────────

    /**
     * Returns true if this promotion is currently active and applicable to the
     * given SKU and cart total — GRASP Information Expert keeping eligibility rules here.
     *
     * @param skuId     the SKU being purchased
     * @param cartTotal the cart subtotal before this promo is applied
     * @param today     the date to evaluate validity against
     * @return applicability flag
     */
    public boolean isApplicableTo(String skuId, double cartTotal, LocalDate today) {
        Objects.requireNonNull(skuId, "skuId cannot be null");
        Objects.requireNonNull(today, "today cannot be null");

        if (expired) return false;
        if (today.isBefore(startDate) || today.isAfter(endDate)) return false;
        if (!eligibleSkuIds.contains(skuId.trim())) return false;
        if (cartTotal < minCartValue) return false;
        if (maxUses > 0 && currentUseCount >= maxUses) return false;
        return true;
    }

    /** Returns whether the promotion's validity window has lapsed as of today. */
    public boolean isExpiredOn(LocalDate today) {
        return today.isAfter(endDate);
    }

    /**
     * Computes the actual monetary discount amount to deduct from the line price.
     * Handles PERCENTAGE_OFF and FIXED_AMOUNT; BUY_X_GET_Y discount is returned
     * as a percentage (1.0 = 100% off one unit) and must be resolved by the caller.
     *
     * @param lineSubtotal the price of the line item before this discount
     * @return the discount amount to subtract (never negative)
     */
    public double computeDiscountAmount(double lineSubtotal) {
        return switch (discountType) {
            case PERCENTAGE_OFF -> lineSubtotal * (discountValue / 100.0);
            case FIXED_AMOUNT   -> Math.min(discountValue, lineSubtotal); // cap at subtotal
            case BUY_X_GET_Y    -> lineSubtotal * (discountValue / 100.0); // caller maps X/Y logic
        };
    }

    /** Increments the redemption counter; called when an order is confirmed. */
    public synchronized void recordRedemption() {
        currentUseCount++;
    }

    /** Marks this promotion as expired (called by the scheduled expiry job). */
    public synchronized void markExpired() {
        this.expired = true;
    }

    // ── Getters ──────────────────────────────────────────────────────────────────

    public String getPromoId()              { return promoId; }
    public String getPromoName()            { return promoName; }
    public String getCouponCode()           { return couponCode; }
    public DiscountType getDiscountType()   { return discountType; }
    public double getDiscountValue()        { return discountValue; }
    public LocalDate getStartDate()         { return startDate; }
    public LocalDate getEndDate()           { return endDate; }
    public List<String> getEligibleSkuIds() { return eligibleSkuIds; }
    public double getMinCartValue()         { return minCartValue; }
    public int getMaxUses()                 { return maxUses; }
    public synchronized int getCurrentUseCount() { return currentUseCount; }
    public synchronized boolean isExpired() { return expired; }

    // ── Builder ───────────────────────────────────────────────────────────────────

    /** Entry point for the Builder. promoId is assigned by the engine, not the caller. */
    static Builder builder(String promoId) {
        return new Builder(promoId);
    }

    static final class Builder {
        private final String promoId;
        private String promoName;
        private String couponCode;
        private DiscountType discountType;
        private double discountValue;
        private LocalDate startDate;
        private LocalDate endDate;
        private List<String> eligibleSkuIds = Collections.emptyList();
        private double minCartValue = 0.0;
        private int maxUses = 0; // 0 = unlimited

        private Builder(String promoId) {
            this.promoId = requireNonBlank(promoId, "promoId");
        }

        public Builder promoName(String name)             { this.promoName = requireNonBlank(name, "promoName"); return this; }
        public Builder couponCode(String code)            { this.couponCode = requireNonBlank(code, "couponCode"); return this; }
        public Builder discountType(DiscountType type)    { this.discountType = Objects.requireNonNull(type, "discountType cannot be null"); return this; }
        public Builder discountValue(double value)        {
            if (!Double.isFinite(value) || value <= 0) throw new IllegalArgumentException("discountValue must be > 0");
            this.discountValue = value; return this;
        }
        public Builder startDate(LocalDate date)          { this.startDate = Objects.requireNonNull(date, "startDate cannot be null"); return this; }
        public Builder endDate(LocalDate date)            { this.endDate = Objects.requireNonNull(date, "endDate cannot be null"); return this; }
        public Builder eligibleSkuIds(List<String> skus)  {
            Objects.requireNonNull(skus, "eligibleSkuIds cannot be null");
            if (skus.isEmpty()) throw new IllegalArgumentException("eligibleSkuIds cannot be empty");
            this.eligibleSkuIds = skus; return this;
        }
        public Builder minCartValue(double value)         { this.minCartValue = value; return this; }
        public Builder maxUses(int maxUses)               {
            if (maxUses < 0) throw new IllegalArgumentException("maxUses cannot be negative");
            this.maxUses = maxUses; return this;
        }

        public Promotion build() {
            Objects.requireNonNull(promoName, "promoName is required");
            Objects.requireNonNull(couponCode, "couponCode is required");
            Objects.requireNonNull(discountType, "discountType is required");
            Objects.requireNonNull(startDate, "startDate is required");
            Objects.requireNonNull(endDate, "endDate is required");
            if (endDate.isBefore(startDate))
                throw new IllegalArgumentException("endDate cannot be before startDate");
            return new Promotion(this);
        }

        private static String requireNonBlank(String v, String field) {
            Objects.requireNonNull(v, field + " cannot be null");
            if (v.trim().isEmpty()) throw new IllegalArgumentException(field + " cannot be blank");
            return v.trim();
        }
    }
}