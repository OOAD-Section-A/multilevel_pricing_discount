package com.pricingos.pricing.baseprice;

import com.pricingos.pricing.pricelist.PriceListManager;
import java.util.NoSuchElementException;
import java.util.Objects;

/*
 * DESIGN PATTERNS USED:
 * - Creational: Builder (BasePriceRecord.Builder) — constructs immutable price records safely.
 * - SOLID SRP: This class only configures prices; storage is delegated to PriceListManager.
 * - SOLID OCP: Implements IBasePriceConfig — new pricing strategies extend the interface.
 * - GRASP Information Expert: Owns cost/margin data; computes derived prices here.
 */

/**
 * Base price configuration service that computes base/floor values from cost and margin.
 */
public class BasePriceConfig implements IBasePriceConfig {

    public static final double PRICE_FLOOR_SAFETY_MARGIN = 1.05;

    private static final String DEFAULT_REGION = "GLOBAL";
    private static final String DEFAULT_CHANNEL = "DIRECT";

    private final String adminUserId;
    private final double defaultMarkupPercentage;
    private final String systemCurrency;
    private final PriceListManager priceListManager;

    /**
     * Creates base pricing configuration service with delegated storage manager.
     *
     * @param adminUserId admin owner id; must be non-blank
     * @param defaultMarkupPercentage default markup ratio in [0, 1); must be finite
     * @param systemCurrency system currency code; must be non-blank
     * @param priceListManager list manager used for persistence/versioning; must be non-null
     */
    public BasePriceConfig(
            String adminUserId,
            double defaultMarkupPercentage,
            String systemCurrency,
            PriceListManager priceListManager) {
        this.adminUserId = requireText(adminUserId, "adminUserId");
        if (!Double.isFinite(defaultMarkupPercentage) || defaultMarkupPercentage < 0.0 || defaultMarkupPercentage >= 1.0) {
            throw new IllegalArgumentException("defaultMarkupPercentage must be in [0, 1)");
        }
        this.defaultMarkupPercentage = defaultMarkupPercentage;
        this.systemCurrency = requireText(systemCurrency, "systemCurrency");
        this.priceListManager = Objects.requireNonNull(priceListManager, "priceListManager cannot be null");
    }

    /**
     * Validates and computes base/floor prices, then stores new active record via manager.
     *
     * @param skuId SKU identifier; must be non-blank
     * @param cost cost of goods sold; must be greater than 0
     * @param margin target margin ratio in (0, 1)
     * @throws IllegalArgumentException if computed floor is not strictly lower than computed base
     */
    @Override
    public void setBasePrice(String skuId, double cost, double margin) throws IllegalArgumentException {
        String normalizedSku = requireText(skuId, "skuId");
        if (!Double.isFinite(cost) || cost <= 0.0) {
            throw new IllegalArgumentException("cost must be a positive finite number");
        }
        if (!Double.isFinite(margin) || margin <= 0.0 || margin >= 1.0) {
            throw new IllegalArgumentException("margin must be in (0, 1)");
        }

        double basePrice = cost / (1.0 - margin);
        double priceFloor = cost * PRICE_FLOOR_SAFETY_MARGIN;
        if (!(priceFloor < basePrice)) {
            throw new IllegalArgumentException(
                    "Price floor [" + priceFloor + "] must be strictly less than base price ["
                            + basePrice + "] for SKU [" + normalizedSku + "].");
        }

        BasePriceRecord record = new BasePriceRecord.Builder()
                .skuId(normalizedSku)
                .regionCode(DEFAULT_REGION)
                .channel(DEFAULT_CHANNEL)
                .priceType("BASE")
                .basePrice(basePrice)
                .priceFloor(priceFloor)
                .currencyCode(systemCurrency)
                .build();
        priceListManager.updatePrice(record);
    }

    /**
     * Validates and applies manual floor update for an existing active base price.
     *
     * @param skuId SKU identifier; must be non-blank
     * @param floorPrice floor amount; must be > 0 and < active base price
     * @throws NoSuchElementException if active base price is missing for SKU
     * @throws IllegalArgumentException if floor is not strictly less than active base price
     */
    @Override
    public void setPriceFloor(String skuId, double floorPrice) throws NoSuchElementException, IllegalArgumentException {
        String normalizedSku = requireText(skuId, "skuId");
        if (!Double.isFinite(floorPrice) || floorPrice <= 0.0) {
            throw new IllegalArgumentException("floorPrice must be a positive finite number");
        }

        double activeBasePrice = priceListManager.getActivePrice(normalizedSku, DEFAULT_REGION, DEFAULT_CHANNEL);
        if (!(floorPrice < activeBasePrice)) {
            throw new IllegalArgumentException(
                    "Price floor [" + floorPrice + "] must be strictly less than base price ["
                            + activeBasePrice + "] for SKU [" + normalizedSku + "].");
        }

        BasePriceRecord updated = new BasePriceRecord.Builder()
                .skuId(normalizedSku)
                .regionCode(DEFAULT_REGION)
                .channel(DEFAULT_CHANNEL)
                .priceType("BASE")
                .basePrice(activeBasePrice)
                .priceFloor(floorPrice)
                .currencyCode(systemCurrency)
                .build();
        priceListManager.updatePrice(updated);
    }

    /**
     * Calculates margin ratio using (price - cost) / price with validation.
     *
     * @param cost cost of goods sold; must be non-negative
     * @param price selling price; must be greater than 0
     * @return margin ratio when valid
     */
    @Override
    public double calculateMargin(double cost, double price) {
        if (!Double.isFinite(cost) || cost < 0.0) {
            throw new IllegalArgumentException("cost must be a non-negative finite number");
        }
        if (!Double.isFinite(price) || price <= 0.0) {
            throw new IllegalArgumentException("price must be a positive finite number");
        }
        double margin = (price - cost) / price;
        if (margin < 0.0) {
            throw new IllegalArgumentException(
                    "NEGATIVE_MARGIN_CALCULATION: Base price configuration results in a negative profit margin. "
                            + "COGS=[" + cost + "], Price=[" + price + "].");
        }
        return margin;
    }

    /**
     * Returns admin owner id for this configuration component.
     *
     * @return admin user identifier
     */
    public String getAdminUserId() {
        return adminUserId;
    }

    /**
     * Returns default markup percentage configured for this component.
     *
     * @return default markup ratio
     */
    public double getDefaultMarkupPercentage() {
        return defaultMarkupPercentage;
    }

    /**
     * Returns system currency code used while publishing records.
     *
     * @return system currency code
     */
    public String getSystemCurrency() {
        return systemCurrency;
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
