package com.pricingos.pricing.baseprice;

import java.util.NoSuchElementException;

/**
 * Abstraction for configuring and computing base pricing attributes.
 * SOLID OCP: alternate pricing configuration strategies can implement this contract.
 */
public interface IBasePriceConfig {

    /**
     * Configures a base price and derived floor using cost and target margin.
     *
     * @param skuId SKU identifier; must be non-blank
     * @param cost cost of goods sold; must be greater than 0
     * @param margin target margin ratio; must be in (0, 1)
     * @throws IllegalArgumentException if computed floor is not strictly below computed base price
     */
    void setBasePrice(String skuId, double cost, double margin) throws IllegalArgumentException;

    /**
     * Sets an explicit floor price for an existing active base price.
     *
     * @param skuId SKU identifier; must be non-blank
     * @param floorPrice floor amount; must be greater than 0 and strictly less than base price
     * @throws NoSuchElementException if no active base price exists for SKU in active region
     * @throws IllegalArgumentException if floor price is not strictly lower than active base price
     */
    void setPriceFloor(String skuId, double floorPrice) throws NoSuchElementException, IllegalArgumentException;

    /**
     * Computes margin ratio from cost and price.
     *
     * @param cost cost of goods sold; must be >= 0
     * @param price selling price; must be > 0
     * @return computed margin ratio in [0, 1)
     */
    double calculateMargin(double cost, double price);
}
