package com.pricingos.common;

public interface IDynamicPricingService {

    double adjustBasePrice(String skuId, double currentBasePrice);
}
