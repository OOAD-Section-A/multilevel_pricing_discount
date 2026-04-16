package com.pricingos.pricing.pricelist;

import com.jackfruit.scm.database.facade.SupplyChainDatabaseFacade;
import com.jackfruit.scm.database.model.PriceList;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class DbPriceReader {

    public Optional<PriceList> findActive(String skuId, String region, String channel) {
        try (SupplyChainDatabaseFacade facade = new SupplyChainDatabaseFacade()) {
            List<PriceList> prices = facade.pricing().listPrices();
            return prices.stream()
                .filter(p -> skuId.equals(p.getSkuId()))
                .filter(p -> region.equals(p.getRegionCode()))
                .filter(p -> channel.equals(p.getChannel()))
                .filter(p -> "ACTIVE".equalsIgnoreCase(p.getStatus()))
                .max(Comparator.comparing(PriceList::getEffectiveFrom, Comparator.nullsLast(Comparator.naturalOrder())));
        }
    }
}
