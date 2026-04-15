package com.pricingos.pricing.approval;

public interface ApprovalRoutingStrategy {

    String resolveApproverId(ApprovalRequest request);

    boolean requiresDualApproval(ApprovalRequest request);
}
