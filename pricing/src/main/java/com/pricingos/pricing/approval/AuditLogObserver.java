package com.pricingos.pricing.approval;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;
import java.util.logging.Level;

public class AuditLogObserver implements ApprovalEventObserver {
    
    private static final Logger LOGGER = Logger.getLogger(AuditLogObserver.class.getName());

    public record AuditEntry(
        LocalDateTime timestamp,
        String approvalId,
        String eventType,
        String actor,
        String detail
    ) {}

    @Override
    public void onRequestSubmitted(ApprovalRequest request, String approverId) {
        AuditEntry entry = new AuditEntry(
            LocalDateTime.now(),
            request.getApprovalId(),
            "SUBMITTED",
            request.getRequestedBy(),
            "Override request submitted. Type=" + request.getRequestType()
                + " Amount=" + request.getRequestedDiscountAmt()
                + " RoutedTo=" + approverId
                + " Justification=" + request.getJustificationText()
        );
        LOGGER.log(Level.INFO, "[AUDIT] " + entry);
    }

    @Override
    public void onRequestApproved(ApprovalRequest request) {
        AuditEntry entry = new AuditEntry(
            LocalDateTime.now(),
            request.getApprovalId(),
            "APPROVED",
            request.getApprovingManagerId(),
            "Override approved. OrderId=" + request.getOrderId()
                + " DiscountAmt=" + request.getRequestedDiscountAmt()
        );
        LOGGER.log(Level.INFO, "[AUDIT] " + entry);
    }

    @Override
    public void onRequestRejected(ApprovalRequest request) {
        AuditEntry entry = new AuditEntry(
            LocalDateTime.now(),
            request.getApprovalId(),
            "REJECTED",
            request.getApprovingManagerId() != null ? request.getApprovingManagerId() : "SYSTEM",
            "Override rejected. OrderId=" + request.getOrderId()
                + " Reason=" + request.getRejectionReason()
        );
        LOGGER.log(Level.INFO, "[AUDIT] " + entry);
    }

    @Override
    public void onRequestEscalated(ApprovalRequest request, String escalationTarget) {
        AuditEntry entry = new AuditEntry(
            LocalDateTime.now(),
            request.getApprovalId(),
            "ESCALATED",
            "SYSTEM",
            "Request escalated after SLA breach. EscalatedTo=" + escalationTarget
                + " OrderId=" + request.getOrderId()
        );
        LOGGER.log(Level.INFO, "[AUDIT] " + entry);
    }

    /**
     * Returns empty list - audit logs are now logged to application logger only.
     * For persistence, database team needs to provide audit storage support.
     */
    public List<AuditEntry> getAuditLog() {
        return Collections.emptyList();
    }
}
