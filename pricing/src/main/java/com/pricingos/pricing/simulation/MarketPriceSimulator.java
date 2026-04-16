package com.pricingos.pricing.simulation;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;

public class MarketPriceSimulator {

    private final AtomicReference<Double> commodityIndex = new AtomicReference<>(1.0);

    public double currentIndex() {
        return commodityIndex.get();
    }

    public double tick() {
        double driftPct = ThreadLocalRandom.current().nextDouble(-0.02, 0.02);
        return commodityIndex.updateAndGet(current -> Math.max(0.5, current * (1.0 + driftPct)));
    }
}
