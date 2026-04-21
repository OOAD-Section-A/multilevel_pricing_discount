package com.pricingos.pricing.promotion;

import com.jackfruit.scm.database.adapter.PricingAdapter;
import com.jackfruit.scm.database.facade.SupplyChainDatabaseFacade;
import com.jackfruit.scm.database.model.PricingModels.*;
import com.pricingos.common.ISkuCatalogService;
import com.pricingos.common.IVolumeDiscountService;
import com.pricingos.common.VolumeTierRule;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class VolumeDiscountManager implements IVolumeDiscountService {

    private final AtomicInteger idCounter = new AtomicInteger();
    private final ISkuCatalogService skuCatalogService;
    private final PricingAdapter pricingAdapter;
    
    // Local cache for quick lookups: SKU -> ScheduleID
    private final Map<String, String> skuToScheduleId = new ConcurrentHashMap<>();

    public VolumeDiscountManager(ISkuCatalogService skuCatalogService, PricingAdapter pricingAdapter) {
        this.skuCatalogService = Objects.requireNonNull(skuCatalogService, "skuCatalogService cannot be null");
        this.pricingAdapter = Objects.requireNonNull(pricingAdapter, "pricingAdapter cannot be null");
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

        // Validate tiers
        VolumeSchedule.validate(tiers);

        String scheduleId = "VOL-" + idCounter.incrementAndGet();
        
        // Create the VolumeDiscountSchedule record
        VolumeDiscountSchedule schedule = new VolumeDiscountSchedule(scheduleId, normalized);
        pricingAdapter.createVolumeDiscountSchedule(schedule);
        
        // Create each VolumeTierRule record
        for (VolumeTierRule tier : tiers) {
            // Convert discountPct from double (0-100 scale) to BigDecimal (0-1 scale)
            BigDecimal discountPctDecimal = BigDecimal.valueOf(tier.getDiscountPct() / 100.0);
            com.jackfruit.scm.database.model.PricingModels.VolumeTierRule dbTier = new com.jackfruit.scm.database.model.PricingModels.VolumeTierRule(
                0L,  // id will be auto-generated
                scheduleId,
                tier.getMinQty(),
                tier.getMaxQty(),
                discountPctDecimal
            );
            pricingAdapter.createVolumeTierRule(dbTier);
        }
        
        // Cache the mapping for quick lookup
        skuToScheduleId.put(normalized, scheduleId);
        
        return scheduleId;
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

        // Try cache first
        String scheduleId = skuToScheduleId.get(normalized);
        if (scheduleId == null) {
            // Fall back to database lookup - find schedule by SKU
            List<VolumeDiscountSchedule> allSchedules = pricingAdapter.listVolumeDiscountSchedules();
            Optional<VolumeDiscountSchedule> scheduleOpt = allSchedules.stream()
                .filter(s -> s.skuId().equals(normalized))
                .findFirst();
                
            if (scheduleOpt.isEmpty()) {
                return baseUnitPrice;  // No volume discount for this SKU
            }
            scheduleId = scheduleOpt.get().scheduleId();
            skuToScheduleId.put(normalized, scheduleId);
        }
        
        // Get the tier rules for this schedule
        List<com.jackfruit.scm.database.model.PricingModels.VolumeTierRule> dbTiers = pricingAdapter.getVolumeTierRules(scheduleId);
        if (dbTiers.isEmpty()) {
            return baseUnitPrice;
        }
        
        List<VolumeTierRule> tiers = new ArrayList<>();
        for (com.jackfruit.scm.database.model.PricingModels.VolumeTierRule dbTier : dbTiers) {
            tiers.add(new VolumeTierRule(dbTier.minQty(), dbTier.maxQty(), dbTier.discountPct().doubleValue() * 100.0));
        }
        
        VolumeSchedule schedule = new VolumeSchedule(tiers);
        return schedule.computeDiscountedUnitPrice(baseUnitPrice, quantity);
    }

    @Override
    public double getLineTotal(String skuId, int quantity, double baseUnitPrice) {
        return getDiscountedUnitPrice(skuId, quantity, baseUnitPrice) * quantity;
    }

    @Override
    public boolean hasVolumePromotion(String skuId) {
        String normalized = skuId == null ? "" : skuId.trim();
        if (normalized.isEmpty()) {
            return false;
        }
        // Check cache first
        if (skuToScheduleId.containsKey(normalized)) {
            return true;
        }
        // Query database
        List<VolumeDiscountSchedule> allSchedules = pricingAdapter.listVolumeDiscountSchedules();
        return allSchedules.stream().anyMatch(s -> s.skuId().equals(normalized));
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
