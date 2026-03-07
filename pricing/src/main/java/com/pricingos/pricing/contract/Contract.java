package com.pricingos.pricing.contract;

import com.pricingos.common.ContractStatus;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

public class Contract {
    private final String contractId;
    private final String customerId;
    private final LocalDate startDate;
    private LocalDate endDate;
    private ContractStatus status;
    private final Map<String, Double> skuPrices;

    public Contract(String contractId, String customerId,
                    LocalDate startDate, LocalDate endDate,
                    Map<String, Double> skuPrices) {
        this.contractId = contractId;
        this.customerId = customerId;
        this.startDate = startDate;
        this.endDate = endDate;
        this.status = ContractStatus.DRAFT;
        this.skuPrices = new HashMap<>(skuPrices);
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
        return endDate; 
    }
    public ContractStatus getStatus(){
        return status;
    }
    public void setStatus(ContractStatus status){
        this.status = status;
    }
    public void setEndDate(LocalDate endDate){
        this.endDate = endDate;
    }
    public Double getPrice(String skuId) {
        return skuPrices.get(skuId);
    }
    public boolean isActiveOn(LocalDate date) {
        return status == ContractStatus.ACTIVE
            && !date.isBefore(startDate)
            && !date.isAfter(endDate);
    }
}