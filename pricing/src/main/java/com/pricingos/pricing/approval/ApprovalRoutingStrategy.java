package com.pricingos.pricing.approval;

/**
 * Behavioural pattern (Strategy): encapsulates approval routing rules for different
 * request categories and discount thresholds.
 *
 * <p>The {@link ApprovalWorkflowEngine} delegates "who should approve this request?"
 * to an implementation of this interface rather than hard-coding branching logic.
 * New routing rules (e.g., C-suite approval for very large discounts) can be added
 * by providing a new strategy without modifying the engine — SOLID OCP.
 *
 * <p>This also satisfies the GRASP Pure Fabrication principle: the strategy object
 * exists purely to hold routing logic, not to represent a real-world entity.
 */
public interface ApprovalRoutingStrategy {

    /**
     * Determines the primary approver manager ID for the given override request.
     *
     * @param request the pending approval request
     * @return the employee ID of the manager who should receive this request
     */
    String resolveApproverId(ApprovalRequest request);

    /**
     * Returns true if this request requires a second-level (senior) approval
     * in addition to the primary approver — e.g., large discount amounts.
     *
     * @param request the pending approval request
     * @return true if dual approval is required
     */
    boolean requiresDualApproval(ApprovalRequest request);
}