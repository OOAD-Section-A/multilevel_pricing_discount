package com.pricingos.pricing.approval;

import com.pricingos.common.ApprovalRequestType;
import com.pricingos.common.ApprovalStatus;
import com.pricingos.common.IApproverRoleService;
import com.pricingos.common.IFloorPriceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.DoubleSummaryStatistics;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Component 8 new features:
 * <ul>
 *   <li>Margin Floor Protection — blocks approval when discount breaches floor price.</li>
 *   <li>Profitability Analytics — tracks approved/rejected discount totals and breakdown.</li>
 *   <li>Escalation bug fix — verifies escalation uses current routed approver, not original.</li>
 * </ul>
 */
class ApprovalWorkflowMarginTest {

    /** Stub: all managers can approve; escalation from MGR-001 → MGR-REGIONAL. */
    private static final IApproverRoleService STUB_ROLE_SERVICE = new IApproverRoleService() {
        @Override
        public boolean canApprove(String approverId, ApprovalRequestType type, double amount) {
            return !approverId.equals("UNAUTHORIZED");
        }
        @Override
        public String getEscalationManagerId(String employeeId) {
            return "MGR-001".equals(employeeId) ? "MGR-REGIONAL" : null;
        }
    };

    /** Stub routing: always routes to MGR-001. */
    private static final ApprovalRoutingStrategy STUB_STRATEGY = new ApprovalRoutingStrategy() {
        @Override public String resolveApproverId(ApprovalRequest r)  { return "MGR-001"; }
        @Override public boolean requiresDualApproval(ApprovalRequest r) { return false; }
    };

    /**
     * Stub floor price service:
     * - Orders with total > 1000 AND discount > 200 violate margin.
     * - Floor price is always 800.
     */
    private static final IFloorPriceService VIOLATING_FLOOR_SERVICE = new IFloorPriceService() {
        @Override public boolean wouldViolateMargin(String orderId, double discountAmount) {
            return discountAmount > 200.0;
        }
        @Override public double getEffectiveFloorPrice(String orderId) { return 800.0; }
    };

    /** Stub floor price service that never blocks. */
    private static final IFloorPriceService PERMISSIVE_FLOOR_SERVICE = new IFloorPriceService() {
        @Override public boolean wouldViolateMargin(String orderId, double discountAmount) { return false; }
        @Override public double getEffectiveFloorPrice(String orderId) { return 0.0; }
    };

    private ApprovalWorkflowEngine engine;

    @BeforeEach
    void setUp() {
        engine = new ApprovalWorkflowEngine(STUB_STRATEGY, STUB_ROLE_SERVICE);
    }

    // ── Margin Protection ─────────────────────────────────────────────────────────

    @Test
    void approve_withViolatingDiscount_throwsMarginViolationException() {
        engine.withFloorPriceService(VIOLATING_FLOOR_SERVICE);
        String id = engine.submitOverrideRequest(
            "EMP-01", ApprovalRequestType.MANUAL_DISCOUNT, "ORD-001", 300.0, "Damage comp");

        // discount=300 > 200 threshold → should throw
        assertThrows(MarginViolationException.class, () -> engine.approve(id, "MGR-001"));
    }

    @Test
    void approve_withViolatingDiscount_statusRemainsUnchanged() {
        engine.withFloorPriceService(VIOLATING_FLOOR_SERVICE);
        String id = engine.submitOverrideRequest(
            "EMP-01", ApprovalRequestType.MANUAL_DISCOUNT, "ORD-001", 300.0, "Damage comp");

        try { engine.approve(id, "MGR-001"); } catch (MarginViolationException ignored) {}

        // Must remain PENDING — the approval was blocked, not recorded
        assertEquals(ApprovalStatus.PENDING, engine.getStatus(id));
    }

    @Test
    void approve_withViolatingDiscount_notifiesObserver() {
        engine.withFloorPriceService(VIOLATING_FLOOR_SERVICE);

        // Observer that captures margin violation events
        boolean[] violationFired = {false};
        engine.addObserver(new ApprovalEventObserver() {
            @Override public void onRequestSubmitted(ApprovalRequest r, String a) {}
            @Override public void onRequestApproved(ApprovalRequest r)             {}
            @Override public void onRequestRejected(ApprovalRequest r)             {}
            @Override public void onRequestEscalated(ApprovalRequest r, String t)  {}
            @Override public void onMarginViolationBlocked(ApprovalRequest r, double fp) {
                violationFired[0] = true;
            }
        });

        String id = engine.submitOverrideRequest(
            "EMP-01", ApprovalRequestType.MANUAL_DISCOUNT, "ORD-001", 250.0, "Test");
        try { engine.approve(id, "MGR-001"); } catch (MarginViolationException ignored) {}

        assertTrue(violationFired[0], "Margin violation observer should have been called.");
    }

    @Test
    void approve_withPermissiveFloorService_succeedsNormally() {
        engine.withFloorPriceService(PERMISSIVE_FLOOR_SERVICE);
        String id = engine.submitOverrideRequest(
            "EMP-01", ApprovalRequestType.MANUAL_DISCOUNT, "ORD-001", 500.0, "Test");

        assertDoesNotThrow(() -> engine.approve(id, "MGR-001"));
        assertEquals(ApprovalStatus.APPROVED, engine.getStatus(id));
    }

    @Test
    void approve_withNoFloorPriceService_succeedsNormally() {
        // No floor price service configured → backward-compatible, no margin check
        String id = engine.submitOverrideRequest(
            "EMP-01", ApprovalRequestType.MANUAL_DISCOUNT, "ORD-001", 9999.0, "Test");
        assertDoesNotThrow(() -> engine.approve(id, "MGR-001"));
        assertEquals(ApprovalStatus.APPROVED, engine.getStatus(id));
    }

