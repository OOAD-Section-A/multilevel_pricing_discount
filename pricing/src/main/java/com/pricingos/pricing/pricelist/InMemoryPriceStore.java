package com.pricingos.pricing.pricelist;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of the price store abstraction.
 * SOLID DIP: serves as a pluggable store for testability and swapability.
 */
public class InMemoryPriceStore implements IPriceStore {

    private final Map<String, List<PriceRecord>> recordsByKey;

    /**
     * Creates an empty concurrent in-memory store.
     */
    public InMemoryPriceStore() {
        this.recordsByKey = new ConcurrentHashMap<>();
    }

    /**
     * Finds active record for given key.
     *
     * @param skuId SKU identifier; must be non-blank
     * @param region region code; must be non-blank
     * @param channel channel code; must be non-blank
     * @return optional active immutable record
     */
    @Override
    public synchronized Optional<PriceRecord> findActive(String skuId, String region, String channel) {
        return recordsByKey.getOrDefault(key(skuId, region, channel), List.of())
                .stream()
                .filter(record -> record.getStatus() == PriceRecord.Status.ACTIVE)
                .findFirst();
    }

    /**
     * Returns all versions for a SKU across region/channel keys.
     *
     * @param skuId SKU identifier; must be non-blank
     * @return immutable list of versions, possibly empty
     */
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

    /**
     * Returns all currently active records in the store.
     *
     * @return immutable list of active records
     */
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

    /**
     * Persists a new immutable record.
     *
     * @param record immutable record to persist; must be non-null
     */
    @Override
    public synchronized void save(PriceRecord record) {
        recordsByKey.computeIfAbsent(key(record.getSkuId(), record.getRegionCode(), record.getChannel()), ignored -> new ArrayList<>())
                .add(record);
    }

    /**
     * Marks existing active record as superseded for the given key.
     *
     * @param skuId SKU identifier; must be non-blank
     * @param region region code; must be non-blank
     * @param channel channel code; must be non-blank
     * @param effectiveTo record end timestamp; must be non-null
     */
    @Override
    public synchronized void markActiveAsSuperseded(String skuId, String region, String channel, Date effectiveTo) {
        String key = key(skuId, region, channel);
        List<PriceRecord> versions = recordsByKey.getOrDefault(key, List.of());
        List<PriceRecord> updated = new ArrayList<>(versions.size());
        for (PriceRecord current : versions) {
            if (current.getStatus() == PriceRecord.Status.ACTIVE) {
                // Versioning rule: old active record is closed and moved to SUPERSEDED.
                updated.add(current.withStatus(PriceRecord.Status.SUPERSEDED, effectiveTo));
            } else {
                updated.add(current);
            }
        }
        if (!updated.isEmpty()) {
            recordsByKey.put(key, updated);
        }
    }

    /**
     * Clears all in-memory records.
     */
    @Override
    public synchronized void clear() {
        recordsByKey.clear();
    }

    private static String key(String skuId, String region, String channel) {
        return skuId + "|" + region + "|" + channel;
    }
}
