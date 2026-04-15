package com.pricingos.pricing.pricelist;

import com.pricingos.common.ValidationUtils;
import java.util.Date;
import java.util.Objects;

public final class PriceRecord {

    public enum Status {
        ACTIVE,
        INACTIVE,
        SUPERSEDED
    }

    private final String priceId;
    private final String skuId;
    private final String regionCode;
    private final String channel;
    private final String priceType;
    private final double basePrice;
    private final double priceFloor;
    private final String currencyCode;
    private final Date effectiveFrom;
    private final Date effectiveTo;
    private final Status status;

    public PriceRecord(
            String priceId,
            String skuId,
            String regionCode,
            String channel,
            String priceType,
            double basePrice,
            double priceFloor,
            String currencyCode,
            Date effectiveFrom,
            Date effectiveTo,
            Status status) {
        this.priceId = ValidationUtils.requireNonBlank(priceId, "priceId");
        this.skuId = ValidationUtils.requireNonBlank(skuId, "skuId");
        this.regionCode = ValidationUtils.requireNonBlank(regionCode, "regionCode");
        this.channel = ValidationUtils.requireNonBlank(channel, "channel");
        this.priceType = ValidationUtils.requireNonBlank(priceType, "priceType");
        ValidationUtils.requireFinitePositive(basePrice, "basePrice");
        if (!Double.isFinite(priceFloor) || priceFloor <= 0.0 || priceFloor >= basePrice) {
            throw new IllegalArgumentException("priceFloor must be positive and strictly lower than basePrice");
        }
        this.basePrice = basePrice;
        this.priceFloor = priceFloor;
        this.currencyCode = ValidationUtils.requireNonBlank(currencyCode, "currencyCode");
        this.effectiveFrom = new Date(Objects.requireNonNull(effectiveFrom, "effectiveFrom cannot be null").getTime());
        this.effectiveTo = effectiveTo == null ? null : new Date(effectiveTo.getTime());
        this.status = Objects.requireNonNull(status, "status cannot be null");
    }

    public String getPriceId() {
        return priceId;
    }

    public String getSkuId() {
        return skuId;
    }

    public String getRegionCode() {
        return regionCode;
    }

    public String getChannel() {
        return channel;
    }

    public String getPriceType() {
        return priceType;
    }

    public double getBasePrice() {
        return basePrice;
    }

    public double getPriceFloor() {
        return priceFloor;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public Date getEffectiveFrom() {
        return new Date(effectiveFrom.getTime());
    }

    public Date getEffectiveTo() {
        return effectiveTo == null ? null : new Date(effectiveTo.getTime());
    }

    public Status getStatus() {
        return status;
    }

    public PriceRecord withStatus(Status newStatus, Date newEffectiveTo) {
        return new PriceRecord(
                this.priceId,
                this.skuId,
                this.regionCode,
                this.channel,
                this.priceType,
                this.basePrice,
                this.priceFloor,
                this.currencyCode,
                this.effectiveFrom,
                newEffectiveTo,
                newStatus);
    }

}
