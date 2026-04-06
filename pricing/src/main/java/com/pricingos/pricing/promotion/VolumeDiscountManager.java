package com.pricingos.pricing.promotion;

import com.pricingos.common.ISkuCatalogService;
import com.pricingos.common.IVolumeDiscountService;
import com.pricingos.common.VolumeTierRule;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Component 5 — Promotion &amp; Campaign Manager: Volume &amp; Tiered Discounts feature.
 *
 * <p>Registers per-SKU volume discount schedules and returns the correct discounted
 * unit price for a given quantity. Supports: "Automatically applies lower per-unit
 * prices as order quantities increase ($10/unit for 1–100 units, $8/unit for 101+)."
 *
 * <p>Design patterns:
 * <ul>
 *   <li><b>GRASP Controller</b>: single entry point for all volume discount queries.</li>
 *   <li><b>SOLID SRP</b>: only volume-tier logic lives here; coupon/bundle/rebate are separate.</li>
 *   <li><b>SOLID DIP</b>: validates SKUs via {@link ISkuCatalogService} interface.</li>
 * </ul>
 */
public class VolumeDiscountManager implements IVolumeDiscountService {

    /** Registry keyed by skuId for O(1) lookup during order line computation. */
    private final Map<String, VolumePricingPromotion> promoBySkuId = new ConcurrentHashMap<>();

    private final AtomicInteger idCounter = new AtomicInteger();
    private final ISkuCatalogService skuCatalogService;

    /** Production constructor. */
    public VolumeDiscountManager(ISkuCatalogService skuCatalogService) {
        this.skuCatalogService = Objects.requireNonNull(skuCatalogService, "skuCatalogService cannot be null");
    }

    // ── IVolumeDiscountService implementation ─────────────────────────────────────

    /**
     * {@inheritDoc}
     *
     * <p>Validates the SKU against the Inventory catalog, then delegates tier validation to
     * {@link VolumePricingPromotion} (which enforces contiguous, non-overlapping tiers with
     * exactly one unlimited top tier). One volume schedule is allowed per SKU; re-registering
     * a SKU replaces the previous schedule.
     */
    @Override
    public String createVolumePromotion(String skuId, List<VolumeTierRule> tiers) {
        Objects.requireNonNull(skuId, "skuId cannot be null");
        String normalized = skuId.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException("skuId cannot be blank");

        if (!skuCatalogService.isSkuActive(normalized))
            throw new IllegalArgumentException("SKU '" + normalized + "' is not active in the product catalog.");

        Objects.requireNonNull(tiers, "tiers cannot be null");
        if (tiers.isEmpty()) throw new IllegalArgumentException("tiers cannot be empty");

        String promoId = "VOL-" + idCounter.incrementAndGet();
        VolumePricingPromotion promo = new VolumePricingPromotion(promoId, normalized, tiers);
        promoBySkuId.put(normalized, promo);
        return promoId;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Returns {@code baseUnitPrice} unchanged if no volume schedule is registered for the SKU
     * or if no tier matches the quantity (defensive fallback).
     */
    @Override
    public double getDiscountedUnitPrice(String skuId, int quantity, double baseUnitPrice) {
        if (quantity < 1)
            throw new IllegalArgumentException("quantity must be >= 1, got: " + quantity);
        if (!Double.isFinite(baseUnitPrice) || baseUnitPrice < 0)
            throw new IllegalArgumentException("baseUnitPrice must be a non-negative finite number");

        VolumePricingPromotion promo = promoBySkuId.get(skuId.trim());
        if (promo == null) return baseUnitPrice; // No schedule → full price
        return promo.computeDiscountedUnitPrice(baseUnitPrice, quantity);
    }

    /** {@inheritDoc} */
    @Override
    public double getLineTotal(String skuId, int quantity, double baseUnitPrice) {
        return getDiscountedUnitPrice(skuId, quantity, baseUnitPrice) * quantity;
    }

    /** {@inheritDoc} */
    @Override
    public boolean hasVolumePromotion(String skuId) {
        return promoBySkuId.containsKey(skuId == null ? "" : skuId.trim());
    }

    // ── Package-private helpers for testing ──────────────────────────────────────

    /** Returns the VolumePricingPromotion for a given SKU (package-private for tests). */
    VolumePricingPromotion getPromoForSku(String skuId) {
        return promoBySkuId.get(skuId);
    }
}
