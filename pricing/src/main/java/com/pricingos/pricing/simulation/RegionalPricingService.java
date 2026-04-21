package com.pricingos.pricing.simulation;

import com.pricingos.common.ILandedCostService;
import com.pricingos.common.ValidationUtils;
import com.jackfruit.scm.database.adapter.PricingAdapter;
import com.jackfruit.scm.database.model.PricingModels.RegionalPricingMultiplier;
import java.math.BigDecimal;
import java.util.Objects;

public class RegionalPricingService implements ILandedCostService {
    
    private final PricingAdapter pricingAdapter;

    public RegionalPricingService(PricingAdapter pricingAdapter) {
        this.pricingAdapter = Objects.requireNonNull(pricingAdapter, "pricingAdapter cannot be null");
        
        // Initialize region multipliers in database if they don't already exist
        initializeRegionalMultipliers();
    }
    
    private void initializeRegionalMultipliers() {
        RegionalPricingMultiplier[] multipliers = {
            new RegionalPricingMultiplier("GLOBAL", BigDecimal.valueOf(1.00)),
            new RegionalPricingMultiplier("SOUTH", BigDecimal.valueOf(1.02)),
            new RegionalPricingMultiplier("NORTH", BigDecimal.valueOf(1.03)),
            new RegionalPricingMultiplier("EU", BigDecimal.valueOf(1.10)),
            new RegionalPricingMultiplier("US", BigDecimal.valueOf(1.08))
        };
        
        for (RegionalPricingMultiplier multiplier : multipliers) {
            try {
                // Check if multiplier already exists
                if (pricingAdapter.getRegionalMultiplier(multiplier.regionCode()).isEmpty()) {
                    pricingAdapter.createRegionalPricingMultiplier(multiplier);
                }
            } catch (Exception e) {
                // If it already exists or any other database error, skip creation
                System.err.println("Could not create regional multiplier " + multiplier.regionCode() + ": " + e.getMessage());
            }
        }
    }

    @Override
    public double getLandedCost(String skuId, String regionCode) {
        ValidationUtils.requireNonBlank(skuId, "skuId");
        String region = ValidationUtils.requireNonBlank(regionCode, "regionCode").toUpperCase();
        
        var multiplier = pricingAdapter.getRegionalMultiplier(region);
        return multiplier.isPresent() ? multiplier.get().multiplier().doubleValue() : 1.00;
    }

    @Override
    public double applyRegionalPricingAdjustment(String skuId, double basePrice, String regionCode) {
        ValidationUtils.requireFiniteNonNegative(basePrice, "basePrice");
        return basePrice * getLandedCost(skuId, regionCode);
    }
}
