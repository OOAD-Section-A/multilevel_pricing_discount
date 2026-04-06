package com.pricingos.common;

/**
 * Margin Floor Price protection use-cases exposed to Component 8.
 *
 * <p>Component 8 — Price Approval Workflow, Margin Protection feature.
 * Supports the spec requirement: "Provides real-time visibility into the floor price,
 * preventing the system from accepting orders where the discount would result in a
 * negative profit margin."
 *
 * <p>The concrete implementation is provided by the Base Price Config team (Component 3),
 * which outputs "base unit price, price floor/ceiling, margin ratios". Component 8
 * depends only on this interface — SOLID DIP / GRASP Indirection.
 *
 * <p>The margin check is keyed on {@code orderId} rather than individual SKU IDs so that
 * Component 8 does not need to understand the internal structure of an order. The
 * implementing service resolves the relevant SKUs and their floor prices internally.
 */
public interface IFloorPriceService {

    /**
     * Returns {@code true} if applying the requested discount to the specified order
     * would push any line-item's net price below its configured floor price, resulting
     * in a negative or unacceptable profit margin.
     *
     * @param orderId        the order whose line items will be checked
     * @param discountAmount the total monetary discount amount being requested
     * @return true if the discount violates the margin floor for any line item
     */
    boolean wouldViolateMargin(String orderId, double discountAmount);

    /**
     * Returns the effective floor price (minimum permissible net price) for the given order.
     * This is the value surfaced in the escalation alert and audit trail.
     *
     * @param orderId the order to evaluate
     * @return the minimum net order value; approvals that breach this must be blocked
     */
    double getEffectiveFloorPrice(String orderId);
}
