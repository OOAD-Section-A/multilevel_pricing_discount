package com.pricingos.pricing.pricelist;

import com.pricingos.common.ValidationUtils;
import com.pricingos.pricing.baseprice.BasePriceRecord;
import com.jackfruit.scm.database.model.PriceList;
import com.scm.subsystems.MultiLevelPricingSubsystem;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class PriceListManager {

    private static final Logger LOGGER = Logger.getLogger(PriceListManager.class.getName());

    private final IPriceStore priceStore;
    private final DbPriceReader dbPriceReader;
    private final DbPricePublisher dbPricePublisher;
    private final Map<String, PriceRecord> activePriceCache;
    private String activeRegion;
    private MultiLevelPricingSubsystem exceptions;
    private Date lastSyncTimestamp;
    private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");
    
    private MultiLevelPricingSubsystem getExceptions() {
        if (exceptions == null && IS_WINDOWS) {
            try {
                exceptions = MultiLevelPricingSubsystem.INSTANCE;
            } catch (Exception e) {
                // Windows Event Viewer initialization failed
                exceptions = null;
            }
        }
        return exceptions;
    }

    public PriceListManager() {
        this(new InMemoryPriceStore(), "GLOBAL", new DbPriceReader(), new DbPricePublisher());
    }

    public PriceListManager(IPriceStore priceStore, String activeRegion) {
        this(priceStore, activeRegion, new DbPriceReader(), new DbPricePublisher());
    }

    public PriceListManager(IPriceStore priceStore, String activeRegion, DbPricePublisher dbPricePublisher) {
        this(priceStore, activeRegion, new DbPriceReader(), dbPricePublisher);
    }

    public PriceListManager(
            IPriceStore priceStore,
            String activeRegion,
            DbPriceReader dbPriceReader,
            DbPricePublisher dbPricePublisher) {
        this.priceStore = Objects.requireNonNull(priceStore, "priceStore cannot be null");
        this.dbPriceReader = Objects.requireNonNull(dbPriceReader, "dbPriceReader cannot be null");
        this.dbPricePublisher = Objects.requireNonNull(dbPricePublisher, "dbPricePublisher cannot be null");
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

        Optional<PriceList> dbPrice = dbPriceReader.findActive(normalizedSku, normalizedRegion, normalizedChannel);
        if (dbPrice.isPresent()) {
            return dbPrice.get().getBasePrice().doubleValue();
        }

        PriceRecord activeRecord = priceStore.findActive(normalizedSku, normalizedRegion, normalizedChannel)
                .filter(record -> record.getStatus() == PriceRecord.Status.ACTIVE)
                .orElseThrow(() -> {
                    try {
                        if (getExceptions() != null) {
                            exceptions.onBasePriceNotFound(normalizedSku);
                        }
                    } catch (Exception e) {
                        // Windows Event Viewer not available on Linux
                    }
                    return new NoSuchElementException(
                        "No active base price found for SKU [" + normalizedSku + "] in region [" + normalizedRegion + "].");
                });
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
                "PRICE-" + UUID.randomUUID().toString(),
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
        try {
            dbPricePublisher.publish(record);
        } catch (RuntimeException ex) {
            LOGGER.warning(() -> "Database publish skipped for skuId=" + record.getSkuId() + ": " + ex.getMessage());
        }
        LOGGER.info(() -> "AUDIT price update: priceId=" + persisted.getPriceId()
            + ", skuId=" + persisted.getSkuId()
            + ", region=" + persisted.getRegionCode()
            + ", channel=" + persisted.getChannel()
            + ", basePrice=" + persisted.getBasePrice()
            + ", floor=" + persisted.getPriceFloor()
            + ", effectiveFrom=" + persisted.getEffectiveFrom());
    }

    public void deletePrice(String priceId) throws Exception {
        ValidationUtils.requireNonBlank(priceId, "priceId");
        String sql = "DELETE FROM price_list WHERE price_id = ?";
        try (java.sql.Connection conn = com.pricingos.pricing.db.DatabaseConnectionPool.getInstance().getConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, priceId);
            int rowsDeleted = ps.executeUpdate();
            if (rowsDeleted > 0) {
                LOGGER.info("Successfully deleted price with ID: " + priceId);
                // Also remove it from active cache if it exists
                activePriceCache.values().removeIf(record -> record.getPriceId().equals(priceId));
            } else {
                LOGGER.warning("No price record found for ID: " + priceId);
            }
        }
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
