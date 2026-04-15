package com.pricingos.common;

public interface IFloorPriceService {

    boolean wouldViolateMargin(String orderId, double discountAmount);

    double getEffectiveFloorPrice(String orderId);
}
