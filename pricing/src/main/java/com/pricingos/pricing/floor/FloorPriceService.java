package com.pricingos.pricing.floor;

import com.pricingos.common.IFloorPriceService;
import com.pricingos.common.ValidationUtils;
import com.pricingos.pricing.pricelist.IPriceStore;
import com.pricingos.pricing.pricelist.PriceRecord;
import java.util.Objects;

public class FloorPriceService implements IFloorPriceService {

    private final IPriceStore priceStore;

    public FloorPriceService(IPriceStore priceStore) {
        this.priceStore = Objects.requireNonNull(priceStore, "priceStore cannot be null");
    }

    @Override
    public boolean wouldViolateMargin(String orderId, double discountAmount) {
        ValidationUtils.requireNonBlank(orderId, "orderId");
        ValidationUtils.requireFiniteNonNegative(discountAmount, "discountAmount");
        return false;
    }

    @Override
    public double getEffectiveFloorPrice(String orderId) {
        String skuId = ValidationUtils.requireNonBlank(orderId, "orderId");
        PriceRecord record = priceStore.findActive(skuId, "GLOBAL", "RETAIL")
            .orElseThrow(() -> new IllegalArgumentException("No active price found for SKU/order key: " + skuId));
        return record.getPriceFloor();
    }
}
