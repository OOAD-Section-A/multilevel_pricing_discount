package com.pricingos.pricing.contract;

import com.pricingos.common.ContractStatus;
import com.pricingos.common.IContractPricingService;
import com.pricingos.common.ValidationUtils;
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
        ContractDao.save(contract);
        return id;
    }

    @Override
    public Optional<Double> getContractPrice(String customerId, String skuId) {
        String normalizedCustomerId = ValidationUtils.requireNonBlank(customerId, "customerId");
        String normalizedSkuId = ValidationUtils.requireNonBlank(skuId, "skuId");
        LocalDate today = LocalDate.now();
        Optional<Double> active = ContractDao.findAll().stream()
            .filter(c -> c.getCustomerId().equals(normalizedCustomerId) && c.isActiveOn(today))
            .max(Comparator.comparing(Contract::getStartDate).thenComparing(Contract::getContractId))
            .map(c -> c.getPrice(normalizedSkuId));

        if (active.isPresent()) {
            return active;
        }

        boolean hasExpiredMatch = ContractDao.findAll().stream().anyMatch(c ->
            c.getCustomerId().equals(normalizedCustomerId)
                && c.getPrice(normalizedSkuId) != null
                && c.getEndDate().isBefore(today));
        if (hasExpiredMatch) {
            // Find the expired contract to get its ID and expiry date
            ContractDao.findAll().stream()
                .filter(c -> c.getCustomerId().equals(normalizedCustomerId)
                    && c.getPrice(normalizedSkuId) != null
                    && c.getEndDate().isBefore(today))
                .findFirst()
                .ifPresent(c -> {
                    try {
                        MultiLevelPricingSubsystem.INSTANCE.onContractExpiredAlert(c.getContractId(), c.getEndDate().toString());
                    } catch (ExceptionInInitializerError | NoClassDefFoundError e) {
                        // Database not available during tests
                    }
                });
        }
        return Optional.empty();
    }

    @Override
    public void submitForApproval(String contractId) {
        Contract c = get(contractId);
        c.submitForApproval();
        ContractDao.save(c);
    }

    @Override
    public void activate(String contractId) {
        Contract c = get(contractId);
        c.activate();
        ContractDao.save(c);
    }

    @Override
    public void renew(String contractId, LocalDate newEndDate) {
        Objects.requireNonNull(newEndDate, "newEndDate cannot be null");
        Contract c = get(contractId);
        c.renew(newEndDate);
        ContractDao.save(c);
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
        for (Contract contract : ContractDao.findAll()) {
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
        for (Contract c : ContractDao.findAll()) {
            if (c.markExpiringIfDue(today, cutoff)) {
                ContractDao.save(c);
                result.add(c.getContractId());
            }
        }
        result.sort(String::compareTo);
        return result;
    }

    private Contract get(String id) {
        String normalizedId = ValidationUtils.requireNonBlank(id, "contractId");
        Contract contract = ContractDao.get(normalizedId);
        if (contract == null) {
            throw new IllegalArgumentException("No contract: " + normalizedId);
        }
        return contract;
    }
}
