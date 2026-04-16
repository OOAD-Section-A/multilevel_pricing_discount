package com.pricingos.pricing.simulation;

import com.pricingos.common.IDynamicPricingService;
import com.pricingos.common.ValidationUtils;

public class DynamicPricingEngine implements IDynamicPricingService {

    private final MarketPriceSimulator marketPriceSimulator;

    public DynamicPricingEngine(MarketPriceSimulator marketPriceSimulator) {
        this.marketPriceSimulator = marketPriceSimulator;
    }

    @Override
    public double adjustBasePrice(String skuId, double currentBasePrice) {
        ValidationUtils.requireNonBlank(skuId, "skuId");
        ValidationUtils.requireFiniteNonNegative(currentBasePrice, "currentBasePrice");
        double marketIndex = marketPriceSimulator.currentIndex();
        return currentBasePrice * marketIndex;
    }
}
