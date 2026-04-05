package com.pricingos.common;

import java.time.LocalDate;
import java.util.Map;

/**
 * Structural pattern (Facade): a simple integration API for pricing workflows.
 */
public interface IPricingFacade {

    String createContract(String customerId, LocalDate startDate, LocalDate endDate, Map<String, Double> skuPrices);

    void evaluateCustomerTier(String customerId);

    double resolveFinalUnitPrice(String customerId, String skuId, double basePrice);
}
