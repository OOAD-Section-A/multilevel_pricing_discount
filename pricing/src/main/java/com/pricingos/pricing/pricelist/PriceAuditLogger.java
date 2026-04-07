package com.pricingos.pricing.pricelist;

import java.util.logging.Logger;

/**
 * Lightweight observer that logs all price updates for auditing.
 * Behavioural Observer: concrete listener for update events.
 */
public class PriceAuditLogger implements IPriceUpdateListener {

    private static final Logger LOGGER = Logger.getLogger(PriceAuditLogger.class.getName());

    /**
     * Logs immutable record details when a price is updated.
     *
     * @param record newly activated record; never null
     * @return no return value
     */
    @Override
    public void onPriceUpdated(PriceRecord record) {
        LOGGER.info(() -> "AUDIT price update: priceId=" + record.getPriceId()
                + ", skuId=" + record.getSkuId()
                + ", region=" + record.getRegionCode()
                + ", channel=" + record.getChannel()
                + ", basePrice=" + record.getBasePrice()
                + ", floor=" + record.getPriceFloor()
                + ", effectiveFrom=" + record.getEffectiveFrom());
    }
}
