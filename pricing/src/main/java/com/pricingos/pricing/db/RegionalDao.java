package com.pricingos.pricing.db;

import com.jackfruit.scm.database.model.PricingModels.RegionalPricingMultiplier;
import java.math.BigDecimal;

public class RegionalDao {

    public static double getMultiplier(String regionCode, double defaultValue) {
        return DatabaseModuleSupport.withPricingAdapter(adapter ->
                adapter.getRegionalMultiplier(regionCode)
                        .map(RegionalPricingMultiplier::multiplier)
                        .map(BigDecimal::doubleValue)
                        .orElse(defaultValue));
    }

    public static void saveMultiplier(String regionCode, double multiplier) {
        DatabaseModuleSupport.usePricingAdapter(adapter -> {
            adapter.getRegionalMultiplier(regionCode)
                    .ifPresent(existing -> adapter.deleteRegionalPricingMultiplier(regionCode));
            adapter.createRegionalPricingMultiplier(
                    new RegionalPricingMultiplier(regionCode, BigDecimal.valueOf(multiplier)));
        });
    }
}
