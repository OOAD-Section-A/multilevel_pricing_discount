package com.pricingos.pricing.baseprice;

import com.pricingos.common.ValidationUtils;
import com.pricingos.pricing.pricelist.PriceListManager;
import com.scm.subsystems.MultiLevelPricingSubsystem;
import java.util.NoSuchElementException;
import java.util.Objects;

public class BasePriceConfig {

    public static final double PRICE_FLOOR_SAFETY_MARGIN = 1.05;

    private static final String DEFAULT_REGION = "GLOBAL";
    private static final String DEFAULT_CHANNEL = "RETAIL";

    private final String adminUserId;
    private final double defaultMarkupPercentage;
    private final String systemCurrency;
    private final PriceListManager priceListManager;
    private MultiLevelPricingSubsystem exceptions;
    private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");
    
    private MultiLevelPricingSubsystem getExceptions() {
        if (exceptions == null && IS_WINDOWS) {
            try {
                exceptions = MultiLevelPricingSubsystem.INSTANCE;
            } catch (Exception e) {
                // Windows Event Viewer initialization failed
                exceptions = null;
            }
        }
        return exceptions;
    }

    public BasePriceConfig(
            String adminUserId,
            double defaultMarkupPercentage,
            String systemCurrency,
            PriceListManager priceListManager) {
        this.adminUserId = ValidationUtils.requireNonBlank(adminUserId, "adminUserId");
        if (!Double.isFinite(defaultMarkupPercentage) || defaultMarkupPercentage < 0.0 || defaultMarkupPercentage >= 1.0) {
            throw new IllegalArgumentException("defaultMarkupPercentage must be in [0, 1)");
        }
        this.defaultMarkupPercentage = defaultMarkupPercentage;
        this.systemCurrency = ValidationUtils.requireNonBlank(systemCurrency, "systemCurrency");
        this.priceListManager = Objects.requireNonNull(priceListManager, "priceListManager cannot be null");
    }

    public void setBasePrice(String skuId, double cost, double margin) throws IllegalArgumentException {
        String normalizedSku = ValidationUtils.requireNonBlank(skuId, "skuId");
        if (!Double.isFinite(cost) || cost <= 0.0) {
            throw new IllegalArgumentException("cost must be a positive finite number");
        }
        if (!Double.isFinite(margin) || margin <= 0.0 || margin >= 1.0) {
            throw new IllegalArgumentException("margin must be in (0, 1)");
        }

        double basePrice = cost / (1.0 - margin);
        double priceFloor = cost * PRICE_FLOOR_SAFETY_MARGIN;
        if (!(priceFloor < basePrice)) {
            try {
                if (getExceptions() != null) {
                    exceptions.onPriceFloorConfigError(normalizedSku);
                }
            } catch (Exception e) {
                // Windows Event Viewer not available on Linux
            }
            throw new IllegalArgumentException(
                    "PRICE_FLOOR_CONFIG_ERROR: Price floor [" + priceFloor + "] must be strictly less than base price ["
                            + basePrice + "] for SKU [" + normalizedSku + "].");
        }
        publish(normalizedSku, basePrice, priceFloor);
    }

    public void setPriceFloor(String skuId, double floorPrice) throws NoSuchElementException, IllegalArgumentException {
        String normalizedSku = ValidationUtils.requireNonBlank(skuId, "skuId");
        if (!Double.isFinite(floorPrice) || floorPrice <= 0.0) {
            throw new IllegalArgumentException("floorPrice must be a positive finite number");
        }

        double activeBasePrice = priceListManager.getActivePrice(normalizedSku, DEFAULT_REGION, DEFAULT_CHANNEL);
        if (!(floorPrice < activeBasePrice)) {
            try {
                if (getExceptions() != null) {
                    exceptions.onPriceFloorConfigError(normalizedSku);
                }
            } catch (Exception e) {
                // Windows Event Viewer not available on Linux
            }
            throw new IllegalArgumentException(
                    "PRICE_FLOOR_CONFIG_ERROR: Price floor [" + floorPrice + "] must be strictly less than base price ["
                            + activeBasePrice + "] for SKU [" + normalizedSku + "].");
        }
        publish(normalizedSku, activeBasePrice, floorPrice);
    }

    public double calculateMargin(double cost, double price) {
        if (!Double.isFinite(cost) || cost < 0.0) {
            throw new IllegalArgumentException("cost must be a non-negative finite number");
        }
        if (!Double.isFinite(price) || price <= 0.0) {
            throw new IllegalArgumentException("price must be a positive finite number");
        }
        double margin = (price - cost) / price;
        if (margin < 0.0) {
            try {
                if (getExceptions() != null) {
                    exceptions.onNegativeMarginCalculation("unknown", margin);
                }
            } catch (Exception e) {
                // Windows Event Viewer not available on Linux
            }
            throw new IllegalArgumentException(
                    "NEGATIVE_MARGIN_CALCULATION: Base price configuration results in a negative profit margin. "
                            + "COGS=[" + cost + "], Price=[" + price + "].");
        }
        return margin;
    }

    public String getAdminUserId() {
        return adminUserId;
    }

    public double getDefaultMarkupPercentage() {
        return defaultMarkupPercentage;
    }

    public String getSystemCurrency() {
        return systemCurrency;
    }

    private void publish(String skuId, double basePrice, double floorPrice) {
        BasePriceRecord record = new BasePriceRecord.Builder()
                .skuId(skuId)
                .regionCode(DEFAULT_REGION)
                .channel(DEFAULT_CHANNEL)
                .priceType("RETAIL")
                .basePrice(basePrice)
                .priceFloor(floorPrice)
                .currencyCode(systemCurrency)
                .build();
        priceListManager.updatePrice(record);
    }

}
