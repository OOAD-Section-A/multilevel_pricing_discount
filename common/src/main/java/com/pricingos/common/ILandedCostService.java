package com.pricingos.common;

public interface ILandedCostService {

    double getLandedCost(String skuId, String regionCode);

    double applyRegionalPricingAdjustment(String skuId, double basePrice, String regionCode);
}
