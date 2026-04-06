package com.pricingos.pricing.approval;

import com.pricingos.common.ApprovalRequestType;
import com.pricingos.common.ApprovalStatus;
import com.pricingos.common.IApproverRoleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Component 8 — Price Approval & Workflow Engine.
 *
 * <p>Covers:
 * <ul>
 *   <li>Happy-path approval and rejection flows.</li>
 *   <li>APPROVAL_ESCALATION_TIMEOUT exception handling.</li>
 *   <li>OVERRIDE_REQUEST_REJECTED exception handling.</li>
 *   <li>Observer notification verification.</li>
 *   <li>Authority checks via the IApproverRoleService stub (T3N50R boundary).</li>
 * </ul>
 */
class ApprovalWorkflowTest {

    /** Stub: all managers can approve anything; escalation target is always "MGR-REGIONAL". */
    private static final IApproverRoleService STUB_ROLE_SERVICE = new IApproverRoleService() {
        @Override
        public boolean canApprove(String approverId, ApprovalRequestType type, double amount) {
            return !approverId.equals("UNAUTHORIZED");
        }
        @Override
        public String getEscalationManagerId(String employeeId) {
            return "MGR-REGIONAL";
        }
    };

    /** Stub routing strategy: always routes to "MGR-001". */
    private static final ApprovalRoutingStrategy STUB_STRATEGY = new ApprovalRoutingStrategy() {
        @Override public String resolveApproverId(ApprovalRequest r) { return "MGR-001"; }
        @Override public boolean requiresDualApproval(ApprovalRequest r) { return false; }
    };

    /** Capturing observer for test assertions. */
    private static class CapturingObserver implements ApprovalEventObserver {
        final List<String> events = new ArrayList<>();
        @Override public void onRequestSubmitted(ApprovalRequest r, String approverId) { events.add("SUBMITTED:" + r.getApprovalId()); }
        @Override public void onRequestApproved(ApprovalRequest r)                     { events.add("APPROVED:"  + r.getApprovalId()); }
        @Override public void onRequestRejected(ApprovalRequest r)                     { events.add("REJECTED:"  + r.getApprovalId()); }
        @Override public void onRequestEscalated(ApprovalRequest r, String target)     { events.add("ESCALATED:" + r.getApprovalId() + "→" + target); }
    }

    private ApprovalWorkflowEngine engine;
    private CapturingObserver observer;

    @BeforeEach
    void setUp() {
        engine = new ApprovalWorkflowEngine(STUB_STRATEGY, STUB_ROLE_SERVICE);
        observer = new CapturingObserver();
        engine.addObserver(observer);
    }

    // ── Submit ────────────────────────────────────────────────────────────────────

    @Test
    void submitOverrideRequest_returnsApprovalId_andNotifiesObserver() {
        String id = submitRequest("EMP-01", ApprovalRequestType.MANUAL_DISCOUNT, 200.0);
        assertNotNull(id);
        assertTrue(id.startsWith("APR-"));
        assertEquals(ApprovalStatus.PENDING, engine.getStatus(id));
        assertTrue(observer.events.stream().anyMatch(e -> e.startsWith("SUBMITTED:" + id)));
    }

