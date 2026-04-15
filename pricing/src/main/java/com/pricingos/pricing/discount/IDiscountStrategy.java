package com.pricingos.pricing.discount;

import com.pricingos.common.OrderLineItem;

public interface IDiscountStrategy {

    boolean isEligible(OrderLineItem item, String customerId);

    double applyDiscount(double currentPrice, OrderLineItem item, String customerId);

    String getStrategyName();
}
