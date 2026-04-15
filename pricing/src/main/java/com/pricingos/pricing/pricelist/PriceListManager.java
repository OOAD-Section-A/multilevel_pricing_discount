package com.pricingos.pricing.pricelist;

import com.pricingos.common.ValidationUtils;
import com.pricingos.pricing.baseprice.BasePriceRecord;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/*
 * DESIGN NOTES:
 * - Facade-style API for active lookup/history/versioning.
 * - Depends on IPriceStore abstraction for storage.
 */

/**
 * Price list facade and controller for active and historical price retrieval/versioning.
 */
public class PriceListManager {

    private static final Logger LOGGER = Logger.getLogger(PriceListManager.class.getName());

    private final IPriceStore priceStore;
    private final Map<String, PriceRecord> activePriceCache;
    private String activeRegion;
    private Date lastSyncTimestamp;

    /**
     * Creates a manager with in-memory store and default observer registration.
     */
    public PriceListManager() {
        this(new InMemoryPriceStore(), "GLOBAL");
    }

    /**
     * Creates a manager with explicit store and active region.
     *
     * @param priceStore abstract storage implementation; must be non-null
     * @param activeRegion active region context for the manager; must be non-blank
     */
    public PriceListManager(IPriceStore priceStore, String activeRegion) {
        this.priceStore = Objects.requireNonNull(priceStore, "priceStore cannot be null");
        this.activeRegion = ValidationUtils.requireNonBlank(activeRegion, "activeRegion");
        this.activePriceCache = new ConcurrentHashMap<>();
        this.lastSyncTimestamp = new Date();
        refreshPriceCache();
    }

    /**
     * Returns active base price for SKU, region, and channel.
     *
     * @param skuId SKU identifier; must be non-blank
     * @param region region code; must be non-blank
     * @param channel sales channel; must be non-blank
     * @return active base price amount
     * @throws NoSuchElementException if no active record exists for key
     */
    public double getActivePrice(String skuId, String region, String channel) throws NoSuchElementException {
        String normalizedSku = ValidationUtils.requireNonBlank(skuId, "skuId");
        String normalizedRegion = ValidationUtils.requireNonBlank(region, "region");
        String normalizedChannel = ValidationUtils.requireNonBlank(channel, "channel");
        String key = key(normalizedSku, normalizedRegion, normalizedChannel);

        PriceRecord cached = activePriceCache.get(key);
        if (cached != null && cached.getStatus() == PriceRecord.Status.ACTIVE) {
            return cached.getBasePrice();
        }

        PriceRecord activeRecord = priceStore.findActive(normalizedSku, normalizedRegion, normalizedChannel)
                .filter(record -> record.getStatus() == PriceRecord.Status.ACTIVE)
                .orElseThrow(() -> new NoSuchElementException(
                        "No active base price found for SKU [" + normalizedSku + "] in region [" + normalizedRegion + "]."));
        activePriceCache.put(key, activeRecord);
        return activeRecord.getBasePrice();
    }

    /**
     * Returns all versioned prices for SKU sorted newest-first by effectiveFrom.
     *
     * @param skuId SKU identifier; must be non-blank
     * @return immutable array of versioned price records
     */
    public PriceRecord[] getHistoricalPrices(String skuId) {
        String normalizedSku = ValidationUtils.requireNonBlank(skuId, "skuId");
        List<PriceRecord> sorted = new ArrayList<>(priceStore.findBySku(normalizedSku));
        sorted.sort(Comparator.comparing(PriceRecord::getEffectiveFrom).reversed());
        return sorted.toArray(new PriceRecord[0]);
    }

    /**
     * Clears and rebuilds active cache from store, then logs sync timestamp.
     * Facade responsibility: hide cache-management internals from clients.
     */
    public void refreshPriceCache() {
        activePriceCache.clear();
        for (PriceRecord record : priceStore.findAllActive()) {
            activePriceCache.put(key(record.getSkuId(), record.getRegionCode(), record.getChannel()), record);
        }
        this.lastSyncTimestamp = new Date();
        LOGGER.info(() -> "Price cache refreshed at " + lastSyncTimestamp);
    }

    /**
     * Versions previous active price and stores the new active record.
     *
     * @param record base price input record; must be non-null
     * @return no return value
     */
    public void updatePrice(BasePriceRecord record) {
        Objects.requireNonNull(record, "record cannot be null");
        Date now = new Date();
        String key = key(record.getSkuId(), record.getRegionCode(), record.getChannel());

        // Versioning logic: active record for same key becomes SUPERSEDED before new ACTIVE insert.
        priceStore.markActiveAsSuperseded(record.getSkuId(), record.getRegionCode(), record.getChannel(), now);

        PriceRecord persisted = new PriceRecord(
                UUID.randomUUID().toString(),
                record.getSkuId(),
                record.getRegionCode(),
                record.getChannel(),
                record.getPriceType(),
                record.getBasePrice(),
                record.getPriceFloor(),
                record.getCurrencyCode(),
                now,
                null,
                PriceRecord.Status.ACTIVE);
        priceStore.save(persisted);
        activePriceCache.put(key, persisted);
        LOGGER.info(() -> "AUDIT price update: priceId=" + persisted.getPriceId()
            + ", skuId=" + persisted.getSkuId()
            + ", region=" + persisted.getRegionCode()
            + ", channel=" + persisted.getChannel()
            + ", basePrice=" + persisted.getBasePrice()
            + ", floor=" + persisted.getPriceFloor()
            + ", effectiveFrom=" + persisted.getEffectiveFrom());
    }

    /**
     * Returns manager region context.
     *
     * @return configured active region value
     */
    public String getActiveRegion() {
        return activeRegion;
    }

    /**
     * Returns last cache sync timestamp.
     *
     * @return defensive copy of last sync timestamp
     */
    public Date getLastSyncTimestamp() {
        return new Date(lastSyncTimestamp.getTime());
    }

    private static String key(String skuId, String region, String channel) {
        return skuId + "|" + region + "|" + channel;
    }
}
