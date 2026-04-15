package com.pricingos.common;

public interface IRebateService {

    String createRebateProgram(String customerId, String skuId,
                               double targetSpend, double rebatePct);

    void recordPurchase(String programId, double purchaseAmount);

    double getRebateDue(String programId);

    boolean isTargetMet(String programId);

    double getAccumulatedSpend(String programId);
}
