package com.pricingos.pricing.promotion;

import com.jackfruit.scm.database.model.PricingModels.RebateProgram;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public final class RebateProgramMath {

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    private RebateProgramMath() {
    }

    public static BigDecimal toStoredRate(double rebatePercent) {
        return BigDecimal.valueOf(rebatePercent).movePointLeft(2);
    }

    public static double toDisplayPercent(BigDecimal storedRate) {
        return Objects.requireNonNull(storedRate, "storedRate cannot be null")
                .movePointRight(2)
                .doubleValue();
    }

    public static boolean isTargetMet(RebateProgram program) {
        RebateProgram nonNullProgram = Objects.requireNonNull(program, "program cannot be null");
        return nonNullProgram.accumulatedSpend().compareTo(nonNullProgram.targetSpend()) >= 0;
    }

    public static double calculateDue(RebateProgram program) {
        RebateProgram nonNullProgram = Objects.requireNonNull(program, "program cannot be null");
        if (!isTargetMet(nonNullProgram)) {
            return 0.0;
        }
        return nonNullProgram.accumulatedSpend()
                .multiply(nonNullProgram.rebatePct())
                .doubleValue();
    }

    public static double calculateProgressPercent(RebateProgram program) {
        RebateProgram nonNullProgram = Objects.requireNonNull(program, "program cannot be null");
        if (nonNullProgram.targetSpend().signum() == 0) {
            return 100.0;
        }
        return nonNullProgram.accumulatedSpend()
                .multiply(ONE_HUNDRED)
                .divide(nonNullProgram.targetSpend(), 4, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
