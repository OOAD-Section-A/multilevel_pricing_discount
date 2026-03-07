package com.pricingos.pricing.contract;

import com.pricingos.common.*;
import java.time.LocalDate;
import java.util.*;

public class ContractPricingEngine implements IContractPricingService {

    private final Map<String, Contract> contracts = new HashMap<>();
    private int counter = 0;

    @Override
    public String createContract(String customerId, LocalDate startDate,
                                 LocalDate endDate, Map<String, Double> skuPrices) {
        String id = "CTR-" + (++counter);
        contracts.put(id, new Contract(id, customerId, startDate, endDate, skuPrices));
        return id;
    }

    @Override
    public Optional<Double> getContractPrice(String customerId, String skuId) {
        LocalDate today = LocalDate.now();
        for (Contract c : contracts.values()) {
            if (c.getCustomerId().equals(customerId) && c.isActiveOn(today)) {
                Double price = c.getPrice(skuId);
                if (price != null) return Optional.of(price);
            }
        }
        return Optional.empty();
    }

    @Override
    public void submitForApproval(String contractId) {
        Contract c = get(contractId);
        if (c.getStatus() != ContractStatus.DRAFT)
            throw new IllegalStateException("Only DRAFT contracts can be submitted");
        c.setStatus(ContractStatus.PENDING_APPROVAL);
    }

    @Override
    public void activate(String contractId) {
        Contract c = get(contractId);
        if (c.getStatus() != ContractStatus.PENDING_APPROVAL)
            throw new IllegalStateException("Only PENDING_APPROVAL contracts can be activated");
        c.setStatus(ContractStatus.ACTIVE);
    }

    @Override
    public void renew(String contractId, LocalDate newEndDate) {
        Contract c = get(contractId);
        c.setEndDate(newEndDate);
        c.setStatus(ContractStatus.ACTIVE);
    }

    @Override
    public ContractStatus getStatus(String contractId) {
        return get(contractId).getStatus();
    }

    public List<String> getExpiringSoon(int days) {
        LocalDate cutoff = LocalDate.now().plusDays(days);
        List<String> result = new ArrayList<>();
        for (Contract c : contracts.values()) {
            if (c.getStatus() == ContractStatus.ACTIVE
                    && c.getEndDate().isBefore(cutoff)) {
                c.setStatus(ContractStatus.EXPIRING);
                result.add(c.getContractId());
            }
        }
        return result;
    }

    private Contract get(String id) {
        Contract c = contracts.get(id);
        if (c == null){
            throw new IllegalArgumentException("No contract: " + id);
        }
        return c;
    }
}