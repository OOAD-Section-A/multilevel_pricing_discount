package com.pricingos.pricing.promotion;

import com.jackfruit.scm.database.adapter.PricingAdapter;
import com.jackfruit.scm.database.model.PricingModels.RebateProgram;
import com.pricingos.common.IRebateService;
import com.pricingos.common.ValidationUtils;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

public class RebateProgramManager implements IRebateService {

    private final AtomicInteger idCounter = new AtomicInteger();
    private final PricingAdapter pricingAdapter;

    public RebateProgramManager(PricingAdapter pricingAdapter) {
        this.pricingAdapter = pricingAdapter;
    }

    @Override
    public String createRebateProgram(String customerId, String skuId,
                                      double targetSpend, double rebatePct) {
        String programId = "RBT-" + idCounter.incrementAndGet();
        RebateProgram program = new RebateProgram(
            programId,
            customerId,
            skuId,
            BigDecimal.valueOf(targetSpend),
            BigDecimal.ZERO,  // accumulatedSpend starts at 0
            BigDecimal.valueOf(rebatePct / 100.0)  // Convert from 0-100 scale to 0-1 scale
        );
        pricingAdapter.createRebateProgram(program);
        return programId;
    }

    @Override
    public void recordPurchase(String programId, double purchaseAmount) {
        Optional<RebateProgram> programOpt = pricingAdapter.getRebateProgram(programId);
        if (programOpt.isEmpty()) {
            throw new IllegalArgumentException("No rebate programme found with ID: " + programId);
        }
        RebateProgram program = programOpt.get();
        BigDecimal newAccumulatedSpend = program.accumulatedSpend().add(BigDecimal.valueOf(purchaseAmount));
        pricingAdapter.updateRebateAccumulatedSpend(programId, newAccumulatedSpend);
    }

    @Override
    public double getRebateDue(String programId) {
        Optional<RebateProgram> programOpt = pricingAdapter.getRebateProgram(programId);
        if (programOpt.isEmpty()) {
            throw new IllegalArgumentException("No rebate programme found with ID: " + programId);
        }
        RebateProgram program = programOpt.get();
        if (program.accumulatedSpend().compareTo(program.targetSpend()) < 0) {
            return 0.0;
        }
        return program.accumulatedSpend().multiply(program.rebatePct()).doubleValue();
    }

    @Override
    public boolean isTargetMet(String programId) {
        Optional<RebateProgram> programOpt = pricingAdapter.getRebateProgram(programId);
        if (programOpt.isEmpty()) {
            throw new IllegalArgumentException("No rebate programme found with ID: " + programId);
        }
        RebateProgram program = programOpt.get();
        return program.accumulatedSpend().compareTo(program.targetSpend()) >= 0;
    }

    @Override
    public double getAccumulatedSpend(String programId) {
        Optional<RebateProgram> programOpt = pricingAdapter.getRebateProgram(programId);
        if (programOpt.isEmpty()) {
            throw new IllegalArgumentException("No rebate programme found with ID: " + programId);
        }
        return programOpt.get().accumulatedSpend().doubleValue();
    }
}
