package com.pricingos.pricing.promotion;

import com.jackfruit.scm.database.model.PricingModels.RebateProgram;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RebateProgramMathTest {

    @Test
    void toStoredRate_convertsPercentInputToFraction() {
        assertEquals(0, new BigDecimal("0.05").compareTo(RebateProgramMath.toStoredRate(5.0)));
    }

    @Test
    void toDisplayPercent_convertsStoredFractionToUiPercent() {
        assertEquals(7.5, RebateProgramMath.toDisplayPercent(new BigDecimal("0.075")), 0.001);
    }

    @Test
    void calculateDue_usesStoredFractionWhenTargetIsMet() {
        RebateProgram program = new RebateProgram(
                "RBT-1",
                "CUST-1",
                "SKU-1",
                new BigDecimal("1000"),
                new BigDecimal("1500"),
                new BigDecimal("0.05"));

        assertEquals(75.0, RebateProgramMath.calculateDue(program), 0.001);
    }

    @Test
    void calculateProgressPercent_handlesZeroTargetSpend() {
        RebateProgram program = new RebateProgram(
                "RBT-1",
                "CUST-1",
                "SKU-1",
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("0.05"));

        assertTrue(RebateProgramMath.isTargetMet(program));
        assertEquals(100.0, RebateProgramMath.calculateProgressPercent(program), 0.001);
    }

    @Test
    void isTargetMet_returnsFalseWhenAccumulatedSpendIsBelowTarget() {
        RebateProgram program = new RebateProgram(
                "RBT-1",
                "CUST-1",
                "SKU-1",
                new BigDecimal("1000"),
                new BigDecimal("500"),
                new BigDecimal("0.05"));

        assertFalse(RebateProgramMath.isTargetMet(program));
        assertEquals(0.0, RebateProgramMath.calculateDue(program), 0.001);
    }
}
