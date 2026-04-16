package com.pricingos.pricing.simulation;

import com.pricingos.common.ILandedCostService;
import com.pricingos.common.ValidationUtils;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RegionalPricingService implements ILandedCostService {

    private final Map<String, Double> regionMultiplier = new ConcurrentHashMap<>();

    public RegionalPricingService() {
        regionMultiplier.put("GLOBAL", 1.00);
        regionMultiplier.put("SOUTH", 1.02);
        regionMultiplier.put("NORTH", 1.03);
        regionMultiplier.put("EU", 1.10);
        regionMultiplier.put("US", 1.08);
    }

    @Override
    public double getLandedCost(String skuId, String regionCode) {
        ValidationUtils.requireNonBlank(skuId, "skuId");
        String region = ValidationUtils.requireNonBlank(regionCode, "regionCode").toUpperCase();
        return regionMultiplier.getOrDefault(region, 1.00);
    }

    @Override
    public double applyRegionalPricingAdjustment(String skuId, double basePrice, String regionCode) {
        ValidationUtils.requireFiniteNonNegative(basePrice, "basePrice");
        return basePrice * getLandedCost(skuId, regionCode);
    }
}