    @Test
    void marginViolationException_containsCorrectDetails() {
        engine.withFloorPriceService(VIOLATING_FLOOR_SERVICE);
        String id = engine.submitOverrideRequest(
            "EMP-01", ApprovalRequestType.MANUAL_DISCOUNT, "ORD-001", 300.0, "Test");

        MarginViolationException ex = assertThrows(MarginViolationException.class,
            () -> engine.approve(id, "MGR-001"));

        assertEquals("ORD-001", ex.getOrderId());
        assertEquals(800.0, ex.getEffectiveFloorPrice(), 0.001);
        assertEquals(300.0, ex.getRequestedDiscountAmount(), 0.001);
    }

    // ── Profitability Analytics ───────────────────────────────────────────────────

    @Test
    void analyticsObserver_tracksApprovedRevenueDelta() {
        ProfitabilityAnalyticsObserver analytics = new ProfitabilityAnalyticsObserver();
        engine.addObserver(analytics);

        String id1 = submit("EMP-01", ApprovalRequestType.MANUAL_DISCOUNT, 200.0);
        String id2 = submit("EMP-02", ApprovalRequestType.CONTRACT_BYPASS,  300.0);
        engine.approve(id1, "MGR-001");
        engine.approve(id2, "MGR-001");

        assertEquals(500.0, analytics.getApprovedRevenueDelta(), 0.001);
    }

    @Test
    void analyticsObserver_tracksRejectedSavings() {
        ProfitabilityAnalyticsObserver analytics = new ProfitabilityAnalyticsObserver();
        engine.addObserver(analytics);

        String id1 = submit("EMP-01", ApprovalRequestType.MANUAL_DISCOUNT, 150.0);
        String id2 = submit("EMP-02", ApprovalRequestType.POLICY_EXCEPTION,  75.0);
        engine.reject(id1, "MGR-001", "Exceeds policy limit.");
        engine.reject(id2, "MGR-001", "Not applicable.");

        assertEquals(225.0, analytics.getRejectedSavings(), 0.001);
    }

    @Test
    void analyticsObserver_breakdownByType_correctGrouping() {
        ProfitabilityAnalyticsObserver analytics = new ProfitabilityAnalyticsObserver();
        engine.addObserver(analytics);

        String id1 = submit("EMP-01", ApprovalRequestType.MANUAL_DISCOUNT, 100.0);
        String id2 = submit("EMP-02", ApprovalRequestType.MANUAL_DISCOUNT, 200.0);
        String id3 = submit("EMP-03", ApprovalRequestType.CONTRACT_BYPASS,  500.0);
        engine.approve(id1, "MGR-001");
        engine.approve(id2, "MGR-001");
        engine.approve(id3, "MGR-001");

        Map<ApprovalRequestType, DoubleSummaryStatistics> breakdown = analytics.getBreakdownByType();
        assertEquals(300.0, breakdown.get(ApprovalRequestType.MANUAL_DISCOUNT).getSum(), 0.001);
        assertEquals(500.0, breakdown.get(ApprovalRequestType.CONTRACT_BYPASS).getSum(), 0.001);
    }

    @Test
    void analyticsObserver_totalDecisions_countsApprovedAndRejected() {
        ProfitabilityAnalyticsObserver analytics = new ProfitabilityAnalyticsObserver();
        engine.addObserver(analytics);

        String id1 = submit("EMP-01", ApprovalRequestType.MANUAL_DISCOUNT, 100.0);
        String id2 = submit("EMP-02", ApprovalRequestType.CONTRACT_BYPASS,  200.0);
        engine.approve(id1, "MGR-001");
        engine.reject(id2, "MGR-001", "Not justified.");

        assertEquals(2, analytics.getTotalDecisions());
    }

    // ── Escalation bug fix verification ──────────────────────────────────────────

    @Test
    void escalateStaleRequests_escalatesFromCurrentApprover_notOriginalSubmitter() {
        SettableClock clock = new SettableClock();
        ApprovalWorkflowEngine clockedEngine =
            new ApprovalWorkflowEngine(STUB_STRATEGY, STUB_ROLE_SERVICE, clock);

        // Submit: routed to MGR-001 by STUB_STRATEGY
        String id = clockedEngine.submitOverrideRequest(
            "EMP-01", ApprovalRequestType.MANUAL_DISCOUNT, "ORD-010", 100.0, "Test");

        // Advance past 48h → should escalate from MGR-001 → MGR-REGIONAL
        clock.advance(Duration.ofHours(49));
        clockedEngine.escalateStaleRequests();

        assertEquals(ApprovalStatus.ESCALATED, clockedEngine.getStatus(id));

        // Verify the request is now visible in MGR-REGIONAL's pending queue
        // (i.e., routedToApproverId was updated to MGR-REGIONAL, not back to MGR-001)
        ApprovalRequest request = clockedEngine.getRequestById(id);
        assertEquals("MGR-REGIONAL", request.getRoutedToApproverId(),
            "After escalation the request should be routed to MGR-REGIONAL, not back to MGR-001");
    }

    // ── Helper ────────────────────────────────────────────────────────────────────

    private String submit(String employee, ApprovalRequestType type, double amount) {
        return engine.submitOverrideRequest(employee, type, "ORD-001", amount, "Test justification");
    }

    /** Controllable clock for deterministic SLA tests. */
    private static class SettableClock extends Clock {
        private Instant now = Instant.now();
        private final ZoneId zone = ZoneId.systemDefault();

        void advance(Duration d) { now = now.plus(d); }

        @Override public ZoneId getZone()         { return zone; }
        @Override public Clock withZone(ZoneId z) { return this; }
        @Override public Instant instant()        { return now; }
    }
}
