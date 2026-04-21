package com.pricingos.pricing.promotion;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Component 5 — Rebate &amp; Loyalty Programme feature.
 *
 * <p>Covers: programme creation, spend accumulation, threshold detection,
 * rebate calculation below/at/above target, invalid input rejection.
 */
class RebateProgramManagerTest {

    private RebateProgramManager manager;

    
    @org.junit.jupiter.api.AfterEach
    void clearDaoBulk() {
        com.pricingos.pricing.db.DaoBulk.clearAll();
    }

    @BeforeEach
    void setUp() {
        manager = new RebateProgramManager(new RebateProgramManager.InMemoryRebateStore());
    }

    // ── Create programme ──────────────────────────────────────────────────────────

    @Test
    void createRebateProgram_returnsId() {
        String id = manager.createRebateProgram("CUST-01", "SKU-001", 1000.0, 5.0);
        assertNotNull(id);
        assertTrue(id.startsWith("RBT-"));
    }

    @Test
    void createRebateProgram_negativeTarget_throws() {
        assertThrows(IllegalArgumentException.class, () ->
            manager.createRebateProgram("CUST-01", "SKU-001", -500.0, 5.0));
    }

    @Test
    void createRebateProgram_rebatePctOver100_throws() {
        assertThrows(IllegalArgumentException.class, () ->
            manager.createRebateProgram("CUST-01", "SKU-001", 1000.0, 110.0));
    }

    @Test
    void createRebateProgram_blankCustomerId_throws() {
        assertThrows(IllegalArgumentException.class, () ->
            manager.createRebateProgram("  ", "SKU-001", 1000.0, 5.0));
    }

    // ── recordPurchase ────────────────────────────────────────────────────────────

    @Test
    void recordPurchase_accumulatesSpend() {
        String id = manager.createRebateProgram("CUST-01", "SKU-001", 1000.0, 5.0);
        manager.recordPurchase(id, 300.0);
        manager.recordPurchase(id, 200.0);
        assertEquals(500.0, manager.getAccumulatedSpend(id), 0.001);
    }

    @Test
    void recordPurchase_negativeAmount_throws() {
        String id = manager.createRebateProgram("CUST-01", "SKU-001", 1000.0, 5.0);
        assertThrows(IllegalArgumentException.class, () ->
            manager.recordPurchase(id, -100.0));
    }

    @Test
    void recordPurchase_unknownProgramId_throws() {
        assertThrows(IllegalArgumentException.class, () ->
            manager.recordPurchase("RBT-9999", 100.0));
    }

    // ── isTargetMet ───────────────────────────────────────────────────────────────

    @Test
    void isTargetMet_belowTarget_returnsFalse() {
        String id = manager.createRebateProgram("CUST-01", "SKU-001", 1000.0, 5.0);
        manager.recordPurchase(id, 500.0);
        assertFalse(manager.isTargetMet(id));
    }

    @Test
    void isTargetMet_exactlyAtTarget_returnsTrue() {
        String id = manager.createRebateProgram("CUST-01", "SKU-001", 1000.0, 5.0);
        manager.recordPurchase(id, 1000.0);
        assertTrue(manager.isTargetMet(id));
    }

    @Test
    void isTargetMet_aboveTarget_returnsTrue() {
        String id = manager.createRebateProgram("CUST-01", "SKU-001", 1000.0, 5.0);
        manager.recordPurchase(id, 1500.0);
        assertTrue(manager.isTargetMet(id));
    }

    // ── getRebateDue ──────────────────────────────────────────────────────────────

    @Test
    void getRebateDue_belowTarget_returnsZero() {
        String id = manager.createRebateProgram("CUST-01", "SKU-001", 1000.0, 5.0);
        manager.recordPurchase(id, 600.0);
        assertEquals(0.0, manager.getRebateDue(id), 0.001);
    }

    @Test
    void getRebateDue_atTarget_returnsCorrectRebate() {
        String id = manager.createRebateProgram("CUST-01", "SKU-001", 1000.0, 5.0);
        manager.recordPurchase(id, 1000.0);
        // 5% of 1000 = 50
        assertEquals(50.0, manager.getRebateDue(id), 0.001);
    }

    @Test
    void getRebateDue_aboveTarget_returnsRebateOnFullAccumulated() {
        String id = manager.createRebateProgram("CUST-01", "SKU-001", 1000.0, 10.0);
        manager.recordPurchase(id, 500.0);
        manager.recordPurchase(id, 800.0); // Total: 1300
        // 10% of 1300 = 130 (rebate is on total spend, not just the excess)
        assertEquals(130.0, manager.getRebateDue(id), 0.001);
    }

    @Test
    void getRebateDue_zeroRebatePct_returnsZeroEvenIfTargetMet() {
        String id = manager.createRebateProgram("CUST-01", "SKU-001", 500.0, 0.0);
        manager.recordPurchase(id, 500.0);
        assertEquals(0.0, manager.getRebateDue(id), 0.001);
    }

    @Test
    void getRebateDue_multiplePurchases_correctlyAccumulates() {
        // 5% rebate at ₹2000 target
        String id = manager.createRebateProgram("CUST-Q1", "SKU-001", 2000.0, 5.0);
        // Quarterly purchases
        manager.recordPurchase(id, 500.0);
        manager.recordPurchase(id, 750.0);
        manager.recordPurchase(id, 800.0); // Total: 2050
        assertTrue(manager.isTargetMet(id));
        // 5% of 2050 = 102.5
        assertEquals(102.5, manager.getRebateDue(id), 0.001);
    }
}
