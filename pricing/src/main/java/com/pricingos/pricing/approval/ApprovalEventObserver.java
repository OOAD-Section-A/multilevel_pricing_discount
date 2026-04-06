package com.pricingos.pricing.approval;

/**
 * Behavioural pattern (Observer): receives notifications when approval workflow
 * events occur — approval, rejection, escalation, or margin violation.
 *
 * <p>The {@link ApprovalWorkflowEngine} is the Subject; concrete implementations
 * (e.g., email notifier, Slack bot, audit logger, analytics recorder) register as
 * observers. This allows new notification channels to be added without modifying
 * the engine — SOLID OCP.
 *
 * <p>GRASP Low Coupling: the engine depends only on this interface, not on any
 * concrete notification implementation.
 *
 * <p>Backward compatibility note: {@link #onMarginViolationBlocked} has a {@code default}
 * no-op implementation so existing observers ({@link AuditLogObserver},
 * {@link ProfitabilityAnalyticsObserver}) continue to compile without changes. Override
 * only if your observer needs to react to margin violation alerts.
 */
public interface ApprovalEventObserver {

    /**
     * Called when a new override request is submitted.
     *
     * @param request     the newly created request
     * @param approverId  the manager ID the request was routed to
     */
    void onRequestSubmitted(ApprovalRequest request, String approverId);

    /**
     * Called when a manager approves an override request.
     *
     * @param request the approved request (status is now APPROVED)
     */
    void onRequestApproved(ApprovalRequest request);

    /**
     * Called when a manager rejects an override request.
     * Implementations should notify the cashier/initiator of the rejection reason.
     *
     * @param request the rejected request (status is now REJECTED)
     */
    void onRequestRejected(ApprovalRequest request);

    /**
     * Called when a request is escalated due to SLA timeout (APPROVAL_ESCALATION_TIMEOUT).
     * Implementations should notify the secondary/regional manager.
     *
     * @param request           the escalated request (status is now ESCALATED)
     * @param escalationTarget  employee ID of the secondary manager to notify
     */
    void onRequestEscalated(ApprovalRequest request, String escalationTarget);

    /**
     * Called when an approval attempt is blocked because the requested discount would
     * push the net order price below the configured floor price (Margin Protection feature).
     *
     * <p>Default implementation is a no-op — only observers that surface margin alerts
     * (e.g., a Pricing Admin dashboard notifier) need to override this.
     *
     * @param request         the request whose approval was blocked
     * @param floorPrice      the effective floor price threshold that would have been breached
     */
    default void onMarginViolationBlocked(ApprovalRequest request, double floorPrice) {
        // no-op by default — existing observers are not affected
    }
}