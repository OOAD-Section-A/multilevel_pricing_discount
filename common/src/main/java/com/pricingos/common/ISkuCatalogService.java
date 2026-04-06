package com.pricingos.common;

import java.util.List;

/**
 * Integration boundary with the Inventory Management subsystem (Team "Better Call Objects").
 *
 * <p>The Promotion & Campaign Manager (Component 5) needs to validate that SKUs listed
 * in a promotion actually exist in the product catalog. Rather than coupling to the
 * Inventory module directly, we depend on this interface — SOLID DIP / GRASP Indirection.
 *
 * <p>"Better Call Objects" provides a concrete implementation; we depend only on this contract.
 */
public interface ISkuCatalogService {

    /**
     * Returns true if the given SKU ID exists and is currently active in the product catalog.
     *
     * @param skuId the SKU identifier to verify
     * @return true if the SKU is valid and active
     */
    boolean isSkuActive(String skuId);

    /**
     * Returns all active SKU IDs currently in the catalog.
     * Used during promotion setup to offer the admin a valid SKU list.
     *
     * @return list of active SKU identifiers
     */
    List<String> getAllActiveSkuIds();
}