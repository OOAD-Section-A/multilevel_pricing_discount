package com.pricingos.common;

/**
 * Rebate &amp; Loyalty Program use-cases exposed to the rest of the subsystem.
 *
 * <p>Component 5 — Promotion &amp; Campaign Manager, Rebate &amp; Loyalty feature.
 * Supports the spec requirement: "Tracks cumulative purchases over a quarter or year
 * and calculates 'back-end' rebates to be paid out or credited to the customer once
 * targets are met."
 *
 * <p>Other teams (Order Fulfillment, Finance) integrate through this interface — SOLID DIP.
 */
public interface IRebateService {

    /**
     * Registers a new rebate program for a customer + SKU combination and returns the programId.
     *
     * @param customerId  the customer this rebate programme targets
     * @param skuId       the SKU whose purchases count towards the rebate target
     * @param targetSpend the cumulative spend (in currency units) needed to unlock the rebate
     * @param rebatePct   the percentage of total accumulated spend paid back as a rebate (0–100)
     * @return generated programme ID
     */
    String createRebateProgram(String customerId, String skuId,
                               double targetSpend, double rebatePct);

    /**
     * Records a confirmed purchase amount against the rebate programme.
     * Call this only after the order is fulfilled and payment confirmed.
     *
     * @param programId      the rebate programme to update
     * @param purchaseAmount the net purchase value to add to accumulated spend
     */
    void recordPurchase(String programId, double purchaseAmount);

    /**
     * Returns the rebate amount due to the customer.
     * Returns {@code 0} if the target spend has not yet been reached.
     *
     * @param programId the rebate programme to query
     * @return monetary rebate amount (rebatePct % of accumulated spend), or 0 if target not met
     */
    double getRebateDue(String programId);

    /**
     * Returns {@code true} if the customer has met or exceeded the target spend threshold.
     *
     * @param programId the rebate programme to query
     * @return true if accumulated spend ≥ target spend
     */
    boolean isTargetMet(String programId);

    /**
     * Returns the total accumulated spend recorded so far for this programme.
     *
     * @param programId the rebate programme to query
     * @return sum of all purchase amounts recorded via {@link #recordPurchase}
     */
    double getAccumulatedSpend(String programId);
}
