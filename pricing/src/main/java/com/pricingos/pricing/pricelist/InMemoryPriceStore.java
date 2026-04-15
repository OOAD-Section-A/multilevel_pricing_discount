package com.pricingos.pricing.pricelist;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryPriceStore implements IPriceStore {

    private final Map<String, List<PriceRecord>> recordsByKey;

    public InMemoryPriceStore() {
        this.recordsByKey = new ConcurrentHashMap<>();
    }

    @Override
    public synchronized Optional<PriceRecord> findActive(String skuId, String region, String channel) {
        return recordsByKey.getOrDefault(key(skuId, region, channel), List.of())
                .stream()
                .filter(record -> record.getStatus() == PriceRecord.Status.ACTIVE)
                .findFirst();
    }

    @Override
    public synchronized List<PriceRecord> findBySku(String skuId) {
        List<PriceRecord> collected = new ArrayList<>();
        for (List<PriceRecord> versions : recordsByKey.values()) {
            for (PriceRecord record : versions) {
                if (record.getSkuId().equals(skuId)) {
                    collected.add(record);
                }
            }
        }
        return Collections.unmodifiableList(collected);
    }

    @Override
    public synchronized List<PriceRecord> findAllActive() {
        List<PriceRecord> active = new ArrayList<>();
        for (List<PriceRecord> versions : recordsByKey.values()) {
            for (PriceRecord record : versions) {
                if (record.getStatus() == PriceRecord.Status.ACTIVE) {
                    active.add(record);
                }
            }
        }
        return Collections.unmodifiableList(active);
    }

    @Override
    public synchronized void save(PriceRecord record) {
        recordsByKey.computeIfAbsent(key(record.getSkuId(), record.getRegionCode(), record.getChannel()), ignored -> new ArrayList<>())
                .add(record);
    }

    @Override
    public synchronized void markActiveAsSuperseded(String skuId, String region, String channel, Date effectiveTo) {
        String key = key(skuId, region, channel);
        List<PriceRecord> versions = recordsByKey.getOrDefault(key, List.of());
        List<PriceRecord> updated = new ArrayList<>(versions.size());
        for (PriceRecord current : versions) {
            if (current.getStatus() == PriceRecord.Status.ACTIVE) {

                updated.add(current.withStatus(PriceRecord.Status.SUPERSEDED, effectiveTo));
            } else {
                updated.add(current);
            }
        }
        if (!updated.isEmpty()) {
            recordsByKey.put(key, updated);
        }
    }

    @Override
    public synchronized void clear() {
        recordsByKey.clear();
    }

    private static String key(String skuId, String region, String channel) {
        return skuId + "|" + region + "|" + channel;
    }
}
