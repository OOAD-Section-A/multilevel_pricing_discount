package com.pricingos.pricing.simulation;

import com.pricingos.common.ILandedCostService;
import com.pricingos.common.ValidationUtils;
import com.pricingos.pricing.db.RegionalDao;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RegionalPricingService implements ILandedCostService {

    private final RegionalMultiplierStore multiplierStore;

    public RegionalPricingService() {
        this(new DatabaseRegionalMultiplierStore());
    }

    RegionalPricingService(RegionalMultiplierStore multiplierStore) {
        this.multiplierStore = multiplierStore;
        seedDefaults();
    }

    @Override
    public double getLandedCost(String skuId, String regionCode) {
        ValidationUtils.requireNonBlank(skuId, "skuId");
        String region = ValidationUtils.requireNonBlank(regionCode, "regionCode").toUpperCase();
        return multiplierStore.getMultiplier(region, 1.00);
    }

    @Override
    public double applyRegionalPricingAdjustment(String skuId, double basePrice, String regionCode) {
        ValidationUtils.requireFiniteNonNegative(basePrice, "basePrice");
        return basePrice * getLandedCost(skuId, regionCode);
    }

    interface RegionalMultiplierStore {
        double getMultiplier(String regionCode, double defaultValue);
        void saveMultiplier(String regionCode, double multiplier);
    }

    static final class InMemoryRegionalMultiplierStore implements RegionalMultiplierStore {
        private final Map<String, Double> multipliers = new ConcurrentHashMap<>();

        @Override
        public double getMultiplier(String regionCode, double defaultValue) {
            return multipliers.getOrDefault(regionCode, defaultValue);
        }

        @Override
        public void saveMultiplier(String regionCode, double multiplier) {
            multipliers.put(regionCode, multiplier);
        }
    }

    private static final class DatabaseRegionalMultiplierStore implements RegionalMultiplierStore {
        @Override
        public double getMultiplier(String regionCode, double defaultValue) {
            return RegionalDao.getMultiplier(regionCode, defaultValue);
        }

        @Override
        public void saveMultiplier(String regionCode, double multiplier) {
            RegionalDao.saveMultiplier(regionCode, multiplier);
        }
    }

    private void seedDefaults() {
        multiplierStore.saveMultiplier("GLOBAL", 1.00);
        multiplierStore.saveMultiplier("SOUTH", 1.02);
        multiplierStore.saveMultiplier("NORTH", 1.03);
        multiplierStore.saveMultiplier("EU", 1.10);
        multiplierStore.saveMultiplier("US", 1.08);
    }
}
