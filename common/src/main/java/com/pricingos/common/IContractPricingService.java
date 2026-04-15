package com.pricingos.common;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

public interface IContractPricingService {

    String createContract(String customerId, LocalDate startDate, LocalDate endDate, Map<String, Double> skuPrices);

    Optional<Double> getContractPrice(String customerId, String skuId);

    void submitForApproval(String contractId);
    void activate(String contractId);
    void renew(String contractId, LocalDate newEndDate);
    ContractStatus getStatus(String contractId);

    boolean hasContractConflict(String customerId, String skuId);
}
