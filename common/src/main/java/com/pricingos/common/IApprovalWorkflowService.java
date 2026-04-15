package com.pricingos.common;

import java.util.List;

public interface IApprovalWorkflowService {

    String submitOverrideRequest(String requestedBy, ApprovalRequestType requestType,
                                 String orderId, double requestedDiscountAmt,
                                 String justificationText);

    void approve(String approvalId, String approverId);

    void reject(String approvalId, String approverId, String reason);

    List<String> getPendingApprovals(String approverId);

    void escalateStaleRequests();

    ApprovalStatus getStatus(String approvalId);
}
