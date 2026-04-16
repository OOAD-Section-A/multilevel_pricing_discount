package com.pricingos.common;

public interface IInvoiceService {

    InvoiceLineItem[] buildInvoiceLines(OrderLineItem[] cart, String customerId);
}
