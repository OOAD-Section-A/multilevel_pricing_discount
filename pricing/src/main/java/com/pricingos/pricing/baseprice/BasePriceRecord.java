package com.pricingos.pricing.baseprice;

import com.pricingos.common.ValidationUtils;
import java.util.Date;
import java.util.Objects;

public final class BasePriceRecord {
    private final String skuId;
    private final String regionCode;
    private final String channel;
    private final String priceType;
    private final double basePrice;
    private final double priceFloor;
    private final String currencyCode;
    private final Date configuredAt;

    private BasePriceRecord(Builder builder) {
        this.skuId = ValidationUtils.requireNonBlank(builder.skuId, "skuId");
        this.regionCode = ValidationUtils.requireNonBlank(builder.regionCode, "regionCode");
        this.channel = ValidationUtils.requireNonBlank(builder.channel, "channel");
        this.priceType = ValidationUtils.requireNonBlank(builder.priceType, "priceType");
        ValidationUtils.requireFinitePositive(builder.basePrice, "basePrice");
        if (!Double.isFinite(builder.priceFloor) || builder.priceFloor <= 0.0 || builder.priceFloor >= builder.basePrice) {
            throw new IllegalArgumentException("priceFloor must be positive and strictly less than basePrice");
        }
        this.basePrice = builder.basePrice;
        this.priceFloor = builder.priceFloor;
        this.currencyCode = ValidationUtils.requireNonBlank(builder.currencyCode, "currencyCode");
        this.configuredAt = new Date(Objects.requireNonNull(builder.configuredAt, "configuredAt cannot be null").getTime());
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

    public Date getConfiguredAt() {
        return new Date(configuredAt.getTime());
    }

    public static final class Builder {
        private String skuId;
        private String regionCode = "GLOBAL";
        private String channel = "DIRECT";
        private String priceType = "BASE";
        private double basePrice;
        private double priceFloor;
        private String currencyCode = "INR";
        private Date configuredAt = new Date();

        public Builder skuId(String skuId) {
            this.skuId = skuId;
            return this;
        }

        public Builder regionCode(String regionCode) {
            this.regionCode = regionCode;
            return this;
        }

        public Builder channel(String channel) {
            this.channel = channel;
            return this;
        }

        public Builder priceType(String priceType) {
            this.priceType = priceType;
            return this;
        }

        public Builder basePrice(double basePrice) {
            this.basePrice = basePrice;
            return this;
        }

        public Builder priceFloor(double priceFloor) {
            this.priceFloor = priceFloor;
            return this;
        }

        public Builder currencyCode(String currencyCode) {
            this.currencyCode = currencyCode;
            return this;
        }

        public Builder configuredAt(Date configuredAt) {
            this.configuredAt = configuredAt;
            return this;
        }

        public BasePriceRecord build() {
            return new BasePriceRecord(this);
        }
    }
}
