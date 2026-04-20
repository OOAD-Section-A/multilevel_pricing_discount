package com.pricingos.pricing.promotion;

import com.pricingos.common.DiscountType;
import com.pricingos.common.IPromotionService;
import com.pricingos.common.ISkuCatalogService;
import com.pricingos.common.ValidationUtils;
import com.pricingos.pricing.promotion.InvalidPromoCodeException.Reason;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import com.pricingos.pricing.db.DaoBulk.PromoDao;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class PromotionManager implements IPromotionService {

    private static final double MAX_PERCENTAGE_VALUE = 100.0;

    private final AtomicInteger idCounter = new AtomicInteger();
    
    private final ISkuCatalogService skuCatalogService;
    private final Clock clock;

    public PromotionManager(ISkuCatalogService skuCatalogService) {
        this(skuCatalogService, Clock.systemDefaultZone());
    }

    PromotionManager(ISkuCatalogService skuCatalogService, Clock clock) {
        this.skuCatalogService = Objects.requireNonNull(skuCatalogService, "skuCatalogService cannot be null");
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
        PromotionState promo = new PromotionState(
            promoId,
            name,
            normalizedCode,
            discountType,
            discountValue,
            startDate,
            endDate,
            normalizedSkuIds,
            minCartValue,
            maxUses
        );

        if (PromoDao.getByCode(normalizedCode, PromotionState.class) != null) {
            throw new IllegalArgumentException("Coupon code '" + normalizedCode + "' already exists.");
        }
        PromoDao.save(promo);
        return promoId;
    }

    @Override
    public double validateAndGetDiscount(String couponCode, String skuId, double cartTotal) {
        String normalizedCode = ValidationUtils.requireNonBlank(couponCode, "couponCode").toUpperCase();
        String normalizedSkuId = ValidationUtils.requireNonBlank(skuId, "skuId");
        if (!Double.isFinite(cartTotal) || cartTotal < 0) {
            throw new IllegalArgumentException("cartTotal must be a non-negative finite number");
        }

        PromotionState promo = findByCouponCode(normalizedCode);
        if (promo == null) {
            throw new InvalidPromoCodeException(normalizedCode, Reason.NOT_FOUND);
        }

        LocalDate today = LocalDate.now(clock);
        if (today.isBefore(promo.startDate())) {
            throw new InvalidPromoCodeException(normalizedCode, Reason.NOT_YET_ACTIVE);
        }
        if (promo.isExpiredOn(today) || promo.expired()) {
            throw new InvalidPromoCodeException(normalizedCode, Reason.EXPIRED);
        }
        if (!promo.eligibleSkuIds().contains(normalizedSkuId)) {
            throw new InvalidPromoCodeException(normalizedCode, Reason.SKU_NOT_ELIGIBLE);
        }
        if (cartTotal < promo.minCartValue()) {
            throw new InvalidPromoCodeException(normalizedCode, Reason.CART_VALUE_TOO_LOW);
        }
        if (promo.maxUses() > 0 && promo.currentUseCount() >= promo.maxUses()) {
            throw new InvalidPromoCodeException(normalizedCode, Reason.MAX_USES_REACHED);
        }
        return promo.computeDiscountAmount(cartTotal);
    }

    @Override
    public void recordRedemption(String couponCode) {
        String normalizedCode = ValidationUtils.requireNonBlank(couponCode, "couponCode").toUpperCase();
        PromotionState promo = findByCouponCode(normalizedCode);
        if (promo == null) {
            throw new IllegalArgumentException("No promotion found for coupon code: " + normalizedCode);
        }
        promo.recordRedemption();
        PromoDao.save(promo);
    }

    @Override
    public List<String> getActivePromoCodes() {
        LocalDate today = LocalDate.now(clock);
        return ((java.util.List<PromotionState>)(java.util.List)PromoDao.findAll(PromotionState.class)).stream()
            .filter(p -> !p.expired())
            .filter(p -> !today.isBefore(p.startDate()) && !today.isAfter(p.endDate()))
            .filter(p -> p.maxUses() == 0 || p.currentUseCount() < p.maxUses())
            .map(PromotionState::couponCode)
            .sorted()
            .collect(Collectors.toList());
    }

    @Override
    public int getRedemptionCount(String couponCode) {
        String normalizedCode = ValidationUtils.requireNonBlank(couponCode, "couponCode").toUpperCase();
        PromotionState promo = findByCouponCode(normalizedCode);
        if (promo == null) {
            throw new IllegalArgumentException("No promotion found for: " + normalizedCode);
        }
        return promo.currentUseCount();
    }

    @Override
    public void expireStalePromotions() {
        LocalDate today = LocalDate.now(clock);
        ((java.util.List<PromotionState>)(java.util.List)PromoDao.findAll(PromotionState.class)).forEach(promo -> {
            if (promo.isExpiredOn(today)) {
                promo.markExpired();
                PromoDao.save(promo);
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

    private PromotionState findByCouponCode(String normalizedCouponCode) {
        return (PromotionState) PromoDao.getByCode(normalizedCouponCode, PromotionState.class);
    }

    private static final class PromotionState {
        private final String promoId;
        private final String name;
        private final String couponCode;
        private final DiscountType discountType;
        private final double discountValue;
        private final LocalDate startDate;
        private final LocalDate endDate;
        private final List<String> eligibleSkuIds;
        private final double minCartValue;
        private final int maxUses;
        private int currentUseCount;
        private boolean expired;

        private PromotionState(String promoId,
                               String promoName,
                               String couponCode,
                               DiscountType discountType,
                               double discountValue,
                               LocalDate startDate,
                               LocalDate endDate,
                               List<String> eligibleSkuIds,
                               double minCartValue,
                               int maxUses) {
            this.promoId = ValidationUtils.requireNonBlank(promoId, "promoId");
            this.name = ValidationUtils.requireNonBlank(promoName, "promoName");
            this.couponCode = ValidationUtils.requireNonBlank(couponCode, "couponCode");
            this.discountType = Objects.requireNonNull(discountType, "discountType cannot be null");
            if (!Double.isFinite(discountValue) || discountValue <= 0) {
                throw new IllegalArgumentException("discountValue must be > 0");
            }
            this.discountValue = discountValue;
            this.startDate = Objects.requireNonNull(startDate, "startDate cannot be null");
            this.endDate = Objects.requireNonNull(endDate, "endDate cannot be null");
            if (endDate.isBefore(startDate)) {
                throw new IllegalArgumentException("endDate cannot be before startDate");
            }
            Objects.requireNonNull(eligibleSkuIds, "eligibleSkuIds cannot be null");
            if (eligibleSkuIds.isEmpty()) {
                throw new IllegalArgumentException("eligibleSkuIds cannot be empty");
            }
            this.eligibleSkuIds = Collections.unmodifiableList(new ArrayList<>(eligibleSkuIds));
            if (!Double.isFinite(minCartValue) || minCartValue < 0) {
                throw new IllegalArgumentException("minCartValue must be a non-negative finite number");
            }
            if (maxUses < 0) {
                throw new IllegalArgumentException("maxUses cannot be negative");
            }
            this.minCartValue = minCartValue;
            this.maxUses = maxUses;
            this.currentUseCount = 0;
            this.expired = false;
        }

        private synchronized double computeDiscountAmount(double lineSubtotal) {
            return switch (discountType) {
                case PERCENTAGE_OFF -> lineSubtotal * (discountValue / 100.0);
                case FIXED_AMOUNT -> Math.min(discountValue, lineSubtotal);
                case BUY_X_GET_Y -> lineSubtotal * (discountValue / 100.0);
            };
        }

        private synchronized void recordRedemption() {
            if (maxUses > 0 && currentUseCount >= maxUses) {
                throw new IllegalStateException("Promotion " + promoId + " has reached its maximum number of uses (" + maxUses + ").");
            }
            currentUseCount++;
        }

        private synchronized void markExpired() {
            this.expired = true;
        }

        private boolean isExpiredOn(LocalDate today) {
            return today.isAfter(endDate);
        }

        private String couponCode() {
            return couponCode;
        }

        private LocalDate startDate() {
            return startDate;
        }

        private LocalDate endDate() {
            return endDate;
        }

        private List<String> eligibleSkuIds() {
            return eligibleSkuIds;
        }

        private double minCartValue() {
            return minCartValue;
        }

        private int maxUses() {
            return maxUses;
        }

        private synchronized int currentUseCount() {
            return currentUseCount;
        }

        private synchronized boolean expired() {
            return expired;
        }
    }
}
