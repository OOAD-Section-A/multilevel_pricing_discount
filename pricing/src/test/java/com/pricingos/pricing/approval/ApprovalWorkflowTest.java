package com.pricingos.pricing.approval;

import com.pricingos.common.ApprovalRequestType;
import com.pricingos.common.ApprovalStatus;
import com.pricingos.common.IApproverRoleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
}