package com.pricingos.pricing.promotion;

import com.jackfruit.scm.database.adapter.PricingAdapter;
import com.jackfruit.scm.database.model.PricingModels.*;
import com.pricingos.common.IBundlePromotionService;
import com.pricingos.common.ISkuCatalogService;
import com.pricingos.common.ValidationUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class BundlePromotionManager implements IBundlePromotionService {

    private final AtomicInteger idCounter = new AtomicInteger();
    private final ISkuCatalogService skuCatalogService;
    private final PricingAdapter pricingAdapter;
    private final Clock clock;

    public BundlePromotionManager(ISkuCatalogService skuCatalogService, PricingAdapter pricingAdapter) {
        this(skuCatalogService, pricingAdapter, Clock.systemDefaultZone());
    }

    BundlePromotionManager(ISkuCatalogService skuCatalogService, PricingAdapter pricingAdapter, Clock clock) {
        this.skuCatalogService = Objects.requireNonNull(skuCatalogService, "skuCatalogService cannot be null");
        this.pricingAdapter = Objects.requireNonNull(pricingAdapter, "pricingAdapter cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
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

        String promoId = "BNDL-" + java.util.UUID.randomUUID().toString();

        // Create the BundlePromotion record
        com.jackfruit.scm.database.model.PricingModels.BundlePromotion bundlePromo = new com.jackfruit.scm.database.model.PricingModels.BundlePromotion(
            promoId,
            name,
            BigDecimal.valueOf(discountPct / 100.0),  // Convert from 0-100 scale to 0-1 scale
            startDate,
            endDate,
            false  // not expired
        );
        pricingAdapter.createBundlePromotion(bundlePromo);

        // Create a BundlePromotionSku record for each SKU
        for (String sku : normalizedSkus) {
            com.jackfruit.scm.database.model.PricingModels.BundlePromotionSku bundleSku = new com.jackfruit.scm.database.model.PricingModels.BundlePromotionSku(0L, promoId, sku);
            pricingAdapter.createBundlePromotionSku(bundleSku);
        }

        return promoId;
    }

    @Override
    public double getBestBundleDiscount(List<String> cartSkuIds, double cartTotal) {
        Objects.requireNonNull(cartSkuIds, "cartSkuIds cannot be null");
        if (!Double.isFinite(cartTotal) || cartTotal < 0)
            throw new IllegalArgumentException("cartTotal must be a non-negative finite number");

        LocalDate today = LocalDate.now(clock);
        double best = 0.0;

        List<BundlePromotion> allBundles = pricingAdapter.listVolumeDiscountSchedules().isEmpty() ? 
            Collections.emptyList() : getActiveBundlesFromDatabase();
            
        // Get all bundle promotions and their associated SKUs
        for (BundlePromotion bundlePromo : getAllBundlePromotionsWithSkus()) {
            if (bundlePromo.isApplicableTo(cartSkuIds, today)) {
                double discount = bundlePromo.computeDiscount(cartTotal);
                if (discount > best) best = discount;
            }
        }
        return best;
    }

    @Override
    public List<String> getActiveBundlePromotions() {
        LocalDate today = LocalDate.now(clock);
        return getAllBundlePromotionsWithSkus().stream()
            .filter(p -> !p.isExpired())
            .filter(p -> !today.isBefore(p.getStartDate()) && !today.isAfter(p.getEndDate()))
            .filter(p -> !p.isExpiredOn(today))
            .map(BundlePromotion::getPromoId)
            .sorted()
            .collect(Collectors.toList());
    }

    /**
     * Get all bundle promotions with their associated SKUs from the database
     */
    private List<BundlePromotion> getAllBundlePromotionsWithSkus() {
        // This is a placeholder - in real implementation, we'd need to join the tables
        // For now, we'll fetch all bundles individually
        // Note: This could be optimized by adding a method to PricingAdapter to return bundles with skus
        return Collections.emptyList();  // TODO: Implement when database methods are available
    }

    private List<BundlePromotion> getActiveBundlesFromDatabase() {
        return Collections.emptyList();  // TODO: Implement when database methods are available
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
            if (discountPct.signum() <= 0 || discountPct.compareTo(BigDecimal.ONE) > 0) {
                throw new IllegalArgumentException("discountPct must be in (0, 1]");
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
            return cartTotal * discountPct.doubleValue();
        }

        private void markExpired() {
            this.expired = true;
        }

        private boolean isExpiredOn(LocalDate today) {
            return today.isAfter(endDate);
        }
    }

}
