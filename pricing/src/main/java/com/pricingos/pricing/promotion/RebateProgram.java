package com.pricingos.pricing.promotion;

import java.util.Objects;

/**
 * Domain object representing a Rebate &amp; Loyalty Programme for a single customer + SKU pairing.
 *
 * <p>Component 5 — Promotion &amp; Campaign Manager, Rebate &amp; Loyalty feature.
 * Supports: "Tracks cumulative purchases over a quarter or year and calculates
 * 'back-end' rebates to be paid out or credited to the customer once targets are met."
 *
 * <p>GRASP Information Expert: owns the rebate calculation and target-met logic so
 * that this state is never scattered across service methods.
 *
 * <p>Thread safety: {@code accumulatedSpend} is mutated under {@code synchronized} guards
 * since multiple order fulfilment threads may call {@link #addSpend} concurrently.
 */
public final class RebateProgram {

    private final String programId;
    private final String customerId;
    private final String skuId;

    /** Cumulative spend threshold that must be reached before any rebate is paid. */
    private final double targetSpend;

    /** Percentage of accumulated spend returned to the customer as a rebate (0–100). */
    private final double rebatePct;

    /** Running total of confirmed purchase amounts recorded by the system. */
    private double accumulatedSpend;

    /**
     * Constructs a RebateProgram. Called only by {@link RebateProgramManager}.
     *
     * @param programId  generated unique ID
     * @param customerId the customer enrolled in this programme
     * @param skuId      the SKU whose purchases count towards the target
     * @param targetSpend cumulative spend threshold (must be > 0)
     * @param rebatePct  rebate percentage to pay back when target is met (0–100)
     */
    RebateProgram(String programId, String customerId, String skuId,
                  double targetSpend, double rebatePct) {
        this.programId  = requireNonBlank(programId,  "programId");
        this.customerId = requireNonBlank(customerId, "customerId");
        this.skuId      = requireNonBlank(skuId,      "skuId");
        if (!Double.isFinite(targetSpend) || targetSpend <= 0)
            throw new IllegalArgumentException("targetSpend must be a positive finite number, got: " + targetSpend);
        if (!Double.isFinite(rebatePct) || rebatePct < 0 || rebatePct > 100)
            throw new IllegalArgumentException("rebatePct must be in [0, 100], got: " + rebatePct);
        this.targetSpend      = targetSpend;
        this.rebatePct        = rebatePct;
        this.accumulatedSpend = 0.0;
    }

    // ── Domain logic ──────────────────────────────────────────────────────────────

    /**
     * Increments the accumulated spend by the given purchase amount.
     * Negative amounts are rejected to prevent fraud.
     *
     * @param amount the net value of a confirmed purchase to add (must be ≥ 0)
     */
    public synchronized void addSpend(double amount) {
        if (!Double.isFinite(amount) || amount < 0)
            throw new IllegalArgumentException("Purchase amount must be a non-negative finite number, got: " + amount);
        this.accumulatedSpend += amount;
    }

    /**
     * Returns {@code true} if the customer has met or exceeded the target spend.
     *
     * @return true if accumulated spend ≥ target spend
     */
    public synchronized boolean isTargetMet() {
        return accumulatedSpend >= targetSpend;
    }

    /**
     * Returns the monetary rebate amount due to the customer.
     * Returns {@code 0.0} if the target spend has not yet been reached.
     *
     * <p>Formula: {@code accumulatedSpend × (rebatePct / 100)} when target is met.
     *
     * @return rebate amount, or 0 if target not met
     */
    public synchronized double calculateRebateDue() {
        if (!isTargetMet()) return 0.0;
        return accumulatedSpend * (rebatePct / 100.0);
    }

    // ── Getters ───────────────────────────────────────────────────────────────────

    public String getProgamId()                          { return programId; }
    public String getCustomerId()                        { return customerId; }
    public String getSkuId()                             { return skuId; }
    public double getTargetSpend()                       { return targetSpend; }
    public double getRebatePct()                         { return rebatePct; }
    public synchronized double getAccumulatedSpend()     { return accumulatedSpend; }

    /**
     * Returns a human-readable summary of the programme's progress, e.g.:
     * {@code "[PROG-1] Customer C001 | SKU SKU-001 | ₹350/₹500 (70%) | Rebate: ₹0.00"}.
     */
    @Override
    public String toString() {
        return String.format("[%s] Customer %s | SKU %s | %.2f/%.2f (%.0f%%) | Rebate: %.2f",
            programId, customerId, skuId,
            accumulatedSpend, targetSpend,
            (accumulatedSpend / targetSpend * 100),
            calculateRebateDue());
    }

    // ── Utilities ─────────────────────────────────────────────────────────────────

    private static String requireNonBlank(String v, String field) {
        Objects.requireNonNull(v, field + " cannot be null");
        if (v.trim().isEmpty()) throw new IllegalArgumentException(field + " cannot be blank");
        return v.trim();
    }
}
