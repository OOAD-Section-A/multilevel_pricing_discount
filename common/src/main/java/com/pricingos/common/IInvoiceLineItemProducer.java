package com.pricingos.common;

/**
 * Service interface for producing invoice line items with all discount and pricing details.
 * This is a STUB interface — implementation is provided by Amit's subsystem.
 *
 * <p>Converts discount-adjusted pricing calculations into formal invoice line items
 * ready for financial reporting and customer invoicing.
 */
public interface IInvoiceLineItemProducer {

    /**
     * Produces an invoice line item from pricing and discount information.
     * Transforms raw pricing calculations into a formal billing record.
     *
     * @param skuId              the product ID
     * @param quantity           the number of units ordered
     * @param finalUnitPrice     the net unit price after all discounts
     * @param appliedDiscounts   array of discount names/codes applied to this line
     * @return a populated InvoiceLineItem ready for invoicing
     */
    InvoiceLineItem produceLineItem(String skuId, int quantity, double finalUnitPrice,
                                     String[] appliedDiscounts);
}
