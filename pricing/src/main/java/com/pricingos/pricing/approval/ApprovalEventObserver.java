package com.pricingos.pricing.approval;

/**
 * Behavioural pattern (Observer): receives notifications when approval workflow
 * events occur — approval, rejection, or escalation.
 *
 * <p>The {@link ApprovalWorkflowEngine} is the Subject; concrete implementations
 * (e.g., email notifier, Slack bot, audit logger) register as observers. This allows
 * new notification channels to be added without modifying the engine — SOLID OCP.
 *
 * <p>GRASP Low Coupling: the engine depends only on this interface, not on any
 * concrete notification implementation.
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
}