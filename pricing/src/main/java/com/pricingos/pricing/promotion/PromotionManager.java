package com.pricingos.pricing.promotion;

import com.jackfruit.scm.database.adapter.PackagingAdapter;
import com.jackfruit.scm.database.adapter.PricingAdapter;
import com.jackfruit.scm.database.model.PackagingModels.PackagingPromotion;
import com.jackfruit.scm.database.model.PackagingModels.PromotionEligibleSku;
import com.jackfruit.scm.database.model.PricingModels.Promotion;
import com.pricingos.common.DiscountType;
import com.pricingos.common.IPromotionService;
import com.pricingos.common.ISkuCatalogService;
import com.pricingos.common.ValidationUtils;
import com.pricingos.pricing.db.DatabaseModuleSupport;
import com.pricingos.pricing.promotion.InvalidPromoCodeException.Reason;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class PromotionManager implements IPromotionService {

    private static final double MAX_PERCENTAGE_VALUE = 100.0;
    private static final int UNLIMITED_MAX_USES = Integer.MAX_VALUE;

    private final ISkuCatalogService skuCatalogService;
    private final Clock clock;
    private final PromotionStore promotionStore;

    public PromotionManager(ISkuCatalogService skuCatalogService) {
        this(skuCatalogService, Clock.systemDefaultZone(), new DatabasePromotionStore());
    }

    PromotionManager(ISkuCatalogService skuCatalogService, Clock clock) {
        this(skuCatalogService, clock, new InMemoryPromotionStore());
    }

    PromotionManager(ISkuCatalogService skuCatalogService, Clock clock, PromotionStore promotionStore) {
        this.skuCatalogService = Objects.requireNonNull(skuCatalogService, "skuCatalogService cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
        this.promotionStore = Objects.requireNonNull(promotionStore, "promotionStore cannot be null");
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

        if (promotionStore.findByCouponCode(normalizedCode) != null) {
            throw new IllegalArgumentException("Coupon code '" + normalizedCode + "' already exists.");
        }

        PromotionState promo = new PromotionState(
                "PROMO-" + java.util.UUID.randomUUID(),
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
        promotionStore.save(promo);
        return promo.promoId();
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
        promotionStore.save(promo);
    }

    @Override
    public List<String> getActivePromoCodes() {
        LocalDate today = LocalDate.now(clock);
        return promotionStore.findAll().stream()
                .filter(promo -> !promo.expired())
                .filter(promo -> !today.isBefore(promo.startDate()) && !today.isAfter(promo.endDate()))
                .filter(promo -> promo.maxUses() == 0 || promo.currentUseCount() < promo.maxUses())
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
        for (PromotionState promo : promotionStore.findAll()) {
            if (promo.isExpiredOn(today)) {
                promo.markExpired();
                promotionStore.save(promo);
            }
        }
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
        return promotionStore.findByCouponCode(normalizedCouponCode);
    }

    interface PromotionStore {
        PromotionState findByCouponCode(String couponCode);
        List<PromotionState> findAll();
        void save(PromotionState promotion);
    }

    static final class InMemoryPromotionStore implements PromotionStore {
        private final Map<String, PromotionState> promotionsById = new ConcurrentHashMap<>();

        @Override
        public PromotionState findByCouponCode(String couponCode) {
            return promotionsById.values().stream()
                    .filter(promotion -> promotion.couponCode().equalsIgnoreCase(couponCode))
                    .findFirst()
                    .orElse(null);
        }

        @Override
        public List<PromotionState> findAll() {
            return promotionsById.values().stream()
                    .sorted(Comparator.comparing(PromotionState::couponCode))
                    .map(PromotionState::copy)
                    .collect(Collectors.toCollection(ArrayList::new));
        }

        @Override
        public void save(PromotionState promotion) {
            promotionsById.put(promotion.promoId(), promotion.copy());
        }
    }

    private static final class DatabasePromotionStore implements PromotionStore {

        @Override
        public PromotionState findByCouponCode(String couponCode) {
            return findAll().stream()
                    .filter(promotion -> promotion.couponCode().equalsIgnoreCase(couponCode))
                    .findFirst()
                    .orElse(null);
        }

        @Override
        public List<PromotionState> findAll() {
            return DatabaseModuleSupport.withFacade(facade -> {
                PackagingAdapter packagingAdapter = new PackagingAdapter(facade);
                List<PackagingPromotion> promotions = packagingAdapter.listPromotions();
                Map<String, List<String>> eligibleSkuMap = packagingAdapter.listPromotionEligibleSkus().stream()
                        .collect(Collectors.groupingBy(
                                PromotionEligibleSku::promoId,
                                Collectors.mapping(PromotionEligibleSku::skuId, Collectors.toList())));
                return promotions.stream()
                        .map(promotion -> mapPromotion(
                                promotion,
                                eligibleSkuMap.getOrDefault(
                                        promotion.promoId(),
                                        parseSkuJson(promotion.eligibleSkuIds()))))
                        .collect(Collectors.toCollection(ArrayList::new));
            });
        }

        @Override
        public void save(PromotionState promotion) {
            DatabaseModuleSupport.useFacade(facade -> {
                PricingAdapter pricingAdapter = new PricingAdapter(facade);
                Promotion dbPromotion = toDbPromotion(promotion);
                if (pricingAdapter.getPromotion(dbPromotion.promoId()).isPresent()) {
                    pricingAdapter.updatePromotion(dbPromotion);
                } else {
                    pricingAdapter.createPromotion(dbPromotion);
                }
            });
        }

        private PromotionState mapPromotion(PackagingPromotion promotion, List<String> eligibleSkus) {
            PromotionState state = new PromotionState(
                    promotion.promoId(),
                    promotion.promoName(),
                    promotion.couponCode(),
                    DiscountType.valueOf(promotion.discountType()),
                    promotion.discountValue().doubleValue(),
                    promotion.startDate().toLocalDate(),
                    promotion.endDate().toLocalDate(),
                    eligibleSkus,
                    promotion.minCartValue().doubleValue(),
                    normalizeMaxUses(promotion.maxUses()));
            state.currentUseCount = normalizeCurrentUseCount(promotion.currentUseCount(), promotion.maxUses());
            state.expired = promotion.expired();
            return state;
        }

        private Promotion toDbPromotion(PromotionState promotion) {
            return new Promotion(
                    promotion.promoId(),
                    promotion.name(),
                    promotion.couponCode(),
                    promotion.discountType().name(),
                    BigDecimal.valueOf(promotion.discountValue()),
                    promotion.startDate().atStartOfDay(),
                    promotion.endDate().atTime(23, 59, 59),
                    toSkuJson(promotion.eligibleSkuIds()),
                    BigDecimal.valueOf(promotion.minCartValue()),
                    promotion.maxUses() == 0 ? UNLIMITED_MAX_USES : promotion.maxUses(),
                    promotion.currentUseCount(),
                    promotion.expired());
        }

        private int normalizeMaxUses(int maxUses) {
            return maxUses >= UNLIMITED_MAX_USES ? 0 : maxUses;
        }

        private int normalizeCurrentUseCount(int currentUseCount, int maxUses) {
            if (maxUses >= UNLIMITED_MAX_USES) {
                return Math.max(0, currentUseCount);
            }
            return currentUseCount;
        }
    }

    private static List<String> parseSkuJson(String skuJson) {
        if (skuJson == null || skuJson.isBlank() || "[]".equals(skuJson.trim())) {
            return List.of();
        }
        String normalized = skuJson.trim();
        if (normalized.startsWith("[")) {
            normalized = normalized.substring(1);
        }
        if (normalized.endsWith("]")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.isBlank()) {
            return List.of();
        }
        String[] rawTokens = normalized.split(",");
        List<String> results = new ArrayList<>();
        for (String rawToken : rawTokens) {
            String token = rawToken.trim();
            if (token.startsWith("\"")) {
                token = token.substring(1);
            }
            if (token.endsWith("\"")) {
                token = token.substring(0, token.length() - 1);
            }
            if (!token.isBlank()) {
                results.add(token);
            }
        }
        return results;
    }

    private static String toSkuJson(List<String> skuIds) {
        return skuIds.stream()
                .map(skuId -> "\"" + skuId + "\"")
                .collect(Collectors.joining(",", "[", "]"));
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
            this.eligibleSkuIds = Collections.unmodifiableList(new ArrayList<>(new LinkedHashSet<>(eligibleSkuIds)));
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

        private PromotionState copy() {
            PromotionState copy = new PromotionState(
                    promoId,
                    name,
                    couponCode,
                    discountType,
                    discountValue,
                    startDate,
                    endDate,
                    eligibleSkuIds,
                    minCartValue,
                    maxUses);
            copy.currentUseCount = currentUseCount;
            copy.expired = expired;
            return copy;
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
                throw new IllegalStateException(
                        "Promotion " + promoId + " has reached its maximum number of uses (" + maxUses + ").");
            }
            currentUseCount++;
        }

        private synchronized void markExpired() {
            expired = true;
        }

        private boolean isExpiredOn(LocalDate today) {
            return today.isAfter(endDate);
        }

        private String promoId() {
            return promoId;
        }

        private String name() {
            return name;
        }

        private String couponCode() {
            return couponCode;
        }

        private DiscountType discountType() {
            return discountType;
        }

        private double discountValue() {
            return discountValue;
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
