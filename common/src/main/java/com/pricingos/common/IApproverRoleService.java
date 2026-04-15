package com.pricingos.common;

public interface IApproverRoleService {

    boolean canApprove(String approverId, ApprovalRequestType requestType, double amount);

    String getEscalationManagerId(String employeeId);
}
