package com.pricingos.pricing.promotion;

import com.pricingos.common.IRebateService;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Component 5 — Promotion &amp; Campaign Manager: Rebate &amp; Loyalty Programme feature.
 *
 * <p>Supports: "Tracks cumulative purchases over a quarter or year and calculates
 * 'back-end' rebates to be paid out or credited to the customer once targets are met."
 *
 * <p>Design patterns:
 * <ul>
 *   <li><b>GRASP Controller</b>: single entry point for all rebate lifecycle use-cases.</li>
 *   <li><b>SOLID SRP</b>: only rebate/loyalty logic lives here.</li>
 *   <li><b>SOLID DIP</b>: the Finance/Order-Fulfillment team calls this via
 *       {@link IRebateService} interface rather than this concrete class.</li>
 * </ul>
 *
 * <p>Thread safety: the programme registry is a {@link ConcurrentHashMap}; individual
 * spend accumulation is synchronised within {@link RebateProgram#addSpend}.
 */
public class RebateProgramManager implements IRebateService {

    private final Map<String, RebateProgram> programRegistry = new ConcurrentHashMap<>();
    private final AtomicInteger idCounter = new AtomicInteger();

    // ── IRebateService implementation ─────────────────────────────────────────────

    /**
     * {@inheritDoc}
     *
     * <p>One customer may have multiple rebate programmes (e.g., one per SKU, one per quarter).
     * Programme IDs are unique across all customers and SKUs.
     */
    @Override
    public String createRebateProgram(String customerId, String skuId,
                                      double targetSpend, double rebatePct) {
        String programId = "RBT-" + idCounter.incrementAndGet();
        RebateProgram program = new RebateProgram(programId, customerId, skuId, targetSpend, rebatePct);
        programRegistry.put(programId, program);
        return programId;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Must only be called after an order is confirmed and payment settled, not during
     * cart preview — matches the contract of {@link PromotionManager#recordRedemption}.
     */
    @Override
    public void recordPurchase(String programId, double purchaseAmount) {
        RebateProgram program = getProgram(programId);
        program.addSpend(purchaseAmount);
    }

    /** {@inheritDoc} */
    @Override
    public double getRebateDue(String programId) {
        return getProgram(programId).calculateRebateDue();
    }

    /** {@inheritDoc} */
    @Override
    public boolean isTargetMet(String programId) {
        return getProgram(programId).isTargetMet();
    }

    /** {@inheritDoc} */
    @Override
    public double getAccumulatedSpend(String programId) {
        return getProgram(programId).getAccumulatedSpend();
    }

    // ── Package-private helpers for testing ──────────────────────────────────────

    /** Returns the RebateProgram for a given programme ID (package-private for tests). */
    RebateProgram getProgramById(String programId) {
        return getProgram(programId);
    }

    // ── Private utilities ──────────────────────────────────────────────────────────

    private RebateProgram getProgram(String programId) {
        Objects.requireNonNull(programId, "programId cannot be null");
        if (programId.trim().isEmpty()) throw new IllegalArgumentException("programId cannot be blank");
        RebateProgram p = programRegistry.get(programId.trim());
        if (p == null)
            throw new IllegalArgumentException("No rebate programme found with ID: " + programId.trim());
        return p;
    }
}
