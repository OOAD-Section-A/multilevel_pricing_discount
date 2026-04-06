package com.pricingos.pricing.pricelist;

import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * Storage abstraction for price records.
 * SOLID DIP: manager depends on this interface, not concrete map storage.
 */
public interface IPriceStore {

    /**
     * Finds the currently active record for SKU, region, and channel.
     *
     * @param skuId SKU identifier; must be non-blank
     * @param region region code; must be non-blank
     * @param channel channel code; must be non-blank
     * @return optional active record when present
     */
    Optional<PriceRecord> findActive(String skuId, String region, String channel);

    /**
     * Returns all historical versions for the given SKU.
     *
     * @param skuId SKU identifier; must be non-blank
     * @return list of historical and active records, possibly empty
     */
    List<PriceRecord> findBySku(String skuId);

    /**
     * Returns all active records across keys.
     *
     * @return list of active records, possibly empty
     */
    List<PriceRecord> findAllActive();

    /**
     * Persists a new immutable record.
     *
     * @param record immutable record to persist; must be non-null
     * @return no return value
     */
    void save(PriceRecord record);

    /**
     * Marks currently active record as superseded for this key.
     *
     * @param skuId SKU identifier; must be non-blank
     * @param region region code; must be non-blank
     * @param channel channel code; must be non-blank
     * @param effectiveTo end timestamp for superseded record; must be non-null
     * @return no return value
     */
    void markActiveAsSuperseded(String skuId, String region, String channel, Date effectiveTo);

    /**
     * Clears all records from the store.
     *
     * @return no return value
     */
    void clear();
}
