package com.pricingos.pricing.promotion;

import com.jackfruit.scm.database.adapter.PricingAdapter;
import com.jackfruit.scm.database.model.PricingModels;
import com.pricingos.common.ISkuCatalogService;
import com.pricingos.common.IVolumeDiscountService;
import com.pricingos.common.VolumeTierRule;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class VolumeDiscountManager implements IVolumeDiscountService {

    private static final int OPEN_ENDED_SENTINEL = Integer.MAX_VALUE;

    private final ISkuCatalogService skuCatalogService;
    private final VolumeStore volumeStore;

    public VolumeDiscountManager(ISkuCatalogService skuCatalogService, PricingAdapter pricingAdapter) {
        this(skuCatalogService, new DatabaseVolumeStore(pricingAdapter));
    }

    VolumeDiscountManager(ISkuCatalogService skuCatalogService, VolumeStore volumeStore) {
        this.skuCatalogService = Objects.requireNonNull(skuCatalogService, "skuCatalogService cannot be null");
        this.volumeStore = Objects.requireNonNull(volumeStore, "volumeStore cannot be null");
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

        VolumeSchedule.validate(tiers);
        String scheduleId = "VOL-" + java.util.UUID.randomUUID();
        volumeStore.save(scheduleId, normalized, tiers);
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

        List<VolumeTierRule> tiers = volumeStore.findTiersBySku(normalized);
        if (tiers.isEmpty()) {
            return baseUnitPrice;
        }
        return new VolumeSchedule(tiers).computeDiscountedUnitPrice(baseUnitPrice, quantity);
    }

    @Override
    public double getLineTotal(String skuId, int quantity, double baseUnitPrice) {
        return getDiscountedUnitPrice(skuId, quantity, baseUnitPrice) * quantity;
    }

    @Override
    public boolean hasVolumePromotion(String skuId) {
        String normalized = skuId == null ? "" : skuId.trim();
        return !normalized.isEmpty() && volumeStore.hasScheduleForSku(normalized);
    }

    interface VolumeStore {
        void save(String scheduleId, String skuId, List<VolumeTierRule> tiers);
        List<VolumeTierRule> findTiersBySku(String skuId);
        boolean hasScheduleForSku(String skuId);
    }

    static final class InMemoryVolumeStore implements VolumeStore {
        private final Map<String, List<VolumeTierRule>> tiersBySku = new ConcurrentHashMap<>();

        @Override
        public void save(String scheduleId, String skuId, List<VolumeTierRule> tiers) {
            tiersBySku.put(skuId, copyTiers(tiers));
        }

        @Override
        public List<VolumeTierRule> findTiersBySku(String skuId) {
            return copyTiers(tiersBySku.getOrDefault(skuId, List.of()));
        }

        @Override
        public boolean hasScheduleForSku(String skuId) {
            return tiersBySku.containsKey(skuId);
        }

        private List<VolumeTierRule> copyTiers(List<VolumeTierRule> source) {
            return source.stream()
                    .map(tier -> new VolumeTierRule(tier.getMinQty(), tier.getMaxQty(), tier.getDiscountPct()))
                    .collect(Collectors.toCollection(ArrayList::new));
        }
    }

    private static final class DatabaseVolumeStore implements VolumeStore {
        private final PricingAdapter pricingAdapter;
        private final Map<String, String> skuToScheduleId = new ConcurrentHashMap<>();

        private DatabaseVolumeStore(PricingAdapter pricingAdapter) {
            this.pricingAdapter = Objects.requireNonNull(pricingAdapter, "pricingAdapter cannot be null");
        }

        @Override
        public void save(String scheduleId, String skuId, List<VolumeTierRule> tiers) {
            String existingScheduleId = findScheduleIdBySku(skuId);
            if (existingScheduleId != null) {
                pricingAdapter.deleteVolumeDiscountSchedule(existingScheduleId);
            }
            pricingAdapter.createVolumeDiscountSchedule(new PricingModels.VolumeDiscountSchedule(scheduleId, skuId));
            for (VolumeTierRule tier : tiers) {
                pricingAdapter.createVolumeTierRule(new PricingModels.VolumeTierRule(
                        0L,
                        scheduleId,
                        tier.getMinQty(),
                        tier.getMaxQty() == 0 ? OPEN_ENDED_SENTINEL : tier.getMaxQty(),
                        BigDecimal.valueOf(tier.getDiscountPct() / 100.0)));
            }
            skuToScheduleId.put(skuId, scheduleId);
        }

        @Override
        public List<VolumeTierRule> findTiersBySku(String skuId) {
            String scheduleId = skuToScheduleId.computeIfAbsent(skuId, this::findScheduleIdBySku);
            if (scheduleId == null) {
                return List.of();
            }

            return pricingAdapter.getVolumeTierRules(scheduleId).stream()
                    .sorted(Comparator.comparingInt(PricingModels.VolumeTierRule::minQty))
                    .map(tier -> new VolumeTierRule(
                            tier.minQty(),
                            tier.maxQty() >= OPEN_ENDED_SENTINEL ? 0 : tier.maxQty(),
                            tier.discountPct().doubleValue() * 100.0))
                    .collect(Collectors.toCollection(ArrayList::new));
        }

        @Override
        public boolean hasScheduleForSku(String skuId) {
            return findScheduleIdBySku(skuId) != null;
        }

        private String findScheduleIdBySku(String skuId) {
            Optional<PricingModels.VolumeDiscountSchedule> schedule = pricingAdapter.listVolumeDiscountSchedules().stream()
                    .filter(candidate -> candidate.skuId().equals(skuId))
                    .findFirst();
            return schedule.map(PricingModels.VolumeDiscountSchedule::scheduleId).orElse(null);
        }
    }

    private static final class VolumeSchedule {
        private final List<VolumeTierRule> tiers;

        private VolumeSchedule(List<VolumeTierRule> tiers) {
            Objects.requireNonNull(tiers, "tiers cannot be null");
            if (tiers.isEmpty()) {
                throw new IllegalArgumentException("tiers list cannot be empty");
            }
            List<VolumeTierRule> sorted = new ArrayList<>(tiers);
            sorted.sort(Comparator.comparingInt(VolumeTierRule::getMinQty));
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
            for (int index = 0; index < sorted.size(); index++) {
                VolumeTierRule current = sorted.get(index);
                if (current.getMaxQty() == 0) {
                    unlimitedCount++;
                    if (index != sorted.size() - 1) {
                        throw new IllegalArgumentException("The unlimited tier (maxQty=0) must be the last tier.");
                    }
                }
                if (index > 0) {
                    VolumeTierRule previous = sorted.get(index - 1);
                    if (previous.getMaxQty() != 0 && previous.getMaxQty() + 1 != current.getMinQty()) {
                        throw new IllegalArgumentException(
                                "Tiers must be contiguous. Gap found between tier ending at "
                                        + previous.getMaxQty() + " and tier starting at " + current.getMinQty());
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
