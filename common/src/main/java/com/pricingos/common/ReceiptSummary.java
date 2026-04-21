package com.pricingos.common;

import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable DTO that the Pricing module produces for the Receipts team.
 * Contains per-line pricing breakdown plus order-level aggregated totals.
 * No database interaction - pure in-memory transfer object.
 */
public record ReceiptSummary(
    String orderId,
    String customerId,
    InvoiceLineItem[] lineItems,
    double grossTotal,
    double totalDiscount,
    double netPayable
) {
    public ReceiptSummary {
        orderId    = ValidationUtils.requireNonBlank(orderId, "orderId");
        customerId = ValidationUtils.requireNonBlank(customerId, "customerId");
        Objects.requireNonNull(lineItems, "lineItems cannot be null");
        if (lineItems.length == 0) {
            throw new IllegalArgumentException("lineItems cannot be empty");
        }
        lineItems = Arrays.copyOf(lineItems, lineItems.length);
        ValidationUtils.requireFiniteNonNegative(grossTotal, "grossTotal");
        ValidationUtils.requireFiniteNonNegative(totalDiscount, "totalDiscount");
        ValidationUtils.requireFiniteNonNegative(netPayable, "netPayable");
    }

    @Override
    public InvoiceLineItem[] lineItems() {
        return Arrays.copyOf(lineItems, lineItems.length);
    }

    public int totalQuantity() {
        int sum = 0;
        for (InvoiceLineItem item : lineItems) {
            sum += item.quantity();
        }
        return sum;
    }
}
