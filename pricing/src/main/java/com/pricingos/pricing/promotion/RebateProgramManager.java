package com.pricingos.pricing.promotion;

import com.pricingos.common.IRebateService;
import com.pricingos.common.ValidationUtils;

import java.util.Map;
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

    private final Map<String, ProgramState> programRegistry = new ConcurrentHashMap<>();
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
        ProgramState program = new ProgramState(programId, customerId, skuId, targetSpend, rebatePct);
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
        ProgramState program = getProgram(programId);
        program.addSpend(purchaseAmount);
    }

    /** {@inheritDoc} */
    @Override
    public double getRebateDue(String programId) {
        return getProgram(programId).rebateDue();
    }

    /** {@inheritDoc} */
    @Override
    public boolean isTargetMet(String programId) {
        return getProgram(programId).targetMet();
    }

    /** {@inheritDoc} */
    @Override
    public double getAccumulatedSpend(String programId) {
        return getProgram(programId).spend();
    }

    private ProgramState getProgram(String programId) {
        String normalizedProgramId = ValidationUtils.requireNonBlank(programId, "programId");
        ProgramState p = programRegistry.get(normalizedProgramId);
        if (p == null)
            throw new IllegalArgumentException("No rebate programme found with ID: " + normalizedProgramId);
        return p;
    }

    private static final class ProgramState {
        private final String programId;
        private final String customerId;
        private final String skuId;
        private final double targetSpend;
        private final double rebatePct;
        private double accumulatedSpend;

        private ProgramState(String programId, String customerId, String skuId, double targetSpend, double rebatePct) {
            this.programId = ValidationUtils.requireNonBlank(programId, "programId");
            this.customerId = ValidationUtils.requireNonBlank(customerId, "customerId");
            this.skuId = ValidationUtils.requireNonBlank(skuId, "skuId");
            if (!Double.isFinite(targetSpend) || targetSpend <= 0) {
                throw new IllegalArgumentException("targetSpend must be a positive finite number");
            }
            if (!Double.isFinite(rebatePct) || rebatePct < 0 || rebatePct > 100) {
                throw new IllegalArgumentException("rebatePct must be in [0, 100]");
            }
            this.targetSpend = targetSpend;
            this.rebatePct = rebatePct;
            this.accumulatedSpend = 0.0;
        }

        private synchronized void addSpend(double amount) {
            if (!Double.isFinite(amount) || amount < 0) {
                throw new IllegalArgumentException("purchaseAmount must be a non-negative finite number");
            }
            this.accumulatedSpend += amount;
        }

        private synchronized boolean targetMet() {
            return accumulatedSpend >= targetSpend;
        }

        private synchronized double rebateDue() {
            if (accumulatedSpend < targetSpend) {
                return 0.0;
            }
            return accumulatedSpend * (rebatePct / 100.0);
        }

        private synchronized double spend() {
            return accumulatedSpend;
        }
    }
}
