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

public class PriceListManager {

    private static final Logger LOGGER = Logger.getLogger(PriceListManager.class.getName());

    private final IPriceStore priceStore;
    private final Map<String, PriceRecord> activePriceCache;
    private String activeRegion;
    private Date lastSyncTimestamp;

    public PriceListManager() {
        this(new InMemoryPriceStore(), "GLOBAL");
    }

    public PriceListManager(IPriceStore priceStore, String activeRegion) {
        this.priceStore = Objects.requireNonNull(priceStore, "priceStore cannot be null");
        this.activeRegion = ValidationUtils.requireNonBlank(activeRegion, "activeRegion");
        this.activePriceCache = new ConcurrentHashMap<>();
        this.lastSyncTimestamp = new Date();
        refreshPriceCache();
    }

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

    public PriceRecord[] getHistoricalPrices(String skuId) {
        String normalizedSku = ValidationUtils.requireNonBlank(skuId, "skuId");
        List<PriceRecord> sorted = new ArrayList<>(priceStore.findBySku(normalizedSku));
        sorted.sort(Comparator.comparing(PriceRecord::getEffectiveFrom).reversed());
        return sorted.toArray(new PriceRecord[0]);
    }

    public void refreshPriceCache() {
        activePriceCache.clear();
        for (PriceRecord record : priceStore.findAllActive()) {
            activePriceCache.put(key(record.getSkuId(), record.getRegionCode(), record.getChannel()), record);
        }
        this.lastSyncTimestamp = new Date();
        LOGGER.info(() -> "Price cache refreshed at " + lastSyncTimestamp);
    }

    public void updatePrice(BasePriceRecord record) {
        Objects.requireNonNull(record, "record cannot be null");
        Date now = new Date();
        String key = key(record.getSkuId(), record.getRegionCode(), record.getChannel());

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

    public String getActiveRegion() {
        return activeRegion;
    }

    public Date getLastSyncTimestamp() {
        return new Date(lastSyncTimestamp.getTime());
    }

    private static String key(String skuId, String region, String channel) {
        return skuId + "|" + region + "|" + channel;
    }
}
