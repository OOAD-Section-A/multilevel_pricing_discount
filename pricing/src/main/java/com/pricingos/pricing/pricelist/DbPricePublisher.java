package com.pricingos.pricing.pricelist;

import com.jackfruit.scm.database.adapter.PricingAdapter;
import com.jackfruit.scm.database.facade.SupplyChainDatabaseFacade;
import com.jackfruit.scm.database.model.PriceList;
import com.pricingos.pricing.baseprice.BasePriceRecord;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public class DbPricePublisher {

    public void publish(BasePriceRecord record) {
        try (SupplyChainDatabaseFacade facade = new SupplyChainDatabaseFacade()) {
            PricingAdapter adapter = new PricingAdapter(facade);
            adapter.publishPrice(new PriceList(
                UUID.randomUUID().toString(),
                record.getSkuId(),
                record.getRegionCode(),
                record.getChannel(),
                record.getPriceType(),
                BigDecimal.valueOf(record.getBasePrice()),
                BigDecimal.valueOf(record.getPriceFloor()),
                record.getCurrencyCode(),
                LocalDateTime.now(),
                null,
                "ACTIVE"
            ));
        }
    }
    public void delete(String priceId) {
        try (SupplyChainDatabaseFacade facade = new SupplyChainDatabaseFacade()) {
            PricingAdapter adapter = new PricingAdapter(facade);
            adapter.deletePrice(priceId);
        }
    }
}
