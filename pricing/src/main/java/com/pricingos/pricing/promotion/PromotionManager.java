package com.pricingos.pricing.promotion;

import com.pricingos.common.DiscountType;
import com.pricingos.common.IPromotionService;
import com.pricingos.common.ISkuCatalogService;
import com.pricingos.pricing.promotion.InvalidPromoCodeException.Reason;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Component 5 — Promotion & Campaign Manager.
 *
 * <p>Responsibilities (from the Component Table):
 * <ul>
 *   <li>Creates and tracks time-bound promotions, seasonal deals, and coupon codes.</li>
 *   <li>Validates coupon codes against eligibility rules.</li>
 *   <li>Maintains redemption counts and revenue delta data.</li>
 *   <li>Expires stale promotions (intended for scheduled job invocation).</li>
 * </ul>
 *
 * <p>Design patterns applied:
 * <ul>
 *   <li><b>Creational (Builder)</b>: {@link Promotion.Builder} constructs Promotion objects safely.</li>
 *   <li><b>GRASP Controller</b>: this class coordinates promotion use-cases on behalf of clients.</li>
 *   <li><b>SOLID SRP</b>: only promotion lifecycle logic lives here; approval is in {@link com.pricingos.pricing.approval.ApprovalWorkflowEngine}.</li>
 *   <li><b>SOLID DIP</b>: depends on {@link ISkuCatalogService} interface, not on the Inventory team's concrete class.</li>
 * </ul>
 *
 * <p>Exception handling:
 * <ul>
 *   <li>INVALID_PROMO_CODE (MINOR): throws {@link InvalidPromoCodeException} with a machine-readable
 *       reason code; the UI layer allows 3 re-attempts before disabling the promo input field.</li>
 * </ul>
 */
public class PromotionManager implements IPromotionService {

    /** Registry keyed by coupon_code for O(1) lookup during checkout validation. */
    private final Map<String, Promotion> couponRegistry = new ConcurrentHashMap<>();

    /** Secondary registry keyed by promo_id for management operations. */
    private final Map<String, Promotion> promoById = new ConcurrentHashMap<>();

    private final AtomicInteger idCounter = new AtomicInteger();

    /**
     * Interface to the Inventory subsystem (Team "Better Call Objects") — SOLID DIP.
     * Used to validate that eligible_sku_ids actually exist in the catalog.
     */
    private final ISkuCatalogService skuCatalogService;

    /**
     * Maximum value percentage cap for promotions — guards against obviously wrong inputs.
     * Promotions above 100% off do not make business sense.
     */
    private static final double MAX_PERCENTAGE_VALUE = 100.0;

    public PromotionManager(ISkuCatalogService skuCatalogService) {
        this.skuCatalogService = Objects.requireNonNull(skuCatalogService, "skuCatalogService cannot be null");
    }

    // ── IPromotionService implementation ─────────────────────────────────────────

