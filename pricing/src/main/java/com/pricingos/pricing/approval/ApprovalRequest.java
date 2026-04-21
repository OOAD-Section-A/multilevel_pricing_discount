package com.pricingos.pricing.approval;

import com.pricingos.common.ApprovalRequestType;
import com.pricingos.common.ApprovalStatus;
import com.pricingos.common.ValidationUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

public class ApprovalRequest {

    private final String approvalId;
    private final ApprovalRequestType requestType;
    private final String requestedBy;
    private final String orderId;
    private final double requestedDiscountAmt;
    private final String justificationText;
    private final LocalDateTime submissionTime;

    private final Clock clock;

    private ApprovalStatus status;
    private String approvingManagerId;
    private String routedToApproverId;
    private LocalDateTime approvalTimestamp;
    private LocalDateTime escalationTime;
    private boolean auditLogFlag;
    private String rejectionReason;

    ApprovalRequest(String approvalId, ApprovalRequestType requestType,
                    String requestedBy, String orderId,
                    double requestedDiscountAmt, String justificationText,
                    Clock clock) {
        this(approvalId,
                requestType,
                requestedBy,
                orderId,
                requestedDiscountAmt,
                justificationText,
                clock,
                LocalDateTime.now(Objects.requireNonNull(clock, "clock cannot be null")),
                ApprovalStatus.PENDING,
                null,
                null,
                null,
                null,
                false,
                null);
    }

    static ApprovalRequest rehydrate(String approvalId,
                                     ApprovalRequestType requestType,
                                     String requestedBy,
                                     String orderId,
                                     double requestedDiscountAmt,
                                     String justificationText,
                                     Clock clock,
                                     LocalDateTime submissionTime,
                                     ApprovalStatus status,
                                     String routedToApproverId,
                                     String approvingManagerId,
                                     LocalDateTime approvalTimestamp,
                                     LocalDateTime escalationTime,
                                     boolean auditLogFlag,
                                     String rejectionReason) {
        return new ApprovalRequest(
                approvalId,
                requestType,
                requestedBy,
                orderId,
                requestedDiscountAmt,
                justificationText,
                clock,
                submissionTime,
                status,
                routedToApproverId,
                approvingManagerId,
                approvalTimestamp,
                escalationTime,
                auditLogFlag,
                rejectionReason);
    }

    private ApprovalRequest(String approvalId,
                            ApprovalRequestType requestType,
                            String requestedBy,
                            String orderId,
                            double requestedDiscountAmt,
                            String justificationText,
                            Clock clock,
                            LocalDateTime submissionTime,
                            ApprovalStatus status,
                            String routedToApproverId,
                            String approvingManagerId,
                            LocalDateTime approvalTimestamp,
                            LocalDateTime escalationTime,
                            boolean auditLogFlag,
                            String rejectionReason) {
        this.approvalId           = ValidationUtils.requireNonBlank(approvalId, "approvalId");
        this.requestType          = Objects.requireNonNull(requestType, "requestType cannot be null");
        this.requestedBy          = ValidationUtils.requireNonBlank(requestedBy, "requestedBy");
        this.orderId              = ValidationUtils.requireNonBlank(orderId, "orderId");
        this.requestedDiscountAmt = requireFiniteNonNeg(requestedDiscountAmt, "requestedDiscountAmt");
        this.justificationText    = Objects.requireNonNull(justificationText, "justificationText cannot be null");
        this.clock                = Objects.requireNonNull(clock, "clock cannot be null");
        this.submissionTime       = Objects.requireNonNull(submissionTime, "submissionTime cannot be null");
        this.status               = Objects.requireNonNull(status, "status cannot be null");
        this.routedToApproverId   = routedToApproverId;
        this.approvingManagerId   = approvingManagerId;
        this.approvalTimestamp    = approvalTimestamp;
        this.escalationTime       = escalationTime;
        this.auditLogFlag         = auditLogFlag;
        this.rejectionReason      = rejectionReason;
    }

    synchronized void markAsApproved(String approverId) {
        requirePendingOrEscalated("approve");
        this.approvingManagerId = ValidationUtils.requireNonBlank(approverId, "approverId");
        this.status             = ApprovalStatus.APPROVED;
        this.approvalTimestamp  = LocalDateTime.now(clock);
        this.auditLogFlag       = true;
    }

    synchronized void markAsRejected(String approverId, String reason) {
        requirePendingOrEscalated("reject");
        this.approvingManagerId = ValidationUtils.requireNonBlank(approverId, "approverId");
        this.rejectionReason    = ValidationUtils.requireNonBlank(reason, "reason");
        this.status             = ApprovalStatus.REJECTED;
        this.approvalTimestamp  = LocalDateTime.now(clock);
        this.auditLogFlag       = true;
    }

    synchronized void markAsEscalated() {
        if (status != ApprovalStatus.PENDING) {
            throw new IllegalStateException("Only PENDING requests can be escalated, current status: " + status);
        }
        this.status = ApprovalStatus.ESCALATED;
        this.escalationTime = LocalDateTime.now(clock);

        this.auditLogFlag = true;
    }

    synchronized long getPendingHours() {
        if (status != ApprovalStatus.PENDING) return 0L;
        return Duration.between(submissionTime, LocalDateTime.now(clock)).toHours();
    }

    synchronized long getEscalatedHours() {
        if (status != ApprovalStatus.ESCALATED || escalationTime == null) return 0L;
        return Duration.between(escalationTime, LocalDateTime.now(clock)).toHours();
    }

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
