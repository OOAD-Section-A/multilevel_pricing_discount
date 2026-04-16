package com.pricingos.pricing.receipt;

import com.pricingos.common.IInvoiceService;
import com.pricingos.common.IReceiptPricingService;
import com.pricingos.common.InvoiceLineItem;
import com.pricingos.common.OrderLineItem;
import com.pricingos.common.ReceiptSummary;
import com.pricingos.common.ValidationUtils;

import java.util.Objects;

/**
 * Produces a {@link ReceiptSummary} by delegating per-line pricing to the
 * existing {@link IInvoiceService} and aggregating the totals.
 *
 * <p>This service performs NO database writes — it is a pure calculation
 * layer designed for cross-team transfer to the Receipts subsystem.</p>
 */
public class ReceiptPricingService implements IReceiptPricingService {

    private final IInvoiceService invoiceService;

    public ReceiptPricingService(IInvoiceService invoiceService) {
        this.invoiceService = Objects.requireNonNull(invoiceService, "invoiceService cannot be null");
    }

    @Override
    public ReceiptSummary calculateReceipt(String orderId, OrderLineItem[] cart, String customerId) {
        String normalizedOrderId = ValidationUtils.requireNonBlank(orderId, "orderId");
        Objects.requireNonNull(cart, "cart cannot be null");
        if (cart.length == 0) {
            throw new IllegalArgumentException("cart cannot be empty");
        }

        InvoiceLineItem[] lineItems = invoiceService.buildInvoiceLines(cart, customerId);

        double grossTotal    = 0.0;
        double totalDiscount = 0.0;
        double netPayable    = 0.0;

        for (InvoiceLineItem line : lineItems) {
            grossTotal    += line.lineSubtotal();
            totalDiscount += line.discountAmount();
            netPayable    += line.lineTotal();
        }

        return new ReceiptSummary(
            normalizedOrderId,
            customerId,
            lineItems,
            grossTotal,
            totalDiscount,
            netPayable
        );
    }
}
