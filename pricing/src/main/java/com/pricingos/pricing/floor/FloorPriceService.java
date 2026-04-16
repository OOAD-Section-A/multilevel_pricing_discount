package com.pricingos.pricing.floor;

import com.pricingos.common.IFloorPriceService;
import com.pricingos.common.ValidationUtils;
import com.pricingos.pricing.pricelist.IPriceStore;
import com.pricingos.pricing.pricelist.PriceRecord;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class FloorPriceService implements IFloorPriceService {

    private final IPriceStore priceStore;

    public FloorPriceService(IPriceStore priceStore) {
        this.priceStore = Objects.requireNonNull(priceStore, "priceStore cannot be null");
    }

    @Override
    public boolean wouldViolateMargin(String orderId, double discountAmount) {
        String pricingKey = ValidationUtils.requireNonBlank(orderId, "orderId");
        double normalizedDiscount = ValidationUtils.requireFiniteNonNegative(discountAmount, "discountAmount");

        PriceRecord record = resolveRecord(pricingKey);
        if (record == null) {
            return false;
        }
        double maxAllowedDiscount = record.getBasePrice() - record.getPriceFloor();
        return normalizedDiscount > maxAllowedDiscount;
    }

    @Override
    public double getEffectiveFloorPrice(String orderId) {
        String pricingKey = ValidationUtils.requireNonBlank(orderId, "orderId");
        PriceRecord record = resolveRecord(pricingKey);
        if (record == null) {
            throw new IllegalArgumentException("No active price found for SKU/order key: " + pricingKey);
        }
        return record.getPriceFloor();
    }

    private PriceRecord resolveRecord(String pricingKey) {
        List<PriceRecord> records = priceStore.findBySku(pricingKey);
        if (records.isEmpty()) {
            return null;
        }
        return records.stream()
            .filter(record -> record.getStatus() == PriceRecord.Status.ACTIVE)
            .max(Comparator.comparing(PriceRecord::getEffectiveFrom))
            .orElseGet(() -> records.stream().max(Comparator.comparing(PriceRecord::getEffectiveFrom)).orElse(null));
    }
}
