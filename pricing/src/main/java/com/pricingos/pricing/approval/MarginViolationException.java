package com.pricingos.pricing.approval;

/**
 * Domain exception thrown by the Price Approval Workflow when a requested discount
 * would push the net price below the floor price configured in Component 3 (Base Price Config).
 *
 * <p>Component 8 — Price Approval Workflow: Margin Protection feature.
 * Supports: "Preventing the system from accepting orders where the discount would
 * result in a negative profit margin."
 *
 * <p>The {@link ApprovalWorkflowEngine} throws this from {@code approve()} when
 * {@link com.pricingos.common.IFloorPriceService#wouldViolateMargin} returns true.
 * The caller (Discount Rules Engine — Component 4) is responsible for surfacing the
 * violation alert to the pricing admin UI and reverting to standard pricing.
 */
public class MarginViolationException extends RuntimeException {

    private final String orderId;
    private final double effectiveFloorPrice;
    private final double requestedDiscountAmount;

    /**
     * Constructs a MarginViolationException.
     *
     * @param orderId                 the order whose margin floor would be breached
     * @param effectiveFloorPrice     minimum permissible net price for the order
     * @param requestedDiscountAmount the discount amount that caused the violation
     */
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

    // ── Getters ───────────────────────────────────────────────────────────────────

    /** The order ID whose margin would have been violated. */
    public String getOrderId() { return orderId; }

    /** The configured floor price threshold for the order. */
    public double getEffectiveFloorPrice() { return effectiveFloorPrice; }

    /** The discount amount that triggered this violation. */
    public double getRequestedDiscountAmount() { return requestedDiscountAmount; }
}
