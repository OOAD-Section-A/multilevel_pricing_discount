package com.pricingos.common;

import java.util.List;

/**
 * Price Approval & Workflow Engine use-cases exposed to the rest of the subsystem.
 *
 * <p>Component 8 in the Data Dictionary and Component Table.
 * The Discount Rules Engine (Component 4) routes override requests through this interface.
 */
public interface IApprovalWorkflowService {

    /**
     * Creates a new price override / approval request and returns the approval_id.
     *
     * @param requestedBy        employee ID of the cashier or sales rep (requested_by)
     * @param requestType        category of override (MANUAL_DISCOUNT, CONTRACT_BYPASS, POLICY_EXCEPTION)
     * @param orderId            order this override request relates to
     * @param requestedDiscountAmt the discount amount or percentage being requested
     * @param justificationText  free-text reason provided by the requester
     * @return generated approval_id
     */
    String submitOverrideRequest(String requestedBy, ApprovalRequestType requestType,
                                 String orderId, double requestedDiscountAmt,
                                 String justificationText);

    /**
     * Records an approval decision by the manager.
     *
     * @param approvalId   the request to approve
     * @param approverId   manager ID making the decision (approving_manager_id)
     */
    void approve(String approvalId, String approverId);

    /**
     * Records a rejection decision by the manager; reverts pricing to standard.
     *
     * @param approvalId   the request to reject
     * @param approverId   manager ID making the decision
     * @param reason       rejection reason logged in the audit trail
     */
    void reject(String approvalId, String approverId, String reason);

    /**
     * Returns all requests currently in PENDING status; intended for the approver dashboard.
     */
    List<String> getPendingApprovals(String approverId);

    /**
     * Scans all PENDING requests and escalates any that have been waiting longer than
     * the configured SLA (default 48 hours). Handles APPROVAL_ESCALATION_TIMEOUT exception.
     */
    void escalateStaleRequests();

    /**
     * Returns the current ApprovalStatus for a given request.
     */
    ApprovalStatus getStatus(String approvalId);
}