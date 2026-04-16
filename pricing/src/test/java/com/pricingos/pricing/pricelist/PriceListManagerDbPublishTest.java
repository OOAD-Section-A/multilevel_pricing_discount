package com.pricingos.pricing.pricelist;

import com.pricingos.pricing.baseprice.BasePriceRecord;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PriceListManagerDbPublishTest {

    private static final DbPriceReader NO_DB_READER = new DbPriceReader() {
        @Override
        public java.util.Optional<com.jackfruit.scm.database.model.PriceList> findActive(String skuId, String region, String channel) {
            return java.util.Optional.empty();
        }
    };

    @Test
    void updatePrice_writesInMemoryAndCallsDbPublisher() {
        InMemoryPriceStore store = new InMemoryPriceStore();
        int[] publishCount = {0};
        DbPricePublisher publisher = new DbPricePublisher() {
            @Override
            public void publish(BasePriceRecord record) {
                publishCount[0]++;
            }
        };
        PriceListManager manager = new PriceListManager(store, "GLOBAL", NO_DB_READER, publisher);

        BasePriceRecord record = new BasePriceRecord.Builder()
            .skuId("SKU-1")
            .regionCode("GLOBAL")
            .channel("RETAIL")
            .priceType("RETAIL")
            .basePrice(100.0)
            .priceFloor(80.0)
            .currencyCode("INR")
            .build();

        manager.updatePrice(record);

        assertEquals(1, publishCount[0]);
        assertEquals(100.0, manager.getActivePrice("SKU-1", "GLOBAL", "RETAIL"), 0.001);
    }

    @Test
    void updatePrice_whenDbPublisherFails_stillKeepsInMemoryPrice() {
        InMemoryPriceStore store = new InMemoryPriceStore();
        DbPricePublisher publisher = new DbPricePublisher() {
            @Override
            public void publish(BasePriceRecord record) {
                throw new RuntimeException("db offline");
            }
        };
        PriceListManager manager = new PriceListManager(store, "GLOBAL", NO_DB_READER, publisher);

        BasePriceRecord record = new BasePriceRecord.Builder()
            .skuId("SKU-2")
            .regionCode("GLOBAL")
            .channel("RETAIL")
            .priceType("RETAIL")
            .basePrice(200.0)
            .priceFloor(150.0)
            .currencyCode("INR")
            .build();

        assertDoesNotThrow(() -> manager.updatePrice(record));
        assertEquals(200.0, manager.getActivePrice("SKU-2", "GLOBAL", "RETAIL"), 0.001);
    }
}
