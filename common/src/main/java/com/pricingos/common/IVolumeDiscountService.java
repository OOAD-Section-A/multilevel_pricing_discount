package com.pricingos.common;

import java.util.List;

public interface IVolumeDiscountService {

    String createVolumePromotion(String skuId, List<VolumeTierRule> tiers);

    double getDiscountedUnitPrice(String skuId, int quantity, double baseUnitPrice);

    double getLineTotal(String skuId, int quantity, double baseUnitPrice);

    boolean hasVolumePromotion(String skuId);
}
