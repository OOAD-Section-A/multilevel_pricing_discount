package com.pricingos.pricing.contract;

import com.pricingos.common.ContractStatus;
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
        this.contractId = Objects.requireNonNull(contractId, "contractId cannot be null");
        this.customerId = Objects.requireNonNull(customerId, "customerId cannot be null");
        this.startDate = Objects.requireNonNull(startDate, "startDate cannot be null");
        this.endDate = Objects.requireNonNull(endDate, "endDate cannot be null");
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("endDate cannot be before startDate");
        }
        this.status = ContractStatus.DRAFT;
        this.skuPrices = Collections.unmodifiableMap(
                new HashMap<>(Objects.requireNonNull(skuPrices, "skuPrices cannot be null"))
        );
    }

    public static Builder builder(String contractId, String customerId) {
        return new Builder(contractId, customerId);
    }

    public String getContractId(){ 
        return contractId;
    }
    public String getCustomerId(){
        return customerId;
    }
    public LocalDate getStartDate(){
        return startDate;
    }
    public LocalDate getEndDate(){
        synchronized (this) {
            return endDate;
        }
    }
    public ContractStatus getStatus(){
        synchronized (this) {
            return status;
        }
    }
    public void setStatus(ContractStatus status){
        transitionTo(status);
    }
    public void setEndDate(LocalDate endDate){
        Objects.requireNonNull(endDate, "endDate cannot be null");
        synchronized (this) {
            if (endDate.isBefore(startDate)) {
                throw new IllegalArgumentException("endDate cannot be before startDate");
            }
            this.endDate = endDate;
        }
    }
    public Double getPrice(String skuId) {
        return skuPrices.get(skuId);
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
        transitionTo(ContractStatus.PENDING_APPROVAL);
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
                transitionTo(ContractStatus.EXPIRING);
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
        transitions.put(ContractStatus.DRAFT, EnumSet.of(ContractStatus.PENDING_APPROVAL));
        transitions.put(ContractStatus.PENDING_APPROVAL, EnumSet.of(ContractStatus.ACTIVE));
        transitions.put(ContractStatus.ACTIVE, EnumSet.of(ContractStatus.EXPIRING, ContractStatus.EXPIRED));
        transitions.put(ContractStatus.EXPIRING, EnumSet.of(ContractStatus.ACTIVE, ContractStatus.EXPIRED));
        transitions.put(ContractStatus.EXPIRED, EnumSet.of(ContractStatus.ACTIVE));
        return transitions;
    }

    public static class Builder {
        private final String contractId;
        private final String customerId;
        private LocalDate startDate;
        private LocalDate endDate;
        private final Map<String, Double> skuPrices = new HashMap<>();

        private Builder(String contractId, String customerId) {
            this.contractId = Objects.requireNonNull(contractId, "contractId cannot be null");
            this.customerId = Objects.requireNonNull(customerId, "customerId cannot be null");
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
            skuPrices.put(Objects.requireNonNull(skuId, "skuId cannot be null"), price);
            return this;
        }

        public Builder skuPrices(Map<String, Double> skuPrices) {
            this.skuPrices.putAll(Objects.requireNonNull(skuPrices, "skuPrices cannot be null"));
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