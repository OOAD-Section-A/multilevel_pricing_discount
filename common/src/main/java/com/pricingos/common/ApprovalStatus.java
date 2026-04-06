package com.pricingos.common;

/**
 * Lifecycle states for a price override / approval request.
 * Matches the approval_status field in the Data Dictionary (Component 8).
 */
public enum ApprovalStatus {
    /** Override request has been submitted and is awaiting manager action. */
    PENDING,

    /** Manager has approved the requested discount/override. */
    APPROVED,

    /** Manager has explicitly rejected the override request. */
    REJECTED,

    /**
     * Request has been waiting beyond the SLA threshold (48 hours) and has been
     * forwarded to the secondary/regional manager — see APPROVAL_ESCALATION_TIMEOUT exception.
     */
    ESCALATED
}