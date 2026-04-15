package com.pricingos.common;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

/**
 * Contract pricing use cases exposed by the pricing subsystem.
 */
public interface IContractPricingService {

    /**
     * Creates a new draft contract and returns the generated contract id.
     */
    String createContract(String customerId, LocalDate startDate, LocalDate endDate, Map<String, Double> skuPrices);

    /**
     * Returns the currently applicable contract price for a customer and SKU, if any.
     */
    Optional<Double> getContractPrice(String customerId, String skuId);

    void submitForApproval(String contractId);
    void activate(String contractId);
    void renew(String contractId, LocalDate newEndDate);
    ContractStatus getStatus(String contractId);

    /**
     * Returns true when multiple active contracts with different prices exist
     * for the same customer + SKU pair and date.
     */
    boolean hasContractConflict(String customerId, String skuId);
}
