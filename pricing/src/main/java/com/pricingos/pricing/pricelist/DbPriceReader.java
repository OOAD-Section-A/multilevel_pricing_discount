package com.pricingos.pricing.pricelist;

import com.jackfruit.scm.database.model.PriceList;
import com.pricingos.pricing.db.DatabaseModuleSupport;
import java.util.Comparator;
import java.util.Optional;

public class DbPriceReader {

    public Optional<PriceList> findActive(String skuId, String region, String channel) {
        return DatabaseModuleSupport.withPricingAdapter(adapter ->
                adapter.getPricesBySku(skuId).stream()
                        .filter(price -> region.equals(price.getRegionCode()))
                        .filter(price -> channel.equals(price.getChannel()))
                        .filter(price -> "ACTIVE".equalsIgnoreCase(price.getStatus()))
                        .max(Comparator.comparing(
                                PriceList::getEffectiveFrom,
                                Comparator.nullsLast(Comparator.naturalOrder()))));
    }
}
