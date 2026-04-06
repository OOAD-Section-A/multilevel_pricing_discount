package com.pricingos.common;

import java.time.LocalDate;
import java.util.List;

/**
 * Bundled Pricing use-cases exposed to the rest of the subsystem.
 *
 * <p>Component 5 — Promotion &amp; Campaign Manager, Bundled Pricing feature.
 * Supports the spec requirement: "Manages discounts for specific product combinations
 * (e.g., Buy a Printer and 3 Ink Cartridges, get 15% off)."
 *
 * <p>Other teams (Discount Rules Engine — Component 4, Order Fulfillment) call this
 * interface rather than coupling to the concrete {@code BundlePromotionManager} — SOLID DIP.
 */
public interface IBundlePromotionService {

    /**
     * Registers a new bundle promotion and returns the generated bundle_promo_id.
     *
     * <p>The discount applies when ALL SKUs in {@code bundleSkuIds} are present in the cart.
     * Missing even one SKU means the bundle discount does not trigger.
     *
     * @param name          human-readable campaign name (e.g., "Printer + Ink Bundle")
     * @param bundleSkuIds  all SKU IDs that must be present together in the cart
     * @param discountPct   percentage discount applied to the total cart value for the bundle
     * @param startDate     first date the bundle is valid
     * @param endDate       last date the bundle is valid (inclusive)
     * @return generated bundle promotion ID
     * @throws IllegalArgumentException if any SKU is inactive, list is empty, or dates are invalid
     */
    String createBundlePromotion(String name, List<String> bundleSkuIds,
                                 double discountPct, LocalDate startDate, LocalDate endDate);

    /**
     * Checks whether any active bundle promotion applies to the given cart, and if so,
     * returns the highest applicable discount amount.
     *
     * <p>If multiple bundles are applicable (cart contains SKUs from multiple bundle sets),
     * returns the largest discount (best-deal-for-customer policy).
     *
     * @param cartSkuIds  SKU IDs present in the current cart (may contain duplicates — treated as a set)
     * @param cartTotal   cart subtotal before any bundle discount is applied
     * @return the discount amount to deduct, or {@code 0.0} if no bundle applies
     */
    double getBestBundleDiscount(List<String> cartSkuIds, double cartTotal);

    /**
     * Returns the IDs of all currently active bundle promotions.
     * Active means: not expired and within the start/end date window.
     */
    List<String> getActiveBundlePromotions();
}
