package com.pricingos.common;

/**
 * Service interface for landed cost and regional pricing adjustments.
 * This is a STUB interface — implementation is provided by Aniruddha's subsystem.
 *
 * <p>Landed cost includes transportation, tariffs, duties, and regional pricing factors
 * that vary by location and shipping method.
 */
public interface ILandedCostService {

    /**
     * Retrieves the total landed cost for a SKU in a specific region.
     * This is the fully-loaded cost including all shipping, duties, and regional fees.
     *
     * @param skuId       the product ID
     * @param regionCode  the region code (e.g., "IN-MH", "US-CA")
     * @return the landed cost, or 0.0 if not available
     */
    double getLandedCost(String skuId, String regionCode);

    /**
     * Applies regional pricing adjustments to a base price.
     * Factors in regional costs, local pricing strategies, and regional competitive dynamics.
     *
     * @param skuId       the product ID
     * @param basePrice   the base price before regional adjustment
     * @param regionCode  the region code (e.g., "IN-MH", "US-CA")
     * @return the price after regional adjustment, or basePrice if region not found
     */
    double applyRegionalPricingAdjustment(String skuId, double basePrice, String regionCode);
}
