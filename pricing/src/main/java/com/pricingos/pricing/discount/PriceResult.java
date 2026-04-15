package com.pricingos.pricing.discount;

import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable value object representing the final pricing result after applying all discounts.
 * Contains the base price, discounted price, and a list of all applied discount names.
 */
public final class PriceResult {

    private final String skuId;
    private final double basePrice;
    private final double finalDiscountedPrice;
    private final String[] appliedDiscounts;
    private final boolean isApproved;

    /**
     * Constructs a price result with all pricing information.
     *
     * @param skuId                  product ID; must be non-blank
     * @param basePrice              price before any discounts; must be >= 0 and finite
     * @param finalDiscountedPrice   price after all discounts; must be >= 0 and finite
     * @param appliedDiscounts       names of all discount strategies applied (may be empty)
     * @param isApproved             whether this price has been approved (applicable for overrides)
     */
    public PriceResult(String skuId, double basePrice, double finalDiscountedPrice,
                       String[] appliedDiscounts, boolean isApproved) {
        this.skuId = requireNonBlank(skuId, "skuId");

        if (!Double.isFinite(basePrice) || basePrice < 0)
            throw new IllegalArgumentException("basePrice must be a non-negative finite number");
        this.basePrice = basePrice;

        if (!Double.isFinite(finalDiscountedPrice) || finalDiscountedPrice < 0)
            throw new IllegalArgumentException("finalDiscountedPrice must be a non-negative finite number");
        this.finalDiscountedPrice = finalDiscountedPrice;

        Objects.requireNonNull(appliedDiscounts, "appliedDiscounts cannot be null");
        this.appliedDiscounts = Arrays.copyOf(appliedDiscounts, appliedDiscounts.length);

        this.isApproved = isApproved;
    }

    // ── Domain logic ──────────────────────────────────────────────────────────────

    /**
     * Calculates the total discount percentage applied to the base price.
     *
     * @return discount percentage, or 0.0 if basePrice is 0
     */
    public double getDiscountPercentage() {
        if (basePrice == 0) return 0.0;
        return ((basePrice - finalDiscountedPrice) / basePrice) * 100.0;
    }

    /**
     * Generates a human-readable receipt string summarizing all applied discounts.
     *
     * @return formatted string showing base price, final price, discount percentage, and applied discount names
     */
    public String generateReceiptString() {
        StringBuilder sb = new StringBuilder();
        sb.append("SKU: ").append(skuId).append("\n");
        sb.append(String.format("  Base Price:     %.2f\n", basePrice));
        sb.append(String.format("  Final Price:    %.2f\n", finalDiscountedPrice));
        sb.append(String.format("  Discount:       %.2f%%\n", getDiscountPercentage()));

        if (appliedDiscounts.length > 0) {
            sb.append("  Discounts Applied:\n");
            for (String discount : appliedDiscounts) {
                sb.append("    - ").append(discount).append("\n");
            }
        } else {
            sb.append("  (No discounts applied)\n");
        }

        sb.append("  Status: ").append(isApproved ? "APPROVED" : "PENDING").append("\n");

        return sb.toString();
    }

    // ── Accessors ─────────────────────────────────────────────────────────────────

    public String getSkuId()                    { return skuId; }
    public double getBasePrice()                { return basePrice; }
    public double getFinalDiscountedPrice()     { return finalDiscountedPrice; }
    public String[] getAppliedDiscounts()       { return Arrays.copyOf(appliedDiscounts, appliedDiscounts.length); }
    public boolean isApproved()                 { return isApproved; }

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
        return String.format("PriceResult{sku=%s, base=%.2f, final=%.2f, discounts=%s}",
            skuId, basePrice, finalDiscountedPrice, Arrays.toString(appliedDiscounts));
    }
}
