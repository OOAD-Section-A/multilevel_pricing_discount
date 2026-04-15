package com.pricingos.common;

public record PricingOverrideRequest(
    String requestedBy,
    ApprovalRequestType requestType,
    String orderId,
    double requestedDiscountAmount,
    String justification
) {
    public PricingOverrideRequest {
        requestedBy = ValidationUtils.requireNonBlank(requestedBy, "requestedBy");
        if (requestType == null) {
            throw new IllegalArgumentException("requestType cannot be null");
        }
        orderId = ValidationUtils.requireNonBlank(orderId, "orderId");
        ValidationUtils.requireFiniteNonNegative(requestedDiscountAmount, "requestedDiscountAmount");
        justification = ValidationUtils.requireNonBlank(justification, "justification");
    }
}
