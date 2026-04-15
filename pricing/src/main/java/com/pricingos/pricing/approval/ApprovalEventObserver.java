package com.pricingos.pricing.approval;

public interface ApprovalEventObserver {

    void onRequestSubmitted(ApprovalRequest request, String approverId);

    void onRequestApproved(ApprovalRequest request);

    void onRequestRejected(ApprovalRequest request);

    void onRequestEscalated(ApprovalRequest request, String escalationTarget);

    default void onMarginViolationBlocked(ApprovalRequest request, double floorPrice) {

    }
}
