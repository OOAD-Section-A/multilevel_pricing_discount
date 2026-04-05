package com.pricingos.common;

/**
 * Customer tier management use cases for pricing decisions.
 */
public interface ICustomerTierService {

    /**
     * Returns effective tier, including manual override when present.
     */
    CustomerTier getTier(String customerId);

    /**
     * Returns effective discount rate derived from the effective tier.
     */
    double getDiscountRate(String customerId);

    /**
     * Re-evaluates tier from order history unless manually overridden.
     */
    void evaluateTier(String customerId);

    /**
     * Forces an explicit tier override for the given customer.
     */
    void overrideTier(String customerId, CustomerTier tier);
}
