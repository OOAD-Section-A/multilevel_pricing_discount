package com.pricingos.pricing.pricelist;

import java.util.Date;
import java.util.Objects;

/**
 * Immutable price DTO representing one versioned price entry.
 * GRASP Information Expert: this object encapsulates versioned price state.
 */
public final class PriceRecord {

    /**
     * Status of a versioned price record.
     */
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

    /**
     * Creates a new immutable versioned price record.
     *
     * @param priceId unique record identifier; must be non-blank
     * @param skuId SKU identifier; must be non-blank
     * @param regionCode region code; must be non-blank
     * @param channel sales channel; must be non-blank
     * @param priceType semantic type of price (for example BASE); must be non-blank
     * @param basePrice configured base price; must be positive
     * @param priceFloor configured floor price; must be positive and lower than basePrice
     * @param currencyCode ISO-like currency code; must be non-blank
     * @param effectiveFrom record start timestamp; must be non-null
     * @param effectiveTo optional record end timestamp; nullable
     * @param status lifecycle status; must be non-null
     */
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
        this.priceId = requireText(priceId, "priceId");
        this.skuId = requireText(skuId, "skuId");
        this.regionCode = requireText(regionCode, "regionCode");
        this.channel = requireText(channel, "channel");
        this.priceType = requireText(priceType, "priceType");
        if (!Double.isFinite(basePrice) || basePrice <= 0.0) {
            throw new IllegalArgumentException("basePrice must be a positive finite number");
        }
        if (!Double.isFinite(priceFloor) || priceFloor <= 0.0 || priceFloor >= basePrice) {
            throw new IllegalArgumentException("priceFloor must be positive and strictly lower than basePrice");
        }
        this.basePrice = basePrice;
        this.priceFloor = priceFloor;
        this.currencyCode = requireText(currencyCode, "currencyCode");
        this.effectiveFrom = new Date(Objects.requireNonNull(effectiveFrom, "effectiveFrom cannot be null").getTime());
        this.effectiveTo = effectiveTo == null ? null : new Date(effectiveTo.getTime());
        this.status = Objects.requireNonNull(status, "status cannot be null");
    }

    /**
     * Returns unique record identifier.
     *
     * @return unique price record identifier
     */
    public String getPriceId() {
        return priceId;
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
     * Returns region code for this price.
     *
     * @return region code
     */
    public String getRegionCode() {
        return regionCode;
    }

    /**
     * Returns sales channel.
     *
     * @return channel code
     */
    public String getChannel() {
        return channel;
    }

    /**
     * Returns semantic price type label.
     *
     * @return price type label
     */
    public String getPriceType() {
        return priceType;
    }

    /**
     * Returns configured base price.
     *
     * @return base price amount
     */
    public double getBasePrice() {
        return basePrice;
    }

    /**
     * Returns configured price floor.
     *
     * @return price floor amount
     */
    public double getPriceFloor() {
        return priceFloor;
    }

    /**
     * Returns currency code of the price.
     *
     * @return currency code
     */
    public String getCurrencyCode() {
        return currencyCode;
    }

    /**
     * Returns effective start date.
     *
     * @return defensive copy of effectiveFrom
     */
    public Date getEffectiveFrom() {
        return new Date(effectiveFrom.getTime());
    }

    /**
     * Returns effective end date when present.
     *
     * @return defensive copy of effectiveTo or null
     */
    public Date getEffectiveTo() {
        return effectiveTo == null ? null : new Date(effectiveTo.getTime());
    }

    /**
     * Returns lifecycle status.
     *
     * @return record status
     */
    public Status getStatus() {
        return status;
    }

    /**
     * Returns a copy with updated lifecycle status and validity end timestamp.
     *
     * @param newStatus target status; must be non-null
     * @param newEffectiveTo end timestamp for current version; nullable
     * @return copied immutable record with new status values
     */
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

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " cannot be null");
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " cannot be blank");
        }
        return normalized;
    }
}
