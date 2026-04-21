package com.pricingos.pricing.contract;

import com.pricingos.common.ContractStatus;
import com.pricingos.common.IContractPricingService;
import com.pricingos.common.ValidationUtils;
import com.jackfruit.scm.database.adapter.PricingAdapter;
import com.scm.subsystems.MultiLevelPricingSubsystem;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ContractPricingEngine implements IContractPricingService {

    private final AtomicInteger counter = new AtomicInteger();
    private final PricingAdapter pricingAdapter;
    private final ConcurrentHashMap<String, Contract> contractCache = new ConcurrentHashMap<>();
    private MultiLevelPricingSubsystem exceptions;
    private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");

    public ContractPricingEngine(PricingAdapter pricingAdapter) {
        this.pricingAdapter = Objects.requireNonNull(pricingAdapter, "pricingAdapter cannot be null");
    }
    
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

    @Override
    public String createContract(String customerId, LocalDate startDate,
                                 LocalDate endDate, Map<String, Double> skuPrices) {
        String normalizedCustomerId = ValidationUtils.requireNonBlank(customerId, "customerId");
        Objects.requireNonNull(startDate, "startDate cannot be null");
        Objects.requireNonNull(endDate, "endDate cannot be null");
        Objects.requireNonNull(skuPrices, "skuPrices cannot be null");

        String id = "CTR-" + counter.incrementAndGet();
        Contract contract = Contract.builder(id, normalizedCustomerId)
            .startDate(startDate)
            .endDate(endDate)
            .skuPrices(skuPrices)
            .build();
        
        // Cache the contract in memory. Note: Database team's PricingAdapter 
        // does not expose a contract persistence API, so we maintain this cache.
        contractCache.put(id, contract);
        
        return id;
    }

    @Override
    public Optional<Double> getContractPrice(String customerId, String skuId) {
        String normalizedCustomerId = ValidationUtils.requireNonBlank(customerId, "customerId");
        String normalizedSkuId = ValidationUtils.requireNonBlank(skuId, "skuId");
        LocalDate today = LocalDate.now();
        Optional<Double> active = contractCache.values().stream()
            .filter(c -> c.getCustomerId().equals(normalizedCustomerId) && c.isActiveOn(today))
            .max(Comparator.comparing(Contract::getStartDate).thenComparing(Contract::getContractId))
            .map(c -> c.getPrice(normalizedSkuId));

        if (active.isPresent()) {
            return active;
        }

        boolean hasExpiredMatch = contractCache.values().stream().anyMatch(c ->
            c.getCustomerId().equals(normalizedCustomerId)
                && c.getPrice(normalizedSkuId) != null
                && c.getEndDate().isBefore(today));
        if (hasExpiredMatch) {
            // Find the expired contract to get its ID and expiry date
            contractCache.values().stream()
                .filter(c -> c.getCustomerId().equals(normalizedCustomerId)
                    && c.getPrice(normalizedSkuId) != null
                    && c.getEndDate().isBefore(today))
                .findFirst()
                .ifPresent(c -> {
                    try {
                        if (getExceptions() != null) {
                            exceptions.onContractExpiredAlert(c.getContractId(), c.getEndDate().toString());
                        }
                    } catch (Exception e) {
                        // Windows Event Viewer not available on Linux
                    }
                });
        }
        return Optional.empty();
    }

    @Override
    public void submitForApproval(String contractId) {
        Contract c = get(contractId);
        c.submitForApproval();
        // Update cache - database team's PricingAdapter does not expose contract persistence API
        contractCache.put(contractId, c);
    }

    @Override
    public void activate(String contractId) {
        Contract c = get(contractId);
        c.activate();
        // Update cache - database team's PricingAdapter does not expose contract persistence API
        contractCache.put(contractId, c);
    }

    @Override
    public void renew(String contractId, LocalDate newEndDate) {
        Objects.requireNonNull(newEndDate, "newEndDate cannot be null");
        Contract c = get(contractId);
        c.renew(newEndDate);
        // Update cache - database team's PricingAdapter does not expose contract persistence API
        contractCache.put(contractId, c);
    }

    @Override
    public ContractStatus getStatus(String contractId) {
        return get(contractId).getStatus();
    }

    @Override
    public boolean hasContractConflict(String customerId, String skuId) {
        String normalizedCustomerId = ValidationUtils.requireNonBlank(customerId, "customerId");
        String normalizedSkuId = ValidationUtils.requireNonBlank(skuId, "skuId");
        LocalDate today = LocalDate.now();

        Set<Double> activePrices = new HashSet<>();
        for (Contract contract : contractCache.values()) {
            if (!contract.getCustomerId().equals(normalizedCustomerId) || !contract.isActiveOn(today)) {
                continue;
            }
            Double price = contract.getPrice(normalizedSkuId);
            if (price == null) {
                continue;
            }
            activePrices.add(price);
            if (activePrices.size() > 1) {
                return true;
            }
        }
        return false;
    }

    public List<String> getExpiringSoon(int days) {
        if (days < 0) {
            throw new IllegalArgumentException("days must be >= 0");
        }
        LocalDate today = LocalDate.now();
        LocalDate cutoff = today.plusDays(days);
        List<String> result = new ArrayList<>();
        for (Contract c : contractCache.values()) {
            if (c.markExpiringIfDue(today, cutoff)) {
                // Update cache - database team's PricingAdapter does not expose contract persistence API
                contractCache.put(c.getContractId(), c);
                result.add(c.getContractId());
            }
        }
        result.sort(String::compareTo);
        return result;
    }

    private Contract get(String id) {
        String normalizedId = ValidationUtils.requireNonBlank(id, "contractId");
        Contract contract = contractCache.get(normalizedId);
        if (contract == null) {
            throw new IllegalArgumentException("No contract: " + normalizedId);
        }
        return contract;
    }
}