    /**
     * {@inheritDoc}
     *
     * <p>Validates that all eligible_sku_ids exist in the Inventory catalog before creating
     * the promotion. Coupon codes must be unique across the active promo catalog.
     */
    @Override
    public String createPromotion(String name, String couponCode, DiscountType discountType,
                                  double discountValue, LocalDate startDate, LocalDate endDate,
                                  List<String> eligibleSkuIds, double minCartValue, int maxUses) {

        String normalizedCode = requireNonBlank(couponCode, "couponCode").toUpperCase();

        // Validate all SKUs exist in the Inventory catalog (boundary check — SOLID DIP).
        // Done first because it is the expensive external call; the uniqueness check is atomic below.
        Objects.requireNonNull(eligibleSkuIds, "eligibleSkuIds cannot be null");
        for (String skuId : eligibleSkuIds) {
            if (!skuCatalogService.isSkuActive(skuId)) {
                throw new IllegalArgumentException("SKU '" + skuId + "' is not active in the product catalog.");
            }
        }

        // PERCENTAGE_OFF: guard against values > 100% which would produce negative prices.
        if (discountType == DiscountType.PERCENTAGE_OFF && discountValue > MAX_PERCENTAGE_VALUE) {
            throw new IllegalArgumentException("PERCENTAGE_OFF discount cannot exceed 100%.");
        }

        String promoId = "PROMO-" + idCounter.incrementAndGet();

        Promotion promo = Promotion.builder(promoId)
            .promoName(name)
            .couponCode(normalizedCode)
            .discountType(discountType)
            .discountValue(discountValue)
            .startDate(startDate)
            .endDate(endDate)
            .eligibleSkuIds(eligibleSkuIds)
            .minCartValue(minCartValue)
            .maxUses(maxUses)
            .build();

        // promoId is guaranteed unique (AtomicInteger), so this put is always safe.
        promoById.put(promoId, promo);

        // Atomic uniqueness guarantee: putIfAbsent returns the existing value if the key was
        // already present, or null if this thread won the insert. No two threads can both "win".
        Promotion existing = couponRegistry.putIfAbsent(normalizedCode, promo);
        if (existing != null) {
            promoById.remove(promoId); // rollback — keep maps consistent
            throw new IllegalArgumentException("Coupon code '" + normalizedCode + "' already exists.");
        }

        return promoId;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Implements INVALID_PROMO_CODE exception handling (MINOR).
     * Throws {@link InvalidPromoCodeException} with a specific {@link Reason} so the UI
     * can display a user-friendly message and track re-attempt count.
     */
    @Override
    public double validateAndGetDiscount(String couponCode, String skuId, double cartTotal) {
        String normalizedCode = requireNonBlank(couponCode, "couponCode").toUpperCase();
        requireNonBlank(skuId, "skuId");

        Promotion promo = couponRegistry.get(normalizedCode);

        // ── Exception: code not found ──────────────────────────────────────────
        if (promo == null) {
            throw new InvalidPromoCodeException(normalizedCode, Reason.NOT_FOUND);
        }

        // ── Exception: code is expired ─────────────────────────────────────────
        LocalDate today = LocalDate.now();
        if (promo.isExpiredOn(today) || promo.isExpired()) {
            throw new InvalidPromoCodeException(normalizedCode, Reason.EXPIRED);
        }

        // ── Exception: SKU not in eligible list ────────────────────────────────
        if (!promo.getEligibleSkuIds().contains(skuId.trim())) {
            throw new InvalidPromoCodeException(normalizedCode, Reason.SKU_NOT_ELIGIBLE);
        }

        // ── Exception: cart total below minimum ────────────────────────────────
        if (cartTotal < promo.getMinCartValue()) {
            throw new InvalidPromoCodeException(normalizedCode, Reason.CART_VALUE_TOO_LOW);
        }

        // ── Exception: max uses reached ────────────────────────────────────────
        if (promo.getMaxUses() > 0 && promo.getCurrentUseCount() >= promo.getMaxUses()) {
            throw new InvalidPromoCodeException(normalizedCode, Reason.MAX_USES_REACHED);
        }

        // All checks passed — return the computed discount amount.
        return promo.computeDiscountAmount(cartTotal);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Must only be called after order confirmation, not during cart preview.
     */
    @Override
    public void recordRedemption(String couponCode) {
        String normalizedCode = requireNonBlank(couponCode, "couponCode").toUpperCase();
        Promotion promo = couponRegistry.get(normalizedCode);
        if (promo == null) {
            throw new IllegalArgumentException("No promotion found for coupon code: " + normalizedCode);
        }
        promo.recordRedemption();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Returns only codes whose date window is currently active and whose max_uses
     * has not been reached — the "active promo catalog" output from the Component Table.
     */
    @Override
    public List<String> getActivePromoCodes() {
        LocalDate today = LocalDate.now();
        return couponRegistry.values().stream()
            .filter(p -> p.isApplicableTo(
                p.getEligibleSkuIds().isEmpty() ? "" : p.getEligibleSkuIds().get(0),
                Double.MAX_VALUE, // ignore cart value for catalog listing
                today))
            // We want all codes, not just ones matching a single SKU, so
            // re-filter just on date and redemption count here:
            .filter(p -> !p.isExpiredOn(today) && !p.isExpired())
            .filter(p -> p.getMaxUses() == 0 || p.getCurrentUseCount() < p.getMaxUses())
            .map(Promotion::getCouponCode)
            .sorted()
            .collect(Collectors.toList());
    }

    /** {@inheritDoc} */
    @Override
    public int getRedemptionCount(String couponCode) {
        String normalizedCode = requireNonBlank(couponCode, "couponCode").toUpperCase();
        Promotion promo = couponRegistry.get(normalizedCode);
        if (promo == null) throw new IllegalArgumentException("No promotion found for: " + normalizedCode);
        return promo.getCurrentUseCount();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Marks as expired any promotion whose end_date is before today.
     * In production this is called by a scheduled cron job (e.g., nightly at midnight).
     */
    @Override
    public void expireStalePromotions() {
        LocalDate today = LocalDate.now();
        couponRegistry.values().forEach(promo -> {
            if (promo.isExpiredOn(today)) {
                promo.markExpired();
            }
        });
    }

    // ── Package-private helpers for testing ──────────────────────────────────────

    /**
     * Returns the Promotion object for a given promo_id (package-private for tests).
     */
    Promotion getPromotionById(String promoId) {
        return promoById.get(promoId);
    }

    // ── Utilities ─────────────────────────────────────────────────────────────────

    private static String requireNonBlank(String v, String field) {
        Objects.requireNonNull(v, field + " cannot be null");
        if (v.trim().isEmpty()) throw new IllegalArgumentException(field + " cannot be blank");
        return v;
    }
}