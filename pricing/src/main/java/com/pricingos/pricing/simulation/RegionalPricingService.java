package com.pricingos.pricing.simulation;

import com.pricingos.common.ILandedCostService;
import com.pricingos.common.ValidationUtils;
import com.pricingos.pricing.db.RegionalDao;

public class RegionalPricingService implements ILandedCostService {

    public RegionalPricingService() {
        // region multipliers are seeded in DB directly, but we can save initial values if needed
        RegionalDao.saveMultiplier("GLOBAL", 1.00);
        RegionalDao.saveMultiplier("SOUTH", 1.02);
        RegionalDao.saveMultiplier("NORTH", 1.03);
        RegionalDao.saveMultiplier("EU", 1.10);
        RegionalDao.saveMultiplier("US", 1.08);
    }

    @Override
    public double getLandedCost(String skuId, String regionCode) {
        ValidationUtils.requireNonBlank(skuId, "skuId");
        String region = ValidationUtils.requireNonBlank(regionCode, "regionCode").toUpperCase();
        return RegionalDao.getMultiplier(region, 1.00);
    }

    @Override
    public double applyRegionalPricingAdjustment(String skuId, double basePrice, String regionCode) {
        ValidationUtils.requireFiniteNonNegative(basePrice, "basePrice");
        return basePrice * getLandedCost(skuId, regionCode);
    }
}
