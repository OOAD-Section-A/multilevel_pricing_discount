package com.pricingos.pricing.approval;

import com.pricingos.common.ApprovalRequestType;
import com.pricingos.common.ApprovalStatus;
import com.pricingos.common.IApprovalWorkflowService;
import com.pricingos.common.IApproverRoleService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Component 8 — Price Approval & Workflow Engine.
 *
 * <p>Responsibilities (from the Component Table):
 * <ul>
 *   <li>Routes discount/override requests through an approval hierarchy with escalations.</li>
 *   <li>Records APPROVED / REJECTED / ESCALATED decisions with a full audit trail.</li>
 *   <li>Auto-escalates requests that have been PENDING beyond the 48-hour SLA.</li>
 *   <li>Auto-rejects requests that remain ESCALATED beyond a second SLA window.</li>
 * </ul>
 *
 * <p>Design patterns applied:
 * <ul>
 *   <li><b>Behavioural (Strategy)</b>: {@link ApprovalRoutingStrategy} determines which manager
 *       receives each request, making routing rules swappable without changing this class.</li>
 *   <li><b>Behavioural (Observer)</b>: {@link ApprovalEventObserver} implementations receive
 *       lifecycle events (submitted, approved, rejected, escalated) — e.g., email notifier,
 *       audit logger — without the engine knowing their details.</li>
 *   <li><b>GRASP Controller</b>: this class is the single entry point for all approval
 *       workflow interactions; it coordinates domain objects but does not do the work itself.</li>
 *   <li><b>SOLID SRP</b>: only approval workflow logic lives here.</li>
 *   <li><b>SOLID DIP</b>: depends on {@link IApproverRoleService} (T3N50R) and
 *       {@link ApprovalRoutingStrategy} abstractions, not concrete classes.</li>
 *   <li><b>SOLID OCP</b>: new routing rules or notification channels extend the system
 *       without modifying this class.</li>
 * </ul>
 *
 * <p>Exception handling:
 * <ul>
 *   <li>APPROVAL_ESCALATION_TIMEOUT (MINOR): handled in {@link #escalateStaleRequests()}.
 *       Requests PENDING > 48 hours are escalated; if still unresolved after a further
 *       48 hours they are auto-rejected and standard pricing is restored.</li>
 *   <li>OVERRIDE_REQUEST_REJECTED (MINOR): handled in {@link #reject}; callers must
 *       revert pricing to the last valid standard price.</li>
 * </ul>
 */
public class ApprovalWorkflowEngine implements IApprovalWorkflowService {

    /** SLA before an unanswered PENDING request is escalated (hours). */
    private static final long ESCALATION_THRESHOLD_HOURS = 48L;

    /** SLA before an ESCALATED request is auto-rejected (hours after escalation). */
    private static final long AUTO_REJECT_THRESHOLD_HOURS = 48L;

    /** All requests, keyed by approval_id. Thread-safe map for concurrent access. */
    private final Map<String, ApprovalRequest> requestStore = new ConcurrentHashMap<>();

    /**
     * Routing strategy (Behavioural — Strategy pattern).
     * Determines which manager receives each request.
     */
    private final ApprovalRoutingStrategy routingStrategy;

    /**
     * Integration with T3N50R for permission checks and hierarchy lookups.
     * SOLID DIP: depends on the interface, not the concrete auth implementation.
     */
    private final IApproverRoleService approverRoleService;

    /**
     * Registered observers (Behavioural — Observer pattern).
     * CopyOnWriteArrayList ensures thread-safe iteration during notification.
     */
    private final List<ApprovalEventObserver> observers = new CopyOnWriteArrayList<>();

    private final AtomicInteger idCounter = new AtomicInteger();

    public ApprovalWorkflowEngine(ApprovalRoutingStrategy routingStrategy,
                                  IApproverRoleService approverRoleService) {
        this.routingStrategy    = Objects.requireNonNull(routingStrategy, "routingStrategy cannot be null");
        this.approverRoleService = Objects.requireNonNull(approverRoleService, "approverRoleService cannot be null");
    }

    // ── Observer registration ─────────────────────────────────────────────────────

    /**
     * Registers an observer to receive approval lifecycle events.
     * Observers are notified in registration order.
     */
    public void addObserver(ApprovalEventObserver observer) {
        observers.add(Objects.requireNonNull(observer, "observer cannot be null"));
    }

    /** Removes a previously registered observer. */
    public void removeObserver(ApprovalEventObserver observer) {
        observers.remove(observer);
    }

    // ── IApprovalWorkflowService implementation ───────────────────────────────────

    /**
     * {@inheritDoc}
     *
     * <p>Uses the Strategy to resolve the target approver, then notifies all
     * observers that a new request has been submitted.
     */
    @Override
    public String submitOverrideRequest(String requestedBy, ApprovalRequestType requestType,
                                        String orderId, double requestedDiscountAmt,
                                        String justificationText) {
        String approvalId = "APR-" + idCounter.incrementAndGet();

        ApprovalRequest request = new ApprovalRequest(
            approvalId, requestType, requestedBy, orderId,
            requestedDiscountAmt, justificationText
        );

        // Strategy resolves the primary approver from T3N50R hierarchy.
        String targetApproverId = routingStrategy.resolveApproverId(request);

        requestStore.put(approvalId, request);

        // Observer: notify all listeners (audit logger, email notifier, etc.).
        notifySubmitted(request, targetApproverId);

        return approvalId;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Validates that the approver has authority for this request type and amount
     * (via T3N50R's {@link IApproverRoleService}), then records the decision.
     */
    @Override
    public void approve(String approvalId, String approverId) {
        ApprovalRequest request = getRequest(approvalId);

        // Verify authority via T3N50R boundary — SOLID DIP.
        if (!approverRoleService.canApprove(approverId, request.getRequestType(),
                                            request.getRequestedDiscountAmt())) {
            throw new IllegalArgumentException(
                "Approver [" + approverId + "] does not have authority to approve this request.");
        }

        request.markAsApproved(approverId);

        // Observer: notify audit logger, notify requester, etc.
        notifyApproved(request);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Handles OVERRIDE_REQUEST_REJECTED (MINOR): records the decision and logs it
     * to the audit trail. The caller is responsible for reverting to standard pricing.
     */
    @Override
    public void reject(String approvalId, String approverId, String reason) {
        ApprovalRequest request = getRequest(approvalId);

        if (!approverRoleService.canApprove(approverId, request.getRequestType(),
                                            request.getRequestedDiscountAmt())) {
            throw new IllegalArgumentException(
                "Approver [" + approverId + "] does not have authority to reject this request.");
        }

        request.markAsRejected(approverId, reason);

        // Observer: notifies the cashier/initiator of the rejection reason via UI (Component 8).
        notifyRejected(request);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Returns all request IDs (not objects) currently routed to this approver
     * that remain in PENDING status. The approver dashboard fetches full details
     * separately using getStatus / the request store.
     */
    @Override
    public List<String> getPendingApprovals(String approverId) {
        requireNonBlank(approverId, "approverId");
        // In a real system with a DB, the query would filter by routed approverId.
        // In this in-memory store we return all PENDING requests for the approver.
        return requestStore.values().stream()
            .filter(r -> r.getStatus() == ApprovalStatus.PENDING || r.getStatus() == ApprovalStatus.ESCALATED)
            .map(ApprovalRequest::getApprovalId)
            .sorted()
            .collect(Collectors.toList());
    }

    /**
     * {@inheritDoc}
     *
     * <p>Handles APPROVAL_ESCALATION_TIMEOUT (MINOR):
     * <ol>
     *   <li>PENDING requests older than 48 hours → mark ESCALATED, notify secondary manager.</li>
     *   <li>ESCALATED requests older than 48 hours (from escalation) → auto-reject,
     *       revert transaction to standard pricing.</li>
     * </ol>
     * This method is designed to be called by a scheduled job (e.g., every hour).
     */
    @Override
    public void escalateStaleRequests() {
        List<ApprovalRequest> toEscalate = new ArrayList<>();
        List<ApprovalRequest> toAutoReject = new ArrayList<>();

        for (ApprovalRequest request : requestStore.values()) {
            ApprovalStatus status = request.getStatus();
            long pendingHours = request.getPendingHours();

            if (status == ApprovalStatus.PENDING && pendingHours >= ESCALATION_THRESHOLD_HOURS) {
                toEscalate.add(request);
            } else if (status == ApprovalStatus.ESCALATED && pendingHours >= AUTO_REJECT_THRESHOLD_HOURS) {
                toAutoReject.add(request);
            }
        }

        // Escalate: mark ESCALATED and notify secondary/regional manager.
        for (ApprovalRequest request : toEscalate) {
            request.markAsEscalated();
            // Fetch the next manager in the T3N50R hierarchy above the primary approver.
            String primaryApprover = routingStrategy.resolveApproverId(request);
            String escalationTarget = approverRoleService.getEscalationManagerId(primaryApprover);
            notifyEscalated(request, escalationTarget != null ? escalationTarget : "REGIONAL_MANAGER");
        }

        // Auto-reject: no one acted within the second SLA window.
        for (ApprovalRequest request : toAutoReject) {
            request.markAsRejected("SYSTEM", "Auto-rejected: no manager action within SLA window. "
                + "Transaction reverted to standard pricing.");
            notifyRejected(request);
        }
    }

    /** {@inheritDoc} */
    @Override
    public ApprovalStatus getStatus(String approvalId) {
        return getRequest(approvalId).getStatus();
    }

    // ── Package-private helpers for testing ──────────────────────────────────────

    /** Returns the full ApprovalRequest object for a given ID (package-private for tests). */
    ApprovalRequest getRequestById(String approvalId) {
        return getRequest(approvalId);
    }

    // ── Observer notification helpers ─────────────────────────────────────────────

    private void notifySubmitted(ApprovalRequest request, String approverId) {
        for (ApprovalEventObserver observer : observers) {
            observer.onRequestSubmitted(request, approverId);
        }
    }

    private void notifyApproved(ApprovalRequest request) {
        for (ApprovalEventObserver observer : observers) {
            observer.onRequestApproved(request);
        }
    }

    private void notifyRejected(ApprovalRequest request) {
        for (ApprovalEventObserver observer : observers) {
            observer.onRequestRejected(request);
        }
    }

    private void notifyEscalated(ApprovalRequest request, String escalationTarget) {
        for (ApprovalEventObserver observer : observers) {
            observer.onRequestEscalated(request, escalationTarget);
        }
    }

    // ── Private utilities ─────────────────────────────────────────────────────────

    private ApprovalRequest getRequest(String approvalId) {
        String normalizedId = requireNonBlank(approvalId, "approvalId");
        ApprovalRequest request = requestStore.get(normalizedId);
        if (request == null) {
            throw new IllegalArgumentException("No approval request found with ID: " + normalizedId);
        }
        return request;
    }

    private static String requireNonBlank(String v, String field) {
        Objects.requireNonNull(v, field + " cannot be null");
        if (v.trim().isEmpty()) throw new IllegalArgumentException(field + " cannot be blank");
        return v.trim();
    }
}