package com.pricingos.common;

import java.util.List;

/**
 * Volume &amp; Tiered Discount use-cases exposed to the rest of the subsystem.
 *
 * <p>Component 5 — Promotion &amp; Campaign Manager, Volume Discount feature.
 * Other teams (Discount Rules Engine — Component 4, Order Fulfillment) call this
 * interface rather than coupling to the concrete {@code VolumeDiscountManager} — SOLID DIP.
 *
 * <p>Supports the spec requirement: "Automatically applies lower per-unit prices as
 * order quantities increase (e.g., $10/unit for 1–100 units, $8/unit for 101+)."
 */
public interface IVolumeDiscountService {

    /**
     * Registers a volume-based discount schedule for a SKU and returns the generated promo_id.
     *
     * <p>Tiers must be non-overlapping and collectively cover a contiguous range starting at 1.
     * Exactly one tier should have {@code maxQty == 0} to serve as the open-ended top tier.
     *
     * @param skuId the SKU this volume schedule applies to
     * @param tiers ordered list of {@link VolumeTierRule}s defining the discount bands
     * @return generated promotion ID for the volume schedule
     * @throws IllegalArgumentException if skuId is invalid or tiers are malformed/overlapping
     */
    String createVolumePromotion(String skuId, List<VolumeTierRule> tiers);

    /**
     * Returns the discounted unit price for a given SKU and order quantity.
     *
     * <p>If no volume promotion exists for the SKU, returns {@code baseUnitPrice} unchanged.
     *
     * @param skuId         the product being ordered
     * @param quantity      the number of units in the order line
     * @param baseUnitPrice the catalogue price per unit before tier discounts
     * @return the net unit price after applying the applicable tier discount
     */
    double getDiscountedUnitPrice(String skuId, int quantity, double baseUnitPrice);

    /**
     * Convenience method: returns {@code getDiscountedUnitPrice(...) × quantity}.
     *
     * @param skuId         the product being ordered
     * @param quantity      the number of units
     * @param baseUnitPrice the catalogue price per unit before tier discounts
     * @return the total line amount after volume discounts
     */
    double getLineTotal(String skuId, int quantity, double baseUnitPrice);

    /**
     * Returns {@code true} if a volume promotion schedule exists for the given SKU.
     *
     * @param skuId the SKU to check
     * @return true if a volume schedule has been registered for this SKU
     */
    boolean hasVolumePromotion(String skuId);
}
