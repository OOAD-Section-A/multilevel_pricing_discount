package com.pricingos.common;

public interface ICustomerTierService {

    CustomerTier getTier(String customerId);

    double getDiscountRate(String customerId);

    void evaluateTier(String customerId);

    void overrideTier(String customerId, CustomerTier tier);
}
