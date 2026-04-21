package com.pricingos.pricing.promotion;

import com.jackfruit.scm.database.adapter.PricingAdapter;
import com.jackfruit.scm.database.model.PackagingModels;
import com.jackfruit.scm.database.model.PricingModels;
import com.pricingos.common.IBundlePromotionService;
import com.pricingos.common.ISkuCatalogService;
import com.pricingos.common.ValidationUtils;
import com.pricingos.pricing.db.DatabaseModuleSupport;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class BundlePromotionManager implements IBundlePromotionService {

    private final ISkuCatalogService skuCatalogService;
    private final BundleStore bundleStore;
    private final Clock clock;

    public BundlePromotionManager(ISkuCatalogService skuCatalogService, PricingAdapter pricingAdapter) {
        this(skuCatalogService, Clock.systemDefaultZone(), new DatabaseBundleStore(pricingAdapter));
    }

    BundlePromotionManager(ISkuCatalogService skuCatalogService, PricingAdapter pricingAdapter, Clock clock) {
        this(skuCatalogService, clock, new DatabaseBundleStore(pricingAdapter));
    }

    BundlePromotionManager(ISkuCatalogService skuCatalogService, Clock clock) {
        this(skuCatalogService, clock, new InMemoryBundleStore());
    }

    BundlePromotionManager(ISkuCatalogService skuCatalogService, Clock clock, BundleStore bundleStore) {
        this.skuCatalogService = Objects.requireNonNull(skuCatalogService, "skuCatalogService cannot be null");
        this.bundleStore = Objects.requireNonNull(bundleStore, "bundleStore cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
    }

    @Override
    public String createBundlePromotion(String name, List<String> bundleSkuIds,
                                        double discountPct,
                                        LocalDate startDate, LocalDate endDate) {
        ValidationUtils.requireNonBlank(name, "name");
        Objects.requireNonNull(bundleSkuIds, "bundleSkuIds cannot be null");
        Objects.requireNonNull(startDate, "startDate cannot be null");
        Objects.requireNonNull(endDate, "endDate cannot be null");
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("endDate cannot be before startDate");
        }
        if (!Double.isFinite(discountPct) || discountPct <= 0 || discountPct > 100) {
            throw new IllegalArgumentException("discountPct must be in (0, 100]");
        }
        if (bundleSkuIds.isEmpty()) {
            throw new IllegalArgumentException("bundleSkuIds cannot be empty");
        }

        Set<String> normalizedSkus = new LinkedHashSet<>();
        for (String sku : bundleSkuIds) {
            if (sku == null) {
                throw new IllegalArgumentException("bundleSkuIds contains a null entry");
            }
            String trimmed = sku.trim();
            if (trimmed.isEmpty()) {
                throw new IllegalArgumentException("bundleSkuIds contains a blank entry");
            }
            if (!skuCatalogService.isSkuActive(trimmed)) {
                throw new IllegalArgumentException("SKU '" + trimmed + "' is not active in the product catalog.");
            }
            normalizedSkus.add(trimmed);
        }

        BundlePromotion bundlePromotion = new BundlePromotion(
                "BNDL-" + java.util.UUID.randomUUID(),
                name,
                normalizedSkus,
                BigDecimal.valueOf(discountPct / 100.0),
                startDate,
                endDate
        );
        bundleStore.save(bundlePromotion);
        return bundlePromotion.getPromoId();
    }

    @Override
    public double getBestBundleDiscount(List<String> cartSkuIds, double cartTotal) {
        Objects.requireNonNull(cartSkuIds, "cartSkuIds cannot be null");
        if (!Double.isFinite(cartTotal) || cartTotal < 0) {
            throw new IllegalArgumentException("cartTotal must be a non-negative finite number");
        }

        LocalDate today = LocalDate.now(clock);
        double best = 0.0;
        for (BundlePromotion bundlePromotion : bundleStore.findAll()) {
            if (bundlePromotion.isApplicableTo(cartSkuIds, today)) {
                best = Math.max(best, bundlePromotion.computeDiscount(cartTotal));
            }
        }
        return best;
    }

    @Override
    public List<String> getActiveBundlePromotions() {
        LocalDate today = LocalDate.now(clock);
        return bundleStore.findAll().stream()
                .filter(bundlePromotion -> !bundlePromotion.isExpired())
                .filter(bundlePromotion -> !today.isBefore(bundlePromotion.getStartDate()))
                .filter(bundlePromotion -> !today.isAfter(bundlePromotion.getEndDate()))
                .filter(bundlePromotion -> !bundlePromotion.isExpiredOn(today))
                .map(BundlePromotion::getPromoId)
                .sorted()
                .collect(Collectors.toList());
    }

    interface BundleStore {
        void save(BundlePromotion promotion);
        List<BundlePromotion> findAll();
    }

    static final class InMemoryBundleStore implements BundleStore {
        private final Map<String, BundlePromotion> bundlesById = new ConcurrentHashMap<>();

        @Override
        public void save(BundlePromotion promotion) {
            bundlesById.put(promotion.getPromoId(), promotion.copy());
        }

        @Override
        public List<BundlePromotion> findAll() {
            return bundlesById.values().stream()
                    .sorted(Comparator.comparing(BundlePromotion::getPromoId))
                    .map(BundlePromotion::copy)
                    .collect(Collectors.toCollection(ArrayList::new));
        }
    }

    private static final class DatabaseBundleStore implements BundleStore {
        private final PricingAdapter pricingAdapter;

        private DatabaseBundleStore(PricingAdapter pricingAdapter) {
            this.pricingAdapter = Objects.requireNonNull(pricingAdapter, "pricingAdapter cannot be null");
        }

        @Override
        public void save(BundlePromotion promotion) {
            if (pricingAdapter.getBundlePromotion(promotion.getPromoId()).isPresent()) {
                pricingAdapter.deleteBundlePromotion(promotion.getPromoId());
            }

            pricingAdapter.createBundlePromotion(new PricingModels.BundlePromotion(
                    promotion.getPromoId(),
                    BundlePromotionRecordCodec.encodeName(promotion.getName(), promotion.getBundleSkuIds()),
                    promotion.getDiscountPct(),
                    promotion.getStartDate(),
                    promotion.getEndDate(),
                    promotion.isExpired()));

            for (String skuId : promotion.getBundleSkuIds()) {
                pricingAdapter.createBundlePromotionSku(new PricingModels.BundlePromotionSku(0L, promotion.getPromoId(), skuId));
            }
        }

        @Override
        public List<BundlePromotion> findAll() {
            return DatabaseModuleSupport.withPackagingAdapter(packagingAdapter ->
                    packagingAdapter.listBundlePromotions().stream()
                            .map(this::mapBundlePromotion)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toCollection(ArrayList::new)));
        }

        private BundlePromotion mapBundlePromotion(PackagingModels.BundlePromotion bundlePromotion) {
            BundlePromotionRecordCodec.DecodedBundle decoded = BundlePromotionRecordCodec.decode(bundlePromotion.promoName());
            if (decoded.bundleSkuIds().isEmpty()) {
                return null;
            }
            BundlePromotion state = new BundlePromotion(
                    bundlePromotion.promoId(),
                    decoded.name(),
                    decoded.bundleSkuIds(),
                    bundlePromotion.discountPct(),
                    bundlePromotion.startDate(),
                    bundlePromotion.endDate());
            state.expired = bundlePromotion.expired();
            return state;
        }
    }

    private static final class BundlePromotionRecordCodec {
        private static final String SKU_MARKER = " |sku| ";
        private static final int MAX_PROMO_NAME_LENGTH = 200;

        private BundlePromotionRecordCodec() {
        }

        private static String encodeName(String name, Set<String> bundleSkuIds) {
            String normalizedName = ValidationUtils.requireNonBlank(name, "name");
            String encoded = normalizedName + SKU_MARKER + String.join(",", bundleSkuIds);
            if (encoded.length() > MAX_PROMO_NAME_LENGTH) {
                throw new IllegalArgumentException("Encoded bundle promotion exceeds database promo_name limit");
            }
            return encoded;
        }

        private static DecodedBundle decode(String storedName) {
            String normalized = ValidationUtils.requireNonBlank(storedName, "storedName");
            int markerIndex = normalized.indexOf(SKU_MARKER);
            if (markerIndex < 0) {
                return new DecodedBundle(normalized, Set.of());
            }

            String name = normalized.substring(0, markerIndex);
            String rawSkuList = normalized.substring(markerIndex + SKU_MARKER.length());
            if (rawSkuList.isBlank()) {
                return new DecodedBundle(name, Set.of());
            }

            Set<String> bundleSkuIds = java.util.Arrays.stream(rawSkuList.split(","))
                    .map(String::trim)
                    .filter(token -> !token.isEmpty())
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            return new DecodedBundle(name, Collections.unmodifiableSet(bundleSkuIds));
        }

        private record DecodedBundle(String name, Set<String> bundleSkuIds) {
        }
    }

    private static final class BundlePromotion {
        private final String promoId;
        private final String name;
        private final Set<String> bundleSkuIds;
        private final BigDecimal discountPct;
        private final LocalDate startDate;
        private final LocalDate endDate;
        private volatile boolean expired;

        private BundlePromotion(String promoId,
                                String name,
                                Set<String> bundleSkuIds,
                                BigDecimal discountPct,
                                LocalDate startDate,
                                LocalDate endDate) {
            this.promoId = ValidationUtils.requireNonBlank(promoId, "promoId");
            this.name = ValidationUtils.requireNonBlank(name, "name");
            Objects.requireNonNull(bundleSkuIds, "bundleSkuIds cannot be null");
            if (bundleSkuIds.isEmpty()) {
                throw new IllegalArgumentException("bundleSkuIds cannot be empty");
            }
            if (discountPct == null || discountPct.signum() <= 0 || discountPct.compareTo(BigDecimal.ONE) > 0) {
                throw new IllegalArgumentException("discountPct must be in (0, 1]");
            }
            this.startDate = Objects.requireNonNull(startDate, "startDate cannot be null");
            this.endDate = Objects.requireNonNull(endDate, "endDate cannot be null");
            if (endDate.isBefore(startDate)) {
                throw new IllegalArgumentException("endDate cannot be before startDate");
            }
            this.bundleSkuIds = Collections.unmodifiableSet(new LinkedHashSet<>(bundleSkuIds));
            this.discountPct = discountPct;
            this.expired = false;
        }

        private BundlePromotion copy() {
            BundlePromotion copy = new BundlePromotion(promoId, name, bundleSkuIds, discountPct, startDate, endDate);
            copy.expired = expired;
            return copy;
        }

        private String getPromoId() {
            return promoId;
        }

        private String getName() {
            return name;
        }

        private Set<String> getBundleSkuIds() {
            return bundleSkuIds;
        }

        private LocalDate getStartDate() {
            return startDate;
        }

        private LocalDate getEndDate() {
            return endDate;
        }

        private BigDecimal getDiscountPct() {
            return discountPct;
        }

        private boolean isExpired() {
            return expired;
        }

        private boolean isApplicableTo(Iterable<String> cartSkuIds, LocalDate today) {
            Objects.requireNonNull(cartSkuIds, "cartSkuIds cannot be null");
            Objects.requireNonNull(today, "today cannot be null");
            if (expired || today.isBefore(startDate) || today.isAfter(endDate)) {
                return false;
            }
            Set<String> normalizedCart = new HashSet<>();
            for (String sku : cartSkuIds) {
                if (sku != null) {
                    normalizedCart.add(sku.trim());
                }
            }
            return normalizedCart.containsAll(bundleSkuIds);
        }

        private double computeDiscount(double cartTotal) {
            return cartTotal * discountPct.doubleValue();
        }

        private boolean isExpiredOn(LocalDate today) {
            return today.isAfter(endDate);
        }
    }
}
