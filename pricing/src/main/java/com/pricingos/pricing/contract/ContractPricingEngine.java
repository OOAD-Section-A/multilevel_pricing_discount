package com.pricingos.pricing.contract;

import com.pricingos.common.ContractStatus;
import com.pricingos.common.IContractPricingService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Application service for contract-pricing use cases.
 * GRASP Controller: coordinates contract operations requested by clients.
 * SOLID SRP: focuses on contract pricing lifecycle without tier logic.
 */
public class ContractPricingEngine implements IContractPricingService {

    private final Map<String, Contract> contracts = new ConcurrentHashMap<>();
    private final AtomicInteger counter = new AtomicInteger();

    @Override
    public String createContract(String customerId, LocalDate startDate,
                                 LocalDate endDate, Map<String, Double> skuPrices) {
        String normalizedCustomerId = requireId(customerId, "customerId");
        Objects.requireNonNull(startDate, "startDate cannot be null");
        Objects.requireNonNull(endDate, "endDate cannot be null");
        Objects.requireNonNull(skuPrices, "skuPrices cannot be null");

        String id = "CTR-" + counter.incrementAndGet();
        Contract contract = Contract.builder(id, normalizedCustomerId)
            .startDate(startDate)
            .endDate(endDate)
            .skuPrices(skuPrices)
            .build();
        contracts.put(id, contract);
        return id;
    }

    @Override
    public Optional<Double> getContractPrice(String customerId, String skuId) {
        String normalizedCustomerId = requireId(customerId, "customerId");
        String normalizedSkuId = requireId(skuId, "skuId");
        LocalDate today = LocalDate.now();
        return contracts.values().stream()
            .filter(c -> c.getCustomerId().equals(normalizedCustomerId) && c.isActiveOn(today))
                .max(Comparator.comparing(Contract::getStartDate).thenComparing(Contract::getContractId))
            .map(c -> c.getPrice(normalizedSkuId));
    }

    @Override
    public void submitForApproval(String contractId) {
        get(contractId).submitForApproval();
    }

    @Override
    public void activate(String contractId) {
        get(contractId).activate();
    }

    @Override
    public void renew(String contractId, LocalDate newEndDate) {
        Objects.requireNonNull(newEndDate, "newEndDate cannot be null");
        get(contractId).renew(newEndDate);
    }

    @Override
    public ContractStatus getStatus(String contractId) {
        return get(contractId).getStatus();
    }

    public List<String> getExpiringSoon(int days) {
        if (days < 0) {
            throw new IllegalArgumentException("days must be >= 0");
        }
        LocalDate today = LocalDate.now();
        LocalDate cutoff = today.plusDays(days);
        List<String> result = new ArrayList<>();
        for (Contract c : contracts.values()) {
            if (c.markExpiringIfDue(today, cutoff)) {
                result.add(c.getContractId());
            }
        }
        result.sort(String::compareTo);
        return result;
    }

    private Contract get(String id) {
        String normalizedId = requireId(id, "contractId");
        Contract c = contracts.get(normalizedId);
        if (c == null) {
            throw new IllegalArgumentException("No contract: " + normalizedId);
        }
        return c;
    }

    private static String requireId(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " cannot be null");
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " cannot be blank");
        }
        return normalized;
    }
}