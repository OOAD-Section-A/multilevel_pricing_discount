package com.pricingos.pricing.pricelist;

/**
 * Observer contract for reacting to price updates in the price list manager.
 * Behavioural Observer: decouples update side-effects from versioning logic.
 */
public interface IPriceUpdateListener {

    /**
     * Receives notification after a new active price record is persisted.
     *
     * @param record the newly activated immutable price record; never null
     * @return no return value
     */
    void onPriceUpdated(PriceRecord record);
}
