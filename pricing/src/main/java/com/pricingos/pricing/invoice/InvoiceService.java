package com.pricingos.pricing.invoice;

import com.pricingos.common.IInvoiceService;
import com.pricingos.common.IDiscountRulesEngine;
import com.pricingos.common.InvoiceLineItem;
import com.pricingos.common.OrderLineItem;
import com.pricingos.common.PriceResult;
import com.pricingos.common.ValidationUtils;
import java.util.Objects;

public class InvoiceService implements IInvoiceService {

    private final IDiscountRulesEngine discountRulesEngine;

    public InvoiceService(IDiscountRulesEngine discountRulesEngine) {
        this.discountRulesEngine = Objects.requireNonNull(discountRulesEngine, "discountRulesEngine cannot be null");
    }

    @Override
    public InvoiceLineItem[] buildInvoiceLines(OrderLineItem[] cart, String customerId) {
        Objects.requireNonNull(cart, "cart cannot be null");
        String normalizedCustomerId = ValidationUtils.requireNonBlank(customerId, "customerId");
        PriceResult[] results = discountRulesEngine.calculateFinalPrice(cart, normalizedCustomerId);
        if (results.length != cart.length) {
            throw new IllegalStateException("Pricing result size mismatch: cart=" + cart.length + ", results=" + results.length);
        }

        InvoiceLineItem[] invoice = new InvoiceLineItem[cart.length];
        for (int i = 0; i < cart.length; i++) {
            OrderLineItem item = cart[i];
            PriceResult result = results[i];
            double lineSubtotal = result.getBasePrice() * item.getQuantity();
            double lineTotal = result.getFinalDiscountedPrice() * item.getQuantity();
            double discountAmount = Math.max(0.0, lineSubtotal - lineTotal);
            invoice[i] = new InvoiceLineItem(
                item.getSkuId(),
                item.getQuantity(),
                result.getBasePrice(),
                result.getFinalDiscountedPrice(),
                lineSubtotal,
                discountAmount,
                lineTotal,
                result.getAppliedDiscounts(),
                result.isApproved()
            );
        }
        return invoice;
    }
}