    @Test
    void submitOverrideRequest_blankRequestedBy_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> engine.submitOverrideRequest("  ", ApprovalRequestType.MANUAL_DISCOUNT,
                "ORD-001", 100.0, "Damage discount"));
    }

    // ── Approve ───────────────────────────────────────────────────────────────────

    @Test
    void approve_validApprover_changesStatusToApproved() {
        String id = submitRequest("EMP-01", ApprovalRequestType.MANUAL_DISCOUNT, 200.0);
        engine.approve(id, "MGR-001");
        assertEquals(ApprovalStatus.APPROVED, engine.getStatus(id));
        assertTrue(observer.events.stream().anyMatch(e -> e.startsWith("APPROVED:" + id)));
    }

    @Test
    void approve_unauthorizedApprover_throws() {
        String id = submitRequest("EMP-01", ApprovalRequestType.MANUAL_DISCOUNT, 200.0);
        assertThrows(IllegalArgumentException.class, () -> engine.approve(id, "UNAUTHORIZED"));
        // Status must remain PENDING after a failed approval attempt.
        assertEquals(ApprovalStatus.PENDING, engine.getStatus(id));
    }

    @Test
    void approve_alreadyApproved_throws() {
        String id = submitRequest("EMP-01", ApprovalRequestType.MANUAL_DISCOUNT, 100.0);
        engine.approve(id, "MGR-001");
        assertThrows(IllegalStateException.class, () -> engine.approve(id, "MGR-001"));
    }

    // ── Reject ────────────────────────────────────────────────────────────────────

    @Test
    void reject_validApprover_changesStatusToRejected_andNotifies() {
        String id = submitRequest("EMP-01", ApprovalRequestType.MANUAL_DISCOUNT, 200.0);
        engine.reject(id, "MGR-001", "Discount too high for this product category.");
        assertEquals(ApprovalStatus.REJECTED, engine.getStatus(id));
        assertTrue(observer.events.stream().anyMatch(e -> e.startsWith("REJECTED:" + id)));
    }

    @Test
    void reject_setsRejectionReason_inAuditTrail() {
        AuditLogObserver auditLog = new AuditLogObserver();
        engine.addObserver(auditLog);
        String id = submitRequest("EMP-01", ApprovalRequestType.POLICY_EXCEPTION, 500.0);
        engine.reject(id, "MGR-001", "Policy does not permit this exception type.");
        boolean found = auditLog.getAuditLog().stream()
            .anyMatch(e -> e.eventType().equals("REJECTED") && e.approvalId().equals(id));
        assertTrue(found, "Audit log should contain a REJECTED entry.");
    }

    // ── Escalation (APPROVAL_ESCALATION_TIMEOUT) ──────────────────────────────────

    @Test
    void escalateStaleRequests_pendingRequestOnSubmission_remainsPending() {
        // A brand-new request has 0 pending hours — should NOT be escalated.
        String id = submitRequest("EMP-01", ApprovalRequestType.MANUAL_DISCOUNT, 100.0);
        engine.escalateStaleRequests();
        assertEquals(ApprovalStatus.PENDING, engine.getStatus(id),
            "A freshly submitted request should not be escalated immediately.");
    }

    @Test
    void escalateStaleRequests_noActionOnApprovedRequest() {
        String id = submitRequest("EMP-01", ApprovalRequestType.MANUAL_DISCOUNT, 100.0);
        engine.approve(id, "MGR-001");
        engine.escalateStaleRequests(); // should be a no-op for APPROVED requests
        assertEquals(ApprovalStatus.APPROVED, engine.getStatus(id));
    }

    // ── Observer ──────────────────────────────────────────────────────────────────

    @Test
    void removeObserver_stopsReceivingEvents() {
        engine.removeObserver(observer);
        submitRequest("EMP-01", ApprovalRequestType.MANUAL_DISCOUNT, 100.0);
        assertTrue(observer.events.isEmpty(), "Removed observer should not receive events.");
    }

    @Test
    void multipleObservers_allReceiveEvents() {
        CapturingObserver second = new CapturingObserver();
        engine.addObserver(second);
        String id = submitRequest("EMP-01", ApprovalRequestType.CONTRACT_BYPASS, 300.0);
        assertTrue(observer.events.stream().anyMatch(e -> e.startsWith("SUBMITTED:" + id)));
        assertTrue(second.events.stream().anyMatch(e -> e.startsWith("SUBMITTED:" + id)));
    }

    // ── GetPendingApprovals ───────────────────────────────────────────────────────

    @Test
    void getPendingApprovals_returnsOnlyPendingRequests() {
        String id1 = submitRequest("EMP-01", ApprovalRequestType.MANUAL_DISCOUNT, 100.0);
        String id2 = submitRequest("EMP-02", ApprovalRequestType.CONTRACT_BYPASS, 200.0);
        engine.approve(id1, "MGR-001");

        List<String> pending = engine.getPendingApprovals("MGR-001");
        assertFalse(pending.contains(id1), "Approved request should not appear as pending.");
        assertTrue(pending.contains(id2), "Unanswered request should appear as pending.");
    }

    // ── AuditLog integration ──────────────────────────────────────────────────────

    @Test
    void auditLogObserver_recordsFullLifecycle() {
        AuditLogObserver auditLog = new AuditLogObserver();
        engine.addObserver(auditLog);
        String id = submitRequest("EMP-01", ApprovalRequestType.MANUAL_DISCOUNT, 250.0);
        engine.approve(id, "MGR-001");

        List<AuditLogObserver.AuditEntry> entries = auditLog.getAuditLog();
        assertEquals(2, entries.size());
        assertEquals("SUBMITTED", entries.get(0).eventType());
        assertEquals("APPROVED",  entries.get(1).eventType());
    }

    // ── Helper ────────────────────────────────────────────────────────────────────

    private String submitRequest(String employee, ApprovalRequestType type, double amount) {
        return engine.submitOverrideRequest(employee, type, "ORD-001", amount, "Test justification");
    }

    // ── Escalation time-based tests ───────────────────────────────────────────────

    @Test
    void escalateStaleRequests_pendingAfter48h_becomesEscalated() {
        SettableClock clock = new SettableClock();
        ApprovalWorkflowEngine clockedEngine = new ApprovalWorkflowEngine(STUB_STRATEGY, STUB_ROLE_SERVICE, clock);
        CapturingObserver clockedObserver = new CapturingObserver();
        clockedEngine.addObserver(clockedObserver);

        String id = clockedEngine.submitOverrideRequest(
            "EMP-01", ApprovalRequestType.MANUAL_DISCOUNT, "ORD-002", 100.0, "Test");

        clock.advance(Duration.ofHours(49));
        clockedEngine.escalateStaleRequests();

        assertEquals(ApprovalStatus.ESCALATED, clockedEngine.getStatus(id),
            "Request pending for >48h should be escalated.");
        assertTrue(clockedObserver.events.stream().anyMatch(e -> e.startsWith("ESCALATED:" + id)),
            "ESCALATED observer event should be fired.");
    }

    @Test
    void escalateStaleRequests_escalatedAfter48hFromEscalation_becomesAutoRejected() {
        SettableClock clock = new SettableClock();
        ApprovalWorkflowEngine clockedEngine = new ApprovalWorkflowEngine(STUB_STRATEGY, STUB_ROLE_SERVICE, clock);

        String id = clockedEngine.submitOverrideRequest(
            "EMP-01", ApprovalRequestType.MANUAL_DISCOUNT, "ORD-003", 100.0, "Test");

        // Advance 49h → triggers escalation
        clock.advance(Duration.ofHours(49));
        clockedEngine.escalateStaleRequests();
        assertEquals(ApprovalStatus.ESCALATED, clockedEngine.getStatus(id));

        // Advance another 49h from escalation → triggers auto-reject
        clock.advance(Duration.ofHours(49));
        clockedEngine.escalateStaleRequests();
        assertEquals(ApprovalStatus.REJECTED, clockedEngine.getStatus(id),
            "Request escalated for >48h should be auto-rejected.");
    }

    @Test
    void reject_blankReason_throws() {
        String id = submitRequest("EMP-01", ApprovalRequestType.MANUAL_DISCOUNT, 100.0);
        assertThrows(IllegalArgumentException.class, () -> engine.reject(id, "MGR-001", "  "),
            "Blank rejection reason should be rejected.");
        assertEquals(ApprovalStatus.PENDING, engine.getStatus(id),
            "Status should remain PENDING after a failed reject call.");
    }

    @Test
    void getPendingApprovals_onlyReturnsRequestsRoutedToThatApprover() {
        // All requests in this test are routed to MGR-001 by the stub strategy.
        String id1 = submitRequest("EMP-01", ApprovalRequestType.MANUAL_DISCOUNT, 100.0);
        String id2 = submitRequest("EMP-02", ApprovalRequestType.CONTRACT_BYPASS, 200.0);

        // Another approver should see no requests since none are routed to them.
        List<String> forOther = engine.getPendingApprovals("MGR-999");
        assertTrue(forOther.isEmpty(), "MGR-999 should have no pending requests.");

        List<String> forMgr001 = engine.getPendingApprovals("MGR-001");
        assertTrue(forMgr001.contains(id1));
        assertTrue(forMgr001.contains(id2));
    }

    /**
     * Controllable clock for deterministic time-based tests.
     * Not intended for production use.
     */
    private static class SettableClock extends Clock {
        private Instant now;
        private final ZoneId zone;

        SettableClock() {
            this(Instant.now(), ZoneId.systemDefault());
        }

        private SettableClock(Instant now, ZoneId zone) {
            this.now = now;
            this.zone = zone;
        }

        void advance(Duration d) { now = now.plus(d); }

        @Override public ZoneId getZone()          { return zone; }
        @Override public Clock withZone(ZoneId z)  { return new SettableClock(this.now, z); }
        @Override public Instant instant()         { return now; }
    }
}