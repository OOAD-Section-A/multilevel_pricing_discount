package com.pricingos.pricing.approval;

import com.pricingos.common.ApprovalRequestType;
import com.pricingos.common.ApprovalStatus;
import com.pricingos.common.ValidationUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Domain object representing a single price override / approval request.
 *
 * <p>Maps directly to the Component 8 fields in the Data Dictionary:
 * approval_id, request_type, requested_by, requested_discount_amt,
 * justification_text, approving_manager_id, approval_status, approval_timestamp, audit_log_flag.
 *
 * <p>GRASP Information Expert: this class owns the state-transition logic for
 * approval decisions (approve, reject, escalate) rather than spreading it across
 * the engine or service layer.
 */
public class ApprovalRequest {

    private final String approvalId;
    private final ApprovalRequestType requestType;
    private final String requestedBy;          // employee / cashier ID
    private final String orderId;
    private final double requestedDiscountAmt;
    private final String justificationText;
    private final LocalDateTime submissionTime;

    /** Clock used for all time computations — injected to allow deterministic testing. */
    private final Clock clock;

    // Mutable fields updated as the request moves through the workflow.
    private ApprovalStatus status;
    private String approvingManagerId;          // set when manager acts
    private String routedToApproverId;          // set on submit, updated on escalation
    private LocalDateTime approvalTimestamp;    // set when decision is made
    private LocalDateTime escalationTime;       // set when the request is escalated
    private boolean auditLogFlag;               // true once audit entry is written
    private String rejectionReason;            // populated on REJECTED status

    ApprovalRequest(String approvalId, ApprovalRequestType requestType,
                    String requestedBy, String orderId,
                    double requestedDiscountAmt, String justificationText,
                    Clock clock) {
        this.approvalId           = ValidationUtils.requireNonBlank(approvalId, "approvalId");
        this.requestType          = Objects.requireNonNull(requestType, "requestType cannot be null");
        this.requestedBy          = ValidationUtils.requireNonBlank(requestedBy, "requestedBy");
        this.orderId              = ValidationUtils.requireNonBlank(orderId, "orderId");
        this.requestedDiscountAmt = requireFiniteNonNeg(requestedDiscountAmt, "requestedDiscountAmt");
        this.justificationText    = Objects.requireNonNull(justificationText, "justificationText cannot be null");
        this.clock                = Objects.requireNonNull(clock, "clock cannot be null");
        this.submissionTime       = LocalDateTime.now(clock);
        this.status               = ApprovalStatus.PENDING;
        this.auditLogFlag         = false;
    }

    // ── State transitions ─────────────────────────────────────────────────────────

    /**
     * Marks the request as APPROVED and records the approver ID and timestamp.
     * Sets audit_log_flag = true to trigger audit entry generation.
     */
    synchronized void markAsApproved(String approverId) {
        requirePendingOrEscalated("approve");
        this.approvingManagerId = ValidationUtils.requireNonBlank(approverId, "approverId");
        this.status             = ApprovalStatus.APPROVED;
        this.approvalTimestamp  = LocalDateTime.now(clock);
        this.auditLogFlag       = true;
    }

    /**
     * Marks the request as REJECTED. Handles the OVERRIDE_REQUEST_REJECTED exception
     * from the Exception Table (MINOR): the caller is responsible for reverting the
     * transaction to the last valid standard price.
     *
     * @param approverId manager or system actor performing the rejection
     * @param reason     non-blank reason for rejection; recorded in the audit trail
     */
    synchronized void markAsRejected(String approverId, String reason) {
        requirePendingOrEscalated("reject");
        this.approvingManagerId = ValidationUtils.requireNonBlank(approverId, "approverId");
        this.rejectionReason    = ValidationUtils.requireNonBlank(reason, "reason");
        this.status             = ApprovalStatus.REJECTED;
        this.approvalTimestamp  = LocalDateTime.now(clock);
        this.auditLogFlag       = true;
    }

    /**
     * Escalates the request to the secondary/regional manager.
     * Handles APPROVAL_ESCALATION_TIMEOUT (MINOR): triggered when the request has been
     * PENDING for more than 48 hours. Records the escalation timestamp for the second
     * SLA window (auto-reject after a further 48 hours of inactivity).
     */
    synchronized void markAsEscalated() {
        if (status != ApprovalStatus.PENDING) {
            throw new IllegalStateException("Only PENDING requests can be escalated, current status: " + status);
        }
        this.status = ApprovalStatus.ESCALATED;
        this.escalationTime = LocalDateTime.now(clock);
        // auditLogFlag is set true so the escalation event is captured.
        this.auditLogFlag = true;
    }

    /** Returns how many hours this request has been waiting in PENDING state (from submission). */
    synchronized long getPendingHours() {
        if (status != ApprovalStatus.PENDING) return 0L;
        return Duration.between(submissionTime, LocalDateTime.now(clock)).toHours();
    }

    /**
     * Returns how many hours have elapsed since this request was escalated.
     * Used to determine whether the second SLA window (auto-reject) has been breached.
     * Returns 0 if the request has not been escalated.
     */
    synchronized long getEscalatedHours() {
        if (status != ApprovalStatus.ESCALATED || escalationTime == null) return 0L;
        return Duration.between(escalationTime, LocalDateTime.now(clock)).toHours();
    }

    // ── Getters ───────────────────────────────────────────────────────────────────

    public String getApprovalId()            { return approvalId; }
    public ApprovalRequestType getRequestType() { return requestType; }
    public String getRequestedBy()           { return requestedBy; }
    public String getOrderId()               { return orderId; }
    public double getRequestedDiscountAmt()  { return requestedDiscountAmt; }
    public String getJustificationText()     { return justificationText; }
    public LocalDateTime getSubmissionTime() { return submissionTime; }
    public synchronized ApprovalStatus getStatus()    { return status; }
    public synchronized String getApprovingManagerId(){ return approvingManagerId; }
    public synchronized String getRoutedToApproverId(){ return routedToApproverId; }
    synchronized void setRoutedToApproverId(String id){ this.routedToApproverId = id; }
    public synchronized LocalDateTime getApprovalTimestamp() { return approvalTimestamp; }
    public synchronized LocalDateTime getEscalationTime()    { return escalationTime; }
    public synchronized boolean isAuditLogFlag()      { return auditLogFlag; }
    public synchronized String getRejectionReason()   { return rejectionReason; }

    // ── Private helpers ───────────────────────────────────────────────────────────

    private void requirePendingOrEscalated(String action) {
        if (status != ApprovalStatus.PENDING && status != ApprovalStatus.ESCALATED) {
            throw new IllegalStateException(
                "Cannot " + action + " a request with status: " + status);
        }
    }

    private static double requireFiniteNonNeg(double v, String field) {
        if (!Double.isFinite(v) || v < 0)
            throw new IllegalArgumentException(field + " must be a non-negative finite number");
        return v;
    }
}
