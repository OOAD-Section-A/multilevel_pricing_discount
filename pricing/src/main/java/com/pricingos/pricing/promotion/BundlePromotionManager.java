package com.pricingos.pricing.promotion;

import com.pricingos.common.IBundlePromotionService;
import com.pricingos.common.ISkuCatalogService;

import java.time.Clock;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Component 5 — Promotion &amp; Campaign Manager: Bundled Pricing feature.
 *
 * <p>Manages bundle promotions where a discount applies when ALL required SKUs
 * appear together in the customer's cart (e.g., "Buy Printer + 3 Ink Cartridges = 15% off").
 *
 * <p>Design patterns applied:
 * <ul>
 *   <li><b>Creational (Builder)</b>: delegates to {@link BundledPromotion.Builder} for safe
 *       domain-object construction.</li>
 *   <li><b>GRASP Controller</b>: this class coordinates bundle use-cases on behalf of clients.</li>
 *   <li><b>SOLID SRP</b>: only bundle lifecycle logic lives here.</li>
 *   <li><b>SOLID DIP</b>: depends on {@link ISkuCatalogService} interface, not the Inventory
 *       team's concrete class.</li>
 * </ul>
 */
public class BundlePromotionManager implements IBundlePromotionService {

    private final Map<String, BundledPromotion> promoById = new ConcurrentHashMap<>();
    private final AtomicInteger idCounter = new AtomicInteger();
    private final ISkuCatalogService skuCatalogService;
    private final Clock clock;

    /** Production constructor — uses the system default clock. */
    public BundlePromotionManager(ISkuCatalogService skuCatalogService) {
        this(skuCatalogService, Clock.systemDefaultZone());
    }

    /** Testing constructor — accepts an explicit clock for deterministic date logic. */
    BundlePromotionManager(ISkuCatalogService skuCatalogService, Clock clock) {
        this.skuCatalogService = Objects.requireNonNull(skuCatalogService, "skuCatalogService cannot be null");
        this.clock             = Objects.requireNonNull(clock, "clock cannot be null");
    }

    // ── IBundlePromotionService implementation ────────────────────────────────────

    /**
     * {@inheritDoc}
     *
     * <p>Validates every SKU in {@code bundleSkuIds} against the Inventory catalog before
     * creating the promotion — consistent with {@link PromotionManager#createPromotion}.
     */
    @Override
    public String createBundlePromotion(String name, List<String> bundleSkuIds,
                                        double discountPct,
                                        LocalDate startDate, LocalDate endDate) {
        requireNonBlank(name, "name");
        Objects.requireNonNull(bundleSkuIds, "bundleSkuIds cannot be null");
        if (bundleSkuIds.isEmpty())
            throw new IllegalArgumentException("bundleSkuIds cannot be empty");

        // Validate and normalise SKU IDs (SOLID DIP — boundary with Inventory team).
        Set<String> normalizedSkus = new HashSet<>();
        for (String sku : bundleSkuIds) {
            if (sku == null) throw new IllegalArgumentException("bundleSkuIds contains a null entry");
            String trimmed = sku.trim();
            if (trimmed.isEmpty()) throw new IllegalArgumentException("bundleSkuIds contains a blank entry");
            if (!skuCatalogService.isSkuActive(trimmed))
                throw new IllegalArgumentException("SKU '" + trimmed + "' is not active in the product catalog.");
            normalizedSkus.add(trimmed);
        }

        String promoId = "BNDL-" + idCounter.incrementAndGet();

        BundledPromotion promo = BundledPromotion.builder(promoId)
            .name(name)
            .bundleSkuIds(normalizedSkus)
            .discountPct(discountPct)
            .startDate(startDate)
            .endDate(endDate)
            .build();

        promoById.put(promoId, promo);
        return promoId;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Iterates all registered bundle promotions and returns the highest applicable
     * discount amount (best-deal-for-customer policy). Returns {@code 0.0} if no bundle
     * applies to the given cart.
     */
    @Override
    public double getBestBundleDiscount(List<String> cartSkuIds, double cartTotal) {
        Objects.requireNonNull(cartSkuIds, "cartSkuIds cannot be null");
        if (!Double.isFinite(cartTotal) || cartTotal < 0)
            throw new IllegalArgumentException("cartTotal must be a non-negative finite number");

        LocalDate today = LocalDate.now(clock);
        double best = 0.0;

        for (BundledPromotion promo : promoById.values()) {
            if (promo.isApplicableTo(cartSkuIds, today)) {
                double discount = promo.computeDiscount(cartTotal);
                if (discount > best) best = discount;
            }
        }
        return best;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Expires promotions whose end_date has passed before returning the active list —
     * same expiry-on-demand pattern used in {@link PromotionManager}.
     */
    @Override
    public List<String> getActiveBundlePromotions() {
        LocalDate today = LocalDate.now(clock);
        return promoById.values().stream()
            .filter(p -> !p.isExpired())
            .filter(p -> !today.isBefore(p.getStartDate()) && !today.isAfter(p.getEndDate()))
            .map(BundledPromotion::getPromoId)
            .sorted()
            .collect(Collectors.toList());
    }

    // ── Package-private helpers for testing ──────────────────────────────────────

    /** Returns the BundledPromotion object for a given promo ID (package-private for tests). */
    BundledPromotion getPromotionById(String promoId) {
        return promoById.get(promoId);
    }

    // ── Utilities ─────────────────────────────────────────────────────────────────

    private static String requireNonBlank(String v, String field) {
        Objects.requireNonNull(v, field + " cannot be null");
        if (v.trim().isEmpty()) throw new IllegalArgumentException(field + " cannot be blank");
        return v;
    }
}
