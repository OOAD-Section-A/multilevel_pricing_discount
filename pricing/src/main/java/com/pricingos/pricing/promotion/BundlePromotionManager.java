package com.pricingos.pricing.promotion;

import com.pricingos.common.IBundlePromotionService;
import com.pricingos.common.ISkuCatalogService;
import com.pricingos.common.ValidationUtils;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class BundlePromotionManager implements IBundlePromotionService {

    private final Map<String, BundlePromotion> promoById = new ConcurrentHashMap<>();
    private final AtomicInteger idCounter = new AtomicInteger();
    private final ISkuCatalogService skuCatalogService;
    private final Clock clock;

    public BundlePromotionManager(ISkuCatalogService skuCatalogService) {
        this(skuCatalogService, Clock.systemDefaultZone());
    }

    BundlePromotionManager(ISkuCatalogService skuCatalogService, Clock clock) {
        this.skuCatalogService = Objects.requireNonNull(skuCatalogService, "skuCatalogService cannot be null");
        this.clock             = Objects.requireNonNull(clock, "clock cannot be null");
    }

    @Override
    public String createBundlePromotion(String name, List<String> bundleSkuIds,
                                        double discountPct,
                                        LocalDate startDate, LocalDate endDate) {
        ValidationUtils.requireNonBlank(name, "name");
        Objects.requireNonNull(bundleSkuIds, "bundleSkuIds cannot be null");
        if (bundleSkuIds.isEmpty())
            throw new IllegalArgumentException("bundleSkuIds cannot be empty");

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

        BundlePromotion promo = new BundlePromotion(
            promoId,
            name,
            normalizedSkus,
            discountPct,
            startDate,
            endDate
        );

        promoById.put(promoId, promo);
        return promoId;
    }

    @Override
    public double getBestBundleDiscount(List<String> cartSkuIds, double cartTotal) {
        Objects.requireNonNull(cartSkuIds, "cartSkuIds cannot be null");
        if (!Double.isFinite(cartTotal) || cartTotal < 0)
            throw new IllegalArgumentException("cartTotal must be a non-negative finite number");

        LocalDate today = LocalDate.now(clock);
        double best = 0.0;

        for (BundlePromotion promo : promoById.values()) {
            if (promo.isApplicableTo(cartSkuIds, today)) {
                double discount = promo.computeDiscount(cartTotal);
                if (discount > best) best = discount;
            }
        }
        return best;
    }

    @Override
    public List<String> getActiveBundlePromotions() {
        LocalDate today = LocalDate.now(clock);
        promoById.values().forEach(promo -> {
            if (promo.isExpiredOn(today)) {
                promo.markExpired();
            }
        });
        return promoById.values().stream()
            .filter(p -> !p.isExpired())
            .filter(p -> !today.isBefore(p.getStartDate()) && !today.isAfter(p.getEndDate()))
            .map(BundlePromotion::getPromoId)
            .sorted()
            .collect(Collectors.toList());
    }

    private static final class BundlePromotion {
        private final String promoId;
        private final Set<String> bundleSkuIds;
        private final double discountPct;
        private final LocalDate startDate;
        private final LocalDate endDate;
        private volatile boolean expired;

        private BundlePromotion(String promoId,
                                String name,
                                Set<String> bundleSkuIds,
                                double discountPct,
                                LocalDate startDate,
                                LocalDate endDate) {
            this.promoId = ValidationUtils.requireNonBlank(promoId, "promoId");
            ValidationUtils.requireNonBlank(name, "name");
            Objects.requireNonNull(bundleSkuIds, "bundleSkuIds cannot be null");
            if (bundleSkuIds.isEmpty()) {
                throw new IllegalArgumentException("bundleSkuIds cannot be empty");
            }
            if (!Double.isFinite(discountPct) || discountPct <= 0 || discountPct > 100) {
                throw new IllegalArgumentException("discountPct must be in (0, 100]");
            }
            this.startDate = Objects.requireNonNull(startDate, "startDate cannot be null");
            this.endDate = Objects.requireNonNull(endDate, "endDate cannot be null");
            if (endDate.isBefore(startDate)) {
                throw new IllegalArgumentException("endDate cannot be before startDate");
            }
            this.bundleSkuIds = Collections.unmodifiableSet(new HashSet<>(bundleSkuIds));
            this.discountPct = discountPct;
            this.expired = false;
        }

        private String getPromoId() {
            return promoId;
        }

        private LocalDate getStartDate() {
            return startDate;
        }

        private LocalDate getEndDate() {
            return endDate;
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
            return cartTotal * (discountPct / 100.0);
        }

        private void markExpired() {
            this.expired = true;
        }

        private boolean isExpiredOn(LocalDate today) {
            return today.isAfter(endDate);
        }
    }

}
