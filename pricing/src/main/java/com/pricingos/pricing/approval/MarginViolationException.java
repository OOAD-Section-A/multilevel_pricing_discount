package com.pricingos.pricing.approval;

public class MarginViolationException extends RuntimeException {

    private final String orderId;
    private final double effectiveFloorPrice;
    private final double requestedDiscountAmount;

    public MarginViolationException(String orderId, double effectiveFloorPrice,
                                    double requestedDiscountAmount) {
        super(String.format(
            "Margin violation: discount of %.2f on order '%s' would breach the floor price of %.2f. " +
            "Approval blocked to protect profit margin.",
            requestedDiscountAmount, orderId, effectiveFloorPrice));
        this.orderId                  = orderId;
        this.effectiveFloorPrice      = effectiveFloorPrice;
        this.requestedDiscountAmount  = requestedDiscountAmount;
    }

    public String getOrderId() { return orderId; }

    public double getEffectiveFloorPrice() { return effectiveFloorPrice; }

    public double getRequestedDiscountAmount() { return requestedDiscountAmount; }
}
