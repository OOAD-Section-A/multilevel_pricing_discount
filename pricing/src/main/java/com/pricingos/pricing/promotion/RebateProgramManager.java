package com.pricingos.pricing.promotion;

import com.pricingos.common.IRebateService;
import com.pricingos.common.ValidationUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.pricingos.pricing.db.DaoBulk.RebateDao;
import java.util.concurrent.atomic.AtomicInteger;

public class RebateProgramManager implements IRebateService {

    
    private final AtomicInteger idCounter = new AtomicInteger();

    @Override
    public String createRebateProgram(String customerId, String skuId,
                                      double targetSpend, double rebatePct) {
        String programId = "RBT-" + idCounter.incrementAndGet();
        ProgramState program = new ProgramState(programId, customerId, skuId, targetSpend, rebatePct);
        RebateDao.save(program);
        return programId;
    }

    @Override
    public void recordPurchase(String programId, double purchaseAmount) {
        ProgramState program = getProgram(programId);
        program.addSpend(purchaseAmount);
    }

    @Override
    public double getRebateDue(String programId) {
        return getProgram(programId).rebateDue();
    }

    @Override
    public boolean isTargetMet(String programId) {
        return getProgram(programId).targetMet();
    }

    @Override
    public double getAccumulatedSpend(String programId) {
        return getProgram(programId).spend();
    }

    private ProgramState getProgram(String programId) {
        String normalizedProgramId = ValidationUtils.requireNonBlank(programId, "programId");
        ProgramState p = (ProgramState) RebateDao.get(normalizedProgramId, ProgramState.class);
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
