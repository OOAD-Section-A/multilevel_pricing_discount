package com.pricingos.pricing.receipt;

import com.pricingos.common.IInvoiceService;
import com.pricingos.common.InvoiceLineItem;
import com.pricingos.common.OrderLineItem;
import com.pricingos.common.ReceiptSummary;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ReceiptPricingServiceTest {

    /**
     * Stub invoice service that returns pre-built InvoiceLineItems
     * so the test focuses on aggregation logic only.
     */
    private static IInvoiceService stubInvoice(InvoiceLineItem... items) {
        return (cart, customerId) -> items;
    }

    @Test
    void shouldAggregateMultipleLineItemsIntoReceiptSummary() {
        InvoiceLineItem line1 = new InvoiceLineItem(
            "SKU-A", 2, 100.0, 90.0, 200.0, 20.0, 180.0,
            new String[]{"VOLUME_10"}, true
        );
        InvoiceLineItem line2 = new InvoiceLineItem(
            "SKU-B", 1, 50.0, 45.0, 50.0, 5.0, 45.0,
            new String[]{"PROMO_SUMMER"}, true
        );

        ReceiptPricingService service = new ReceiptPricingService(stubInvoice(line1, line2));

        OrderLineItem[] cart = {
            new OrderLineItem("SKU-A", 2, "GLOBAL", "RETAIL"),
            new OrderLineItem("SKU-B", 1, "GLOBAL", "RETAIL")
        };

        ReceiptSummary summary = service.calculateReceipt("ORD-001", cart, "CUST-42");

        assertEquals("ORD-001", summary.orderId());
        assertEquals("CUST-42", summary.customerId());
        assertEquals(2, summary.lineItems().length);

        // grossTotal = 200 + 50 = 250
        assertEquals(250.0, summary.grossTotal(), 0.001);
        // totalDiscount = 20 + 5 = 25
        assertEquals(25.0, summary.totalDiscount(), 0.001);
        // netPayable = 180 + 45 = 225
        assertEquals(225.0, summary.netPayable(), 0.001);
        // totalQuantity = 2 + 1 = 3
        assertEquals(3, summary.totalQuantity());
    }

    @Test
    void shouldRejectNullCart() {
        ReceiptPricingService service = new ReceiptPricingService(stubInvoice());
        assertThrows(NullPointerException.class,
            () -> service.calculateReceipt("ORD-X", null, "CUST-1"));
    }

    @Test
    void shouldRejectEmptyCart() {
        ReceiptPricingService service = new ReceiptPricingService(stubInvoice());
        assertThrows(IllegalArgumentException.class,
            () -> service.calculateReceipt("ORD-X", new OrderLineItem[0], "CUST-1"));
    }

    @Test
    void shouldRejectBlankOrderId() {
        ReceiptPricingService service = new ReceiptPricingService(stubInvoice());
        OrderLineItem[] cart = { new OrderLineItem("SKU-A", 1, "US", "ONLINE") };
        assertThrows(IllegalArgumentException.class,
            () -> service.calculateReceipt("  ", cart, "CUST-1"));
    }
}
