package com.pricingos.pricing.promotion;

import com.jackfruit.scm.database.adapter.PricingAdapter;
import com.jackfruit.scm.database.model.PricingModels.RebateProgram;
import com.pricingos.common.IRebateService;
import com.pricingos.common.ValidationUtils;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class RebateProgramManager implements IRebateService {

    private final RebateStore rebateStore;

    public RebateProgramManager(PricingAdapter pricingAdapter) {
        this(new DatabaseRebateStore(pricingAdapter));
    }

    RebateProgramManager(RebateStore rebateStore) {
        this.rebateStore = Objects.requireNonNull(rebateStore, "rebateStore cannot be null");
    }

    @Override
    public String createRebateProgram(String customerId, String skuId,
                                      double targetSpend, double rebatePct) {
        String normalizedCustomerId = ValidationUtils.requireNonBlank(customerId, "customerId");
        String normalizedSkuId = ValidationUtils.requireNonBlank(skuId, "skuId");
        if (!Double.isFinite(targetSpend) || targetSpend < 0) {
            throw new IllegalArgumentException("targetSpend must be a non-negative finite number");
        }
        if (!Double.isFinite(rebatePct) || rebatePct < 0 || rebatePct > 100) {
            throw new IllegalArgumentException("rebatePct must be between 0 and 100");
        }

        RebateProgram program = new RebateProgram(
                "RBT-" + java.util.UUID.randomUUID(),
                normalizedCustomerId,
                normalizedSkuId,
                BigDecimal.valueOf(targetSpend),
                BigDecimal.ZERO,
                RebateProgramMath.toStoredRate(rebatePct)
        );
        rebateStore.save(program);
        return program.programId();
    }

    @Override
    public void recordPurchase(String programId, double purchaseAmount) {
        String normalizedProgramId = ValidationUtils.requireNonBlank(programId, "programId");
        if (!Double.isFinite(purchaseAmount) || purchaseAmount < 0) {
            throw new IllegalArgumentException("purchaseAmount must be a non-negative finite number");
        }
        RebateProgram program = rebateStore.get(normalizedProgramId);
        if (program == null) {
            throw new IllegalArgumentException("No rebate programme found with ID: " + normalizedProgramId);
        }
        rebateStore.updateAccumulatedSpend(
                normalizedProgramId,
                program.accumulatedSpend().add(BigDecimal.valueOf(purchaseAmount)));
    }

    @Override
    public double getRebateDue(String programId) {
        RebateProgram program = requireProgram(programId);
        return RebateProgramMath.calculateDue(program);
    }

    @Override
    public boolean isTargetMet(String programId) {
        return RebateProgramMath.isTargetMet(requireProgram(programId));
    }

    @Override
    public double getAccumulatedSpend(String programId) {
        return requireProgram(programId).accumulatedSpend().doubleValue();
    }

    private RebateProgram requireProgram(String programId) {
        String normalizedProgramId = ValidationUtils.requireNonBlank(programId, "programId");
        RebateProgram program = rebateStore.get(normalizedProgramId);
        if (program == null) {
            throw new IllegalArgumentException("No rebate programme found with ID: " + normalizedProgramId);
        }
        return program;
    }

    interface RebateStore {
        void save(RebateProgram program);
        RebateProgram get(String programId);
        void updateAccumulatedSpend(String programId, BigDecimal newAmount);
    }

    static final class InMemoryRebateStore implements RebateStore {
        private final Map<String, RebateProgram> programsById = new ConcurrentHashMap<>();

        @Override
        public void save(RebateProgram program) {
            programsById.put(program.programId(), program);
        }

        @Override
        public RebateProgram get(String programId) {
            return programsById.get(programId);
        }

        @Override
        public void updateAccumulatedSpend(String programId, BigDecimal newAmount) {
            RebateProgram current = programsById.get(programId);
            if (current == null) {
                throw new IllegalArgumentException("No rebate programme found with ID: " + programId);
            }
            programsById.put(programId, new RebateProgram(
                    current.programId(),
                    current.customerId(),
                    current.skuId(),
                    current.targetSpend(),
                    newAmount,
                    current.rebatePct()));
        }
    }

    private static final class DatabaseRebateStore implements RebateStore {
        private final PricingAdapter pricingAdapter;

        private DatabaseRebateStore(PricingAdapter pricingAdapter) {
            this.pricingAdapter = Objects.requireNonNull(pricingAdapter, "pricingAdapter cannot be null");
        }

        @Override
        public void save(RebateProgram program) {
            pricingAdapter.createRebateProgram(program);
        }

        @Override
        public RebateProgram get(String programId) {
            return pricingAdapter.getRebateProgram(programId).orElse(null);
        }

        @Override
        public void updateAccumulatedSpend(String programId, BigDecimal newAmount) {
            pricingAdapter.updateRebateAccumulatedSpend(programId, newAmount);
        }
    }
}
