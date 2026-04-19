package com.pricingos.pricing.approval;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import com.pricingos.pricing.db.DaoBulk.AuditLogDao;
import java.util.List;

public class AuditLogObserver implements ApprovalEventObserver {

    public record AuditEntry(
        LocalDateTime timestamp,
        String approvalId,
        String eventType,
        String actor,
        String detail
    ) {}

    @Override
    public void onRequestSubmitted(ApprovalRequest request, String approverId) {
        AuditLogDao.save(new AuditEntry(
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
        AuditLogDao.save(new AuditEntry(
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
        AuditLogDao.save(new AuditEntry(
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
        AuditLogDao.save(new AuditEntry(
            LocalDateTime.now(),
            request.getApprovalId(),
            "ESCALATED",
            "SYSTEM",
            "Request escalated after SLA breach. EscalatedTo=" + escalationTarget
                + " OrderId=" + request.getOrderId()
        ));
    }

    public List<AuditEntry> getAuditLog() {
            return AuditLogDao.findAll();
    }
}
