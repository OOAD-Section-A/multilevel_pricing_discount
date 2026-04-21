package com.pricingos.pricing.pricelist;

import com.jackfruit.scm.database.model.PriceList;
import com.pricingos.pricing.baseprice.BasePriceRecord;
import com.pricingos.pricing.db.DatabaseModuleSupport;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

public class DbPricePublisher {

    private static final LocalDateTime FAR_FUTURE = LocalDateTime.of(9999, 12, 31, 23, 59, 59);

    public void publish(BasePriceRecord record) {
        LocalDateTime effectiveFrom = LocalDateTime.ofInstant(
                record.getConfiguredAt().toInstant(),
                ZoneId.systemDefault());

        DatabaseModuleSupport.usePricingAdapter(adapter -> {
            List<PriceList> existingPrices = adapter.getPricesBySku(record.getSkuId());
            for (PriceList existingPrice : existingPrices) {
                if (!record.getRegionCode().equals(existingPrice.getRegionCode())) {
                    continue;
                }
                if (!record.getChannel().equals(existingPrice.getChannel())) {
                    continue;
                }
                if (!record.getPriceType().equals(existingPrice.getPriceType())) {
                    continue;
                }
                if ("ACTIVE".equalsIgnoreCase(existingPrice.getStatus())) {
                    adapter.updatePriceStatus(existingPrice.getPriceId(), "SUPERSEDED");
                }
            }

            adapter.publishPrice(new PriceList(
                    UUID.randomUUID().toString(),
                    record.getSkuId(),
                    record.getRegionCode(),
                    record.getChannel(),
                    record.getPriceType(),
                    BigDecimal.valueOf(record.getBasePrice()),
                    BigDecimal.valueOf(record.getPriceFloor()),
                    record.getCurrencyCode(),
                    effectiveFrom,
                    FAR_FUTURE,
                    "ACTIVE"));
        });
    }
}
