package com.pricingos.common;

public interface IDiscountRulesEngine {

    PriceResult[] calculateFinalPrice(OrderLineItem[] cart, String customerId);

    boolean submitPricingOverride(PricingOverrideRequest request);
}
