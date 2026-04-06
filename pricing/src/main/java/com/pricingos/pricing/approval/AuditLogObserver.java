package com.pricingos.pricing.approval;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Concrete implementation of {@link ApprovalEventObserver} that writes every
 * approval workflow event to an in-memory audit log.
 *
 * <p>Behavioural pattern (Observer): registered with {@link ApprovalWorkflowEngine}
 * to receive lifecycle events without the engine knowing about logging details.
 *
 * <p>This satisfies the audit_log_flag requirement from the Data Dictionary (Component 8)
 * and the "audit trail" output listed in the Component Table.
 *
 * <p>In production, the log entries would be persisted to a database table or
 * forwarded to a centralised logging service. The interface boundary means the
 * engine code does not change when the storage mechanism changes — SOLID OCP.
 */
public class AuditLogObserver implements ApprovalEventObserver {

    /** Immutable record of a single audit event. */
    public record AuditEntry(
        LocalDateTime timestamp,
        String approvalId,
        String eventType,
        String actor,
        String detail
    ) {}

    private final List<AuditEntry> log = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void onRequestSubmitted(ApprovalRequest request, String approverId) {
        log.add(new AuditEntry(
            LocalDateTime.now(),
            request.getApprovalId(),
            "SUBMITTED",
            request.getRequestedBy(),
            "Override request submitted. Type=" + request.getRequestType()
                + " Amount=" + request.getRequestedDiscountAmt()
                + " RoutedTo=" + approverId
                + " Justification=" + request.getJustificationText()
        ));
    }

    @Override
    public void onRequestApproved(ApprovalRequest request) {
        log.add(new AuditEntry(
            LocalDateTime.now(),
            request.getApprovalId(),
            "APPROVED",
            request.getApprovingManagerId(),
            "Override approved. OrderId=" + request.getOrderId()
                + " DiscountAmt=" + request.getRequestedDiscountAmt()
        ));
    }

    @Override
    public void onRequestRejected(ApprovalRequest request) {
        log.add(new AuditEntry(
            LocalDateTime.now(),
            request.getApprovalId(),
            "REJECTED",
            request.getApprovingManagerId() != null ? request.getApprovingManagerId() : "SYSTEM",
            "Override rejected. OrderId=" + request.getOrderId()
                + " Reason=" + request.getRejectionReason()
        ));
    }

    @Override
    public void onRequestEscalated(ApprovalRequest request, String escalationTarget) {
        log.add(new AuditEntry(
            LocalDateTime.now(),
            request.getApprovalId(),
            "ESCALATED",
            "SYSTEM",
            "Request escalated after SLA breach. EscalatedTo=" + escalationTarget
                + " OrderId=" + request.getOrderId()
        ));
    }

    /**
     * Returns an unmodifiable snapshot of the current audit log.
     * Used by the Pricing Admin dashboard to display the audit trail.
     */
    public List<AuditEntry> getAuditLog() {
        synchronized (log) {
            return Collections.unmodifiableList(new ArrayList<>(log));
        }
    }
}