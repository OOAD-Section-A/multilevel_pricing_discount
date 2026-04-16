package com.pricingos.pricing.pricelist;

import com.jackfruit.scm.database.model.PriceList;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PriceListManagerDbReadTest {

    @Test
    void getActivePrice_prefersDbPriceWhenAvailable() {
        InMemoryPriceStore store = new InMemoryPriceStore();
        DbPriceReader dbReader = new DbPriceReader() {
            @Override
            public Optional<PriceList> findActive(String skuId, String region, String channel) {
                return Optional.of(new PriceList(
                    "P-1",
                    skuId,
                    region,
                    channel,
                    "RETAIL",
                    BigDecimal.valueOf(321.0),
                    BigDecimal.valueOf(300.0),
                    "INR",
                    LocalDateTime.now(),
                    null,
                    "ACTIVE"
                ));
            }
        };
        DbPricePublisher noOpPublisher = new DbPricePublisher() {
            @Override
            public void publish(com.pricingos.pricing.baseprice.BasePriceRecord record) {
            }
        };
        PriceListManager manager = new PriceListManager(store, "GLOBAL", dbReader, noOpPublisher);

        assertEquals(321.0, manager.getActivePrice("SKU-X", "GLOBAL", "RETAIL"), 0.001);
    }
}
