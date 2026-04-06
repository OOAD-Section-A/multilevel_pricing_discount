package com.pricingos.common;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable value object representing one line item in a final invoice.
 *
 * <p>This is the "final invoice data structure" output of the overall pricing subsystem.
 * The Discount Rules Engine assembles one InvoiceLineItem per order line after all
 * discounts, tier adjustments, contract prices, and promo codes have been applied.
 *
 * <p>GRASP Information Expert: this class owns the logic to compute its own subtotal.
 * Structural pattern (Value Object): immutable, compared by value not identity.
 */
public final class InvoiceLineItem {

    private final String orderLineId;
    private final String skuId;
    private final int quantity;
    private final double baseUnitPrice;
    private final double finalUnitPrice;
    private final List<String> appliedDiscounts;   // discount_breakdown
    private final double appliedDiscountPct;
    private final String couponCodeApplied;        // null if none

    private InvoiceLineItem(Builder builder) {
        this.orderLineId = builder.orderLineId;
        this.skuId = builder.skuId;
        this.quantity = builder.quantity;
        this.baseUnitPrice = builder.baseUnitPrice;
        this.finalUnitPrice = builder.finalUnitPrice;
        this.appliedDiscounts = Collections.unmodifiableList(builder.appliedDiscounts);
        this.appliedDiscountPct = builder.appliedDiscountPct;
        this.couponCodeApplied = builder.couponCodeApplied;
    }

    /** Computed line total = final_unit_price × quantity. */
    public double getLineTotal() {
        return finalUnitPrice * quantity;
    }

    /** Revenue impact of discounts on this line = (base - final) × quantity. */
    public double getRevenueDelta() {
        return (baseUnitPrice - finalUnitPrice) * quantity;
    }

    public String getOrderLineId()       { return orderLineId; }
    public String getSkuId()             { return skuId; }
    public int getQuantity()             { return quantity; }
    public double getBaseUnitPrice()     { return baseUnitPrice; }
    public double getFinalUnitPrice()    { return finalUnitPrice; }
    public List<String> getAppliedDiscounts() { return appliedDiscounts; }
    public double getAppliedDiscountPct() { return appliedDiscountPct; }
    public String getCouponCodeApplied() { return couponCodeApplied; }

    /**
     * Generates a human-readable receipt line for audit and display.
     * Part of the "active promo catalog, coupon codes, redemption count & revenue delta"
     * output listed in the Component Table for Component 5.
     */
    public String generateReceiptLine() {
        return String.format(
            "[%s] SKU=%s qty=%d base=%.2f final=%.2f disc=%.1f%% total=%.2f discounts=%s coupon=%s",
            orderLineId, skuId, quantity, baseUnitPrice, finalUnitPrice,
            appliedDiscountPct * 100, getLineTotal(),
            appliedDiscounts, couponCodeApplied == null ? "none" : couponCodeApplied
        );
    }

    // ── Builder (Creational Pattern) ────────────────────────────────────────────

    /** Creational pattern (Builder): constructs InvoiceLineItem safely with validation. */
    public static Builder builder(String orderLineId, String skuId) {
        return new Builder(orderLineId, skuId);
    }

    public static final class Builder {
        private final String orderLineId;
        private final String skuId;
        private int quantity = 1;
        private double baseUnitPrice;
        private double finalUnitPrice;
        private List<String> appliedDiscounts = Collections.emptyList();
        private double appliedDiscountPct;
        private String couponCodeApplied;

        private Builder(String orderLineId, String skuId) {
            this.orderLineId = requireNonBlank(orderLineId, "orderLineId");
            this.skuId = requireNonBlank(skuId, "skuId");
        }

        public Builder quantity(int quantity) {
            if (quantity <= 0) throw new IllegalArgumentException("quantity must be > 0");
            this.quantity = quantity;
            return this;
        }

        public Builder baseUnitPrice(double price) {
            requireFiniteNonNegative(price, "baseUnitPrice");
            this.baseUnitPrice = price;
            return this;
        }

        public Builder finalUnitPrice(double price) {
            requireFiniteNonNegative(price, "finalUnitPrice");
            this.finalUnitPrice = price;
            return this;
        }

        public Builder appliedDiscounts(List<String> discounts) {
            Objects.requireNonNull(discounts, "appliedDiscounts cannot be null");
            this.appliedDiscounts = new java.util.ArrayList<>(discounts);
            return this;
        }

        public Builder appliedDiscountPct(double pct) {
            if (!Double.isFinite(pct) || pct < 0 || pct > 1)
                throw new IllegalArgumentException("appliedDiscountPct must be between 0 and 1");
            this.appliedDiscountPct = pct;
            return this;
        }

        public Builder couponCodeApplied(String code) {
            this.couponCodeApplied = code; // null is valid (no coupon)
            return this;
        }

        public InvoiceLineItem build() {
            if (finalUnitPrice > baseUnitPrice)
                throw new IllegalStateException("finalUnitPrice cannot exceed baseUnitPrice");
            return new InvoiceLineItem(this);
        }

        private static String requireNonBlank(String v, String field) {
            Objects.requireNonNull(v, field + " cannot be null");
            if (v.trim().isEmpty()) throw new IllegalArgumentException(field + " cannot be blank");
            return v.trim();
        }

        private static void requireFiniteNonNegative(double v, String field) {
            if (!Double.isFinite(v) || v < 0)
                throw new IllegalArgumentException(field + " must be a non-negative finite number");
        }
    }
}