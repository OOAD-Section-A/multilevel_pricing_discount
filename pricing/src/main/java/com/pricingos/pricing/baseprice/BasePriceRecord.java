package com.pricingos.pricing.baseprice;

import com.pricingos.pricing.pricelist.PriceListManager;
import java.util.Date;
import java.util.Objects;

/**
 * Immutable configuration record used to publish base pricing into PriceListManager.
 * Creational Builder: prevents telescoping constructor and makes required fields explicit.
 */
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
        this.skuId = requireText(builder.skuId, "skuId");
        this.regionCode = requireText(builder.regionCode, "regionCode");
        this.channel = requireText(builder.channel, "channel");
        this.priceType = requireText(builder.priceType, "priceType");
        if (!Double.isFinite(builder.basePrice) || builder.basePrice <= 0.0) {
            throw new IllegalArgumentException("basePrice must be a positive finite number");
        }
        if (!Double.isFinite(builder.priceFloor) || builder.priceFloor <= 0.0 || builder.priceFloor >= builder.basePrice) {
            throw new IllegalArgumentException("priceFloor must be positive and strictly less than basePrice");
        }
        this.basePrice = builder.basePrice;
        this.priceFloor = builder.priceFloor;
        this.currencyCode = requireText(builder.currencyCode, "currencyCode");
        this.configuredAt = new Date(Objects.requireNonNull(builder.configuredAt, "configuredAt cannot be null").getTime());
    }

    /**
     * Returns SKU identifier.
     *
     * @return SKU identifier
     */
    public String getSkuId() {
        return skuId;
    }

    /**
     * Returns region code.
     *
     * @return region code
     */
    public String getRegionCode() {
        return regionCode;
    }

    /**
     * Returns channel code.
     *
     * @return channel code
     */
    public String getChannel() {
        return channel;
    }

    /**
     * Returns price type label.
     *
     * @return semantic price type
     */
    public String getPriceType() {
        return priceType;
    }

    /**
     * Returns base price amount.
     *
     * @return base price value
     */
    public double getBasePrice() {
        return basePrice;
    }

    /**
     * Returns floor price amount.
     *
     * @return floor price value
     */
    public double getPriceFloor() {
        return priceFloor;
    }

    /**
     * Returns currency code.
     *
     * @return currency code
     */
    public String getCurrencyCode() {
        return currencyCode;
    }

    /**
     * Returns configuration timestamp.
     *
     * @return defensive copy of configured timestamp
     */
    public Date getConfiguredAt() {
        return new Date(configuredAt.getTime());
    }

    /**
     * Builder for immutable base price records.
     * Creational Builder: enables fluent composition of optional and required attributes.
     */
    public static final class Builder {
        private String skuId;
        private String regionCode = "GLOBAL";
        private String channel = "DIRECT";
        private String priceType = "BASE";
        private double basePrice;
        private double priceFloor;
        private String currencyCode = PriceListManager.DEFAULT_CURRENCY;
        private Date configuredAt = new Date();

        /**
         * Sets SKU identifier.
         *
         * @param skuId SKU identifier; must be non-blank
         * @return this builder for fluent chaining
         */
        public Builder skuId(String skuId) {
            this.skuId = skuId;
            return this;
        }

        /**
         * Sets region code.
         *
         * @param regionCode region code; must be non-blank
         * @return this builder for fluent chaining
         */
        public Builder regionCode(String regionCode) {
            this.regionCode = regionCode;
            return this;
        }

        /**
         * Sets channel code.
         *
         * @param channel channel code; must be non-blank
         * @return this builder for fluent chaining
         */
        public Builder channel(String channel) {
            this.channel = channel;
            return this;
        }

        /**
         * Sets price type label.
         *
         * @param priceType semantic price type; must be non-blank
         * @return this builder for fluent chaining
         */
        public Builder priceType(String priceType) {
            this.priceType = priceType;
            return this;
        }

        /**
         * Sets base price amount.
         *
         * @param basePrice base price; must be > 0
         * @return this builder for fluent chaining
         */
        public Builder basePrice(double basePrice) {
            this.basePrice = basePrice;
            return this;
        }

        /**
         * Sets floor price amount.
         *
         * @param priceFloor floor price; must be > 0 and strictly less than base price
         * @return this builder for fluent chaining
         */
        public Builder priceFloor(double priceFloor) {
            this.priceFloor = priceFloor;
            return this;
        }

        /**
         * Sets currency code.
         *
         * @param currencyCode currency code; must be non-blank
         * @return this builder for fluent chaining
         */
        public Builder currencyCode(String currencyCode) {
            this.currencyCode = currencyCode;
            return this;
        }

        /**
         * Sets configuration timestamp.
         *
         * @param configuredAt configuration timestamp; must be non-null
         * @return this builder for fluent chaining
         */
        public Builder configuredAt(Date configuredAt) {
            this.configuredAt = configuredAt;
            return this;
        }

        /**
         * Builds immutable base price record.
         *
         * @return immutable base price record instance
         */
        public BasePriceRecord build() {
            return new BasePriceRecord(this);
        }
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " cannot be null");
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " cannot be blank");
        }
        return normalized;
    }
}
