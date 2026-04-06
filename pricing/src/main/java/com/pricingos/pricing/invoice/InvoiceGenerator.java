package com.pricingos.pricing.invoice;

import com.pricingos.common.InvoiceLineItem;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Assembles the final invoice data structure from individual priced line items.
 *
 * <p>Structural pattern (Facade / Composer): acts as the single exit point of the
 * pricing subsystem, collecting the outputs of Components 4 (Discount Rules Engine),
 * 5 (Promotion Manager), and 8 (Approval Workflow) into one consistent invoice document.
 *
 * <p>This satisfies the "final invoice data structures" deliverable mentioned in the
 * brief for Component 8's scope.
 *
 * <p>GRASP Information Expert: this class owns the logic to compute invoice-level totals
 * (gross, net, total discount, revenue delta) from the individual line items.
 */
public class InvoiceGenerator {

    /**
     * Immutable invoice document. Produced by {@link InvoiceGenerator#generate}.
     */
    public static final class Invoice {
        private final String invoiceId;
        private final String orderId;
        private final String customerId;
        private final LocalDateTime generatedAt;
        private final List<InvoiceLineItem> lineItems;
        private final double grossTotal;        // sum of (baseUnitPrice × quantity) per line
        private final double netTotal;          // sum of (finalUnitPrice × quantity) per line
        private final double totalDiscountGiven;// grossTotal − netTotal
        private final String approvalId;        // null if no manual override was involved

        private Invoice(Builder b) {
            this.invoiceId          = b.invoiceId;
            this.orderId            = b.orderId;
            this.customerId         = b.customerId;
            this.generatedAt        = LocalDateTime.now();
            this.lineItems          = Collections.unmodifiableList(b.lineItems);
            this.grossTotal         = b.lineItems.stream()
                                        .mapToDouble(li -> li.getBaseUnitPrice() * li.getQuantity())
                                        .sum();
            this.netTotal           = b.lineItems.stream()
                                        .mapToDouble(InvoiceLineItem::getLineTotal)
                                        .sum();
            this.totalDiscountGiven = grossTotal - netTotal;
            this.approvalId         = b.approvalId;
        }

        public String getInvoiceId()            { return invoiceId; }
        public String getOrderId()              { return orderId; }
        public String getCustomerId()           { return customerId; }
        public LocalDateTime getGeneratedAt()   { return generatedAt; }
        public List<InvoiceLineItem> getLineItems() { return lineItems; }
        public double getGrossTotal()           { return grossTotal; }
        public double getNetTotal()             { return netTotal; }
        public double getTotalDiscountGiven()   { return totalDiscountGiven; }
        public String getApprovalId()           { return approvalId; }

        /**
         * Generates a human-readable text receipt — useful for POS printout and audit.
         */
        public String generateReceiptString() {
            StringBuilder sb = new StringBuilder();
            sb.append("============================\n");
            sb.append("INVOICE ").append(invoiceId).append("\n");
            sb.append("Order:    ").append(orderId).append("\n");
            sb.append("Customer: ").append(customerId).append("\n");
            sb.append("Date:     ").append(generatedAt).append("\n");
            if (approvalId != null) {
                sb.append("Approval: ").append(approvalId).append("\n");
            }
            sb.append("----------------------------\n");
            for (InvoiceLineItem li : lineItems) {
                sb.append(li.generateReceiptLine()).append("\n");
            }
            sb.append("----------------------------\n");
            sb.append(String.format("Gross Total:     %.2f%n", grossTotal));
            sb.append(String.format("Total Discount:  %.2f%n", totalDiscountGiven));
            sb.append(String.format("NET TOTAL:       %.2f%n", netTotal));
            sb.append("============================\n");
            return sb.toString();
        }

        // Inner builder for Invoice — Creational (Builder) within the invoice scope.
        static final class Builder {
            private final String invoiceId = "INV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            private String orderId;
            private String customerId;
            private List<InvoiceLineItem> lineItems = new ArrayList<>();
            private String approvalId;

            Builder orderId(String orderId)       { this.orderId = orderId; return this; }
            Builder customerId(String customerId) { this.customerId = customerId; return this; }
            Builder lineItems(List<InvoiceLineItem> items) { this.lineItems = new ArrayList<>(items); return this; }
            Builder approvalId(String approvalId) { this.approvalId = approvalId; return this; }

            Invoice build() {
                Objects.requireNonNull(orderId,    "orderId is required");
                Objects.requireNonNull(customerId, "customerId is required");
                if (lineItems.isEmpty()) throw new IllegalStateException("Invoice must have at least one line item");
                return new Invoice(this);
            }
        }
    }

    /**
     * Assembles and returns a finalized {@link Invoice} from the provided line items.
     *
     * @param orderId     the order identifier
     * @param customerId  the customer this invoice belongs to
     * @param lineItems   priced line items produced by the Discount Rules Engine
     * @param approvalId  approval_id if a manual override was involved; null otherwise
     * @return a fully populated, immutable Invoice
     */
    public Invoice generate(String orderId, String customerId,
                            List<InvoiceLineItem> lineItems, String approvalId) {
        requireNonBlank(orderId, "orderId");
        requireNonBlank(customerId, "customerId");
        Objects.requireNonNull(lineItems, "lineItems cannot be null");
        if (lineItems.isEmpty()) throw new IllegalArgumentException("lineItems cannot be empty");

        return new Invoice.Builder()
            .orderId(orderId)
            .customerId(customerId)
            .lineItems(lineItems)
            .approvalId(approvalId)
            .build();
    }

    private static void requireNonBlank(String v, String field) {
        Objects.requireNonNull(v, field + " cannot be null");
        if (v.trim().isEmpty()) throw new IllegalArgumentException(field + " cannot be blank");
    }
}