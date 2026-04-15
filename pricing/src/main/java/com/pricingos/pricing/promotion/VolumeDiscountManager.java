package com.pricingos.pricing.promotion;

import com.pricingos.common.ISkuCatalogService;
import com.pricingos.common.IVolumeDiscountService;
import com.pricingos.common.VolumeTierRule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class VolumeDiscountManager implements IVolumeDiscountService {

    private final Map<String, VolumeSchedule> promoBySkuId = new ConcurrentHashMap<>();
    private final AtomicInteger idCounter = new AtomicInteger();
    private final ISkuCatalogService skuCatalogService;

    public VolumeDiscountManager(ISkuCatalogService skuCatalogService) {
        this.skuCatalogService = Objects.requireNonNull(skuCatalogService, "skuCatalogService cannot be null");
    }

    @Override
    public String createVolumePromotion(String skuId, List<VolumeTierRule> tiers) {
        Objects.requireNonNull(skuId, "skuId cannot be null");
        String normalized = skuId.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("skuId cannot be blank");
        }
        if (!skuCatalogService.isSkuActive(normalized)) {
            throw new IllegalArgumentException("SKU '" + normalized + "' is not active in the product catalog.");
        }
        Objects.requireNonNull(tiers, "tiers cannot be null");
        if (tiers.isEmpty()) {
            throw new IllegalArgumentException("tiers cannot be empty");
        }

        String promoId = "VOL-" + idCounter.incrementAndGet();
        promoBySkuId.put(normalized, new VolumeSchedule(tiers));
        return promoId;
    }

    @Override
    public double getDiscountedUnitPrice(String skuId, int quantity, double baseUnitPrice) {
        Objects.requireNonNull(skuId, "skuId cannot be null");
        String normalized = skuId.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("skuId cannot be blank");
        }
        if (quantity < 1) {
            throw new IllegalArgumentException("quantity must be >= 1, got: " + quantity);
        }
        if (!Double.isFinite(baseUnitPrice) || baseUnitPrice < 0) {
            throw new IllegalArgumentException("baseUnitPrice must be a non-negative finite number");
        }

        VolumeSchedule promo = promoBySkuId.get(normalized);
        if (promo == null) {
            return baseUnitPrice;
        }
        return promo.computeDiscountedUnitPrice(baseUnitPrice, quantity);
    }

    @Override
    public double getLineTotal(String skuId, int quantity, double baseUnitPrice) {
        return getDiscountedUnitPrice(skuId, quantity, baseUnitPrice) * quantity;
    }

    @Override
    public boolean hasVolumePromotion(String skuId) {
        return promoBySkuId.containsKey(skuId == null ? "" : skuId.trim());
    }

    private static final class VolumeSchedule {
        private final List<VolumeTierRule> tiers;

        private VolumeSchedule(List<VolumeTierRule> tiers) {
            Objects.requireNonNull(tiers, "tiers cannot be null");
            if (tiers.isEmpty()) {
                throw new IllegalArgumentException("tiers list cannot be empty");
            }
            List<VolumeTierRule> sorted = new ArrayList<>(tiers);
            sorted.sort((a, b) -> Integer.compare(a.getMinQty(), b.getMinQty()));
            validate(sorted);
            this.tiers = Collections.unmodifiableList(sorted);
        }

        private double computeDiscountedUnitPrice(double baseUnitPrice, int quantity) {
            for (VolumeTierRule tier : tiers) {
                if (tier.appliesToQuantity(quantity)) {
                    return tier.computeDiscountedUnitPrice(baseUnitPrice);
                }
            }
            return baseUnitPrice;
        }

        private static void validate(List<VolumeTierRule> sorted) {
            int unlimitedCount = 0;
            for (int i = 0; i < sorted.size(); i++) {
                VolumeTierRule current = sorted.get(i);
                if (current.getMaxQty() == 0) {
                    unlimitedCount++;
                    if (i != sorted.size() - 1) {
                        throw new IllegalArgumentException("The unlimited tier (maxQty=0) must be the last tier.");
                    }
                }
                if (i > 0) {
                    VolumeTierRule prev = sorted.get(i - 1);
                    if (prev.getMaxQty() != 0 && prev.getMaxQty() + 1 != current.getMinQty()) {
                        throw new IllegalArgumentException(
                            "Tiers must be contiguous. Gap found between tier ending at "
                                + prev.getMaxQty() + " and tier starting at " + current.getMinQty());
                    }
                }
            }
            if (sorted.get(0).getMinQty() != 1) {
                throw new IllegalArgumentException("The first tier must start at minQty=1");
            }
            if (unlimitedCount == 0) {
                throw new IllegalArgumentException("Volume promotion must have exactly one open-ended top tier (maxQty=0).");
            }
        }
    }
}
