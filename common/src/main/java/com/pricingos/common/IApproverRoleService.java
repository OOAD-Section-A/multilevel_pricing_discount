package com.pricingos.common;

/**
 * Integration boundary with the UI/Auth subsystem (Team T3N50R).
 *
 * <p>The Price Approval Workflow (Component 8) must verify that a manager is authorised
 * to approve a given category of override before recording their decision.
 * This interface decouples Component 8 from the concrete auth implementation — SOLID DIP.
 *
 * <p>T3N50R provides a concrete implementation; we depend only on this contract.
 */
public interface IApproverRoleService {

    /**
     * Returns true if the given employee is permitted to approve overrides
     * of the specified request type at the given monetary threshold.
     *
     * @param approverId  employee ID to check
     * @param requestType category of override being approved
     * @param amount      the discount amount in question
     * @return true if the approver has sufficient authority
     */
    boolean canApprove(String approverId, ApprovalRequestType requestType, double amount);

    /**
     * Returns the ID of the next manager in the escalation hierarchy above the given employee.
     * Returns null if the given employee is already at the top of the hierarchy.
     *
     * @param employeeId current approver whose escalation target we need
     * @return escalation manager ID, or null if none
     */
    String getEscalationManagerId(String employeeId);
}