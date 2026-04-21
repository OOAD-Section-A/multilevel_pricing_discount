package com.pricingos.pricing.promotion;

import com.pricingos.common.DiscountType;
import com.pricingos.common.IPromotionService;
import com.pricingos.common.ISkuCatalogService;
import com.pricingos.common.ValidationUtils;
import com.pricingos.pricing.promotion.InvalidPromoCodeException.Reason;
import com.jackfruit.scm.database.adapter.PricingAdapter;
import com.jackfruit.scm.database.model.PricingModels;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class PromotionManager implements IPromotionService {

    private static final double MAX_PERCENTAGE_VALUE = 100.0;

    private final ISkuCatalogService skuCatalogService;
    private final Clock clock;
    private final PricingAdapter pricingAdapter;

    public PromotionManager(ISkuCatalogService skuCatalogService, PricingAdapter pricingAdapter) {
        this(skuCatalogService, pricingAdapter, Clock.systemDefaultZone());
    }

    PromotionManager(ISkuCatalogService skuCatalogService, PricingAdapter pricingAdapter, Clock clock) {
        this.skuCatalogService = Objects.requireNonNull(skuCatalogService, "skuCatalogService cannot be null");
        this.pricingAdapter = Objects.requireNonNull(pricingAdapter, "pricingAdapter cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
    }

    @Override
    public String createPromotion(String name, String couponCode, DiscountType discountType,
                                  double discountValue, LocalDate startDate, LocalDate endDate,
                                  List<String> eligibleSkuIds, double minCartValue, int maxUses) {

        String normalizedCode = ValidationUtils.requireNonBlank(couponCode, "couponCode").toUpperCase();
        List<String> normalizedSkuIds = normalizeAndValidateSkus(eligibleSkuIds);
        if (discountType == DiscountType.PERCENTAGE_OFF && discountValue > MAX_PERCENTAGE_VALUE) {
            throw new IllegalArgumentException("PERCENTAGE_OFF discount cannot exceed 100%.");
        }

        String promoId = "PROMO-" + java.util.UUID.randomUUID().toString();
        
        // Create promotion record for database
        PricingModels.Promotion promo = new PricingModels.Promotion(
            promoId,
            name,
            normalizedCode,
            discountType.name(),
            BigDecimal.valueOf(discountValue),
            LocalDateTime.now(),  // startDate as LocalDateTime
            LocalDateTime.now(),  // endDate as LocalDateTime
            String.join(",", normalizedSkuIds),
            BigDecimal.valueOf(minCartValue),
            maxUses,
            0  // currentUseCount
        );

        // Check for duplicate coupon code in database
        Optional<PricingModels.Promotion> existing = pricingAdapter.getPromotionByCouponCode(normalizedCode);
        if (existing.isPresent()) {
            throw new IllegalArgumentException("Coupon code '" + normalizedCode + "' already exists.");
        }

        // Persist to database
        pricingAdapter.createPromotion(promo);
        return promoId;
    }

    @Override
    public double validateAndGetDiscount(String couponCode, String skuId, double cartTotal) {
        String normalizedCode = ValidationUtils.requireNonBlank(couponCode, "couponCode").toUpperCase();
        String normalizedSkuId = ValidationUtils.requireNonBlank(skuId, "skuId");
        if (!Double.isFinite(cartTotal) || cartTotal < 0) {
            throw new IllegalArgumentException("cartTotal must be a non-negative finite number");
        }

        // Load from database
        Optional<PricingModels.Promotion> promo = pricingAdapter.getPromotionByCouponCode(normalizedCode);
        if (promo.isEmpty()) {
            throw new InvalidPromoCodeException(normalizedCode, Reason.NOT_FOUND);
        }

        PricingModels.Promotion promotion = promo.get();
        LocalDateTime now = LocalDateTime.now(clock);
        
        if (now.isBefore(promotion.startDate())) {
            throw new InvalidPromoCodeException(normalizedCode, Reason.NOT_YET_ACTIVE);
        }
        if (now.isAfter(promotion.endDate()) || promotion.expired()) {
            throw new InvalidPromoCodeException(normalizedCode, Reason.EXPIRED);
        }
        
        List<String> eligibleSkus = List.of(promotion.eligibleSkuIds().split(","));
        if (!eligibleSkus.contains(normalizedSkuId)) {
            throw new InvalidPromoCodeException(normalizedCode, Reason.SKU_NOT_ELIGIBLE);
        }
        if (cartTotal < promotion.minCartValue().doubleValue()) {
            throw new InvalidPromoCodeException(normalizedCode, Reason.CART_VALUE_TOO_LOW);
        }
        if (promotion.maxUses() > 0 && promotion.currentUseCount() >= promotion.maxUses()) {
            throw new InvalidPromoCodeException(normalizedCode, Reason.MAX_USES_REACHED);
        }

        return computeDiscountAmount(promotion.discountType(), promotion.discountValue().doubleValue(), cartTotal);
    }

    @Override
    public void recordRedemption(String couponCode) {
        String normalizedCode = ValidationUtils.requireNonBlank(couponCode, "couponCode").toUpperCase();
        Optional<PricingModels.Promotion> promo = pricingAdapter.getPromotionByCouponCode(normalizedCode);
        if (promo.isEmpty()) {
            throw new IllegalArgumentException("No promotion found for coupon code: " + normalizedCode);
        }

        PricingModels.Promotion promotion = promo.get();
        int newCount = promotion.currentUseCount() + 1;
        
        // Update use count in database
        pricingAdapter.updatePromotionUseCount(promotion.promoId(), newCount);
    }

    @Override
    public List<String> getActivePromoCodes() {
        LocalDateTime now = LocalDateTime.now(clock);
        return pricingAdapter.listActivePromotions().stream()
            .filter(p -> !p.expired())
            .filter(p -> !now.isBefore(p.startDate()) && !now.isAfter(p.endDate()))
            .filter(p -> p.maxUses() == 0 || p.currentUseCount() < p.maxUses())
            .map(PricingModels.Promotion::couponCode)
            .sorted()
            .collect(Collectors.toList());
    }

    @Override
    public int getRedemptionCount(String couponCode) {
        String normalizedCode = ValidationUtils.requireNonBlank(couponCode, "couponCode").toUpperCase();
        Optional<PricingModels.Promotion> promo = pricingAdapter.getPromotionByCouponCode(normalizedCode);
        if (promo.isEmpty()) {
            throw new IllegalArgumentException("No promotion found for: " + normalizedCode);
        }
        return promo.get().currentUseCount();
    }

    @Override
    public void expireStalePromotions() {
        LocalDateTime now = LocalDateTime.now(clock);
        pricingAdapter.listActivePromotions().forEach(promo -> {
            if (now.isAfter(promo.endDate()) && !promo.expired()) {
                // Mark as expired in database
                pricingAdapter.updatePromotionExpired(promo.promoId(), true);
            }
        });
    }

    private List<String> normalizeAndValidateSkus(List<String> eligibleSkuIds) {
        Objects.requireNonNull(eligibleSkuIds, "eligibleSkuIds cannot be null");
        List<String> normalizedSkuIds = new ArrayList<>();
        for (String skuId : eligibleSkuIds) {
            if (skuId == null) {
                throw new IllegalArgumentException("eligibleSkuIds contains a null entry");
            }
            String trimmedSku = skuId.trim();
            if (trimmedSku.isEmpty()) {
                throw new IllegalArgumentException("eligibleSkuIds contains a blank entry");
            }
            if (!skuCatalogService.isSkuActive(trimmedSku)) {
                throw new IllegalArgumentException("SKU '" + trimmedSku + "' is not active in the product catalog.");
            }
            normalizedSkuIds.add(trimmedSku);
        }
        return normalizedSkuIds;
    }

    private static double computeDiscountAmount(String discountType, double discountValue, double lineSubtotal) {
        return switch (DiscountType.valueOf(discountType)) {
            case PERCENTAGE_OFF -> lineSubtotal * (discountValue / 100.0);
            case FIXED_AMOUNT -> Math.min(discountValue, lineSubtotal);
            case BUY_X_GET_Y -> lineSubtotal * (discountValue / 100.0);
        };
    }
}
