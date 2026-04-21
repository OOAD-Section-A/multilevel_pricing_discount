package com.pricingos.common;

/**
 * Interface exposed to the Receipts team.
 * Calculates the full pricing breakdown for an order and returns a
 * {@link ReceiptSummary} ready for receipt/bill generation.
 *
 * <p>This is a transfer-only API - no data is persisted to the database.
 * The Receipts team is responsible for storing the result on their side.</p>
 */
public interface IReceiptPricingService {

    /**
     * Calculates the complete receipt for the given order.
     *
     * @param orderId    the order identifier (supplied by the caller)
     * @param cart       the line items in the customer's cart
     * @param customerId the customer placing the order
     * @return a {@link ReceiptSummary} containing per-line breakdown and aggregated totals
     */
    ReceiptSummary calculateReceipt(String orderId, OrderLineItem[] cart, String customerId);
}
