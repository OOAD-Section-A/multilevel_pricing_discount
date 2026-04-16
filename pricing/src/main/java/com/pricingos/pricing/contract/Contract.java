package com.pricingos.pricing.contract;

import com.pricingos.common.ContractStatus;
import com.pricingos.common.ValidationUtils;
import java.time.LocalDate;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class Contract {

    private static final Map<ContractStatus, Set<ContractStatus>> ALLOWED_TRANSITIONS = buildAllowedTransitions();

    private final String contractId;
    private final String customerId;
    private final LocalDate startDate;
    private LocalDate endDate;
    private ContractStatus status;
    private final Map<String, Double> skuPrices;

    public Contract(String contractId, String customerId,
                    LocalDate startDate, LocalDate endDate,
                    Map<String, Double> skuPrices) {
        this.contractId = ValidationUtils.requireNonBlank(contractId, "contractId");
        this.customerId = ValidationUtils.requireNonBlank(customerId, "customerId");
        this.startDate = Objects.requireNonNull(startDate, "startDate cannot be null");
        this.endDate = Objects.requireNonNull(endDate, "endDate cannot be null");
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("endDate cannot be before startDate");
        }
        this.status = ContractStatus.PENDING;
        this.skuPrices = Collections.unmodifiableMap(validateSkuPrices(
            Objects.requireNonNull(skuPrices, "skuPrices cannot be null")
        ));
    }

    public static Builder builder(String contractId, String customerId) {
        return new Builder(contractId, customerId);
    }

    public String getContractId() {
        return contractId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        synchronized (this) {
            return endDate;
        }
    }

    public ContractStatus getStatus() {
        synchronized (this) {
            return status;
        }
    }

    public void setStatus(ContractStatus status) {
        transitionTo(status);
    }

    public void setEndDate(LocalDate endDate) {
        Objects.requireNonNull(endDate, "endDate cannot be null");
        synchronized (this) {
            if (endDate.isBefore(startDate)) {
                throw new IllegalArgumentException("endDate cannot be before startDate");
            }
            this.endDate = endDate;
        }
    }

    public Double getPrice(String skuId) {
        return skuPrices.get(ValidationUtils.requireNonBlank(skuId, "skuId"));
    }

    public boolean isActiveOn(LocalDate date) {
        Objects.requireNonNull(date, "date cannot be null");
        synchronized (this) {
            return status == ContractStatus.ACTIVE
                && !date.isBefore(startDate)
                && !date.isAfter(endDate);
        }
    }

    public void submitForApproval() {
        transitionTo(ContractStatus.PENDING);
    }

    public void activate() {
        transitionTo(ContractStatus.ACTIVE);
    }

    public void renew(LocalDate newEndDate) {
        setEndDate(newEndDate);
        transitionTo(ContractStatus.ACTIVE);
    }

    public boolean markExpiringIfDue(LocalDate today, LocalDate cutoff) {
        Objects.requireNonNull(today, "today cannot be null");
        Objects.requireNonNull(cutoff, "cutoff cannot be null");
        synchronized (this) {
            if (status == ContractStatus.ACTIVE && endDate.isBefore(today)) {
                transitionTo(ContractStatus.EXPIRED);
                return false;
            }
            if (status == ContractStatus.ACTIVE && !endDate.isAfter(cutoff)) {
                transitionTo(ContractStatus.PENDING);
                return true;
            }
            return false;
        }
    }

    public void transitionTo(ContractStatus nextStatus) {
        Objects.requireNonNull(nextStatus, "nextStatus cannot be null");
        synchronized (this) {
            if (status == nextStatus) {
                return;
            }
            Set<ContractStatus> allowed = ALLOWED_TRANSITIONS.get(status);
            if (allowed == null || !allowed.contains(nextStatus)) {
                throw new IllegalStateException("Invalid status transition from " + status + " to " + nextStatus);
            }
            status = nextStatus;
        }
    }

    private static Map<ContractStatus, Set<ContractStatus>> buildAllowedTransitions() {
        Map<ContractStatus, Set<ContractStatus>> transitions = new EnumMap<>(ContractStatus.class);
        transitions.put(ContractStatus.PENDING, EnumSet.of(ContractStatus.ACTIVE));
        transitions.put(ContractStatus.ACTIVE, EnumSet.of(ContractStatus.PENDING, ContractStatus.EXPIRED));
        transitions.put(ContractStatus.EXPIRED, EnumSet.of(ContractStatus.ACTIVE));
        return Collections.unmodifiableMap(transitions);
    }

    private static Map<String, Double> validateSkuPrices(Map<String, Double> inputPrices) {
        Map<String, Double> validatedPrices = new HashMap<>();
        for (Map.Entry<String, Double> entry : inputPrices.entrySet()) {
            String skuId = ValidationUtils.requireNonBlank(entry.getKey(), "skuId");
            Double price = Objects.requireNonNull(entry.getValue(), "price cannot be null for skuId " + skuId);
            if (!Double.isFinite(price) || price < 0.0) {
                throw new IllegalArgumentException("price must be a non-negative finite number for skuId " + skuId);
            }
            validatedPrices.put(skuId, price);
        }
        return validatedPrices;
    }

    public static class Builder {
        private final String contractId;
        private final String customerId;
        private LocalDate startDate;
        private LocalDate endDate;
        private final Map<String, Double> skuPrices = new HashMap<>();

        private Builder(String contractId, String customerId) {
            this.contractId = ValidationUtils.requireNonBlank(contractId, "contractId");
            this.customerId = ValidationUtils.requireNonBlank(customerId, "customerId");
        }

        public Builder startDate(LocalDate startDate) {
            this.startDate = Objects.requireNonNull(startDate, "startDate cannot be null");
            return this;
        }

        public Builder endDate(LocalDate endDate) {
            this.endDate = Objects.requireNonNull(endDate, "endDate cannot be null");
            return this;
        }

        public Builder skuPrice(String skuId, double price) {
            String normalizedSkuId = ValidationUtils.requireNonBlank(skuId, "skuId");
            if (!Double.isFinite(price) || price < 0.0) {
                throw new IllegalArgumentException("price must be a non-negative finite number");
            }
            skuPrices.put(normalizedSkuId, price);
            return this;
        }

        public Builder skuPrices(Map<String, Double> skuPrices) {
            this.skuPrices.putAll(validateSkuPrices(Objects.requireNonNull(skuPrices, "skuPrices cannot be null")));
            return this;
        }

        public Contract build() {
            if (startDate == null || endDate == null) {
                throw new IllegalStateException("Both startDate and endDate are required");
            }
            return new Contract(contractId, customerId, startDate, endDate, skuPrices);
        }
    }
}
