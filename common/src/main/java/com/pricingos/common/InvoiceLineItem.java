package com.pricingos.common;

import java.util.Arrays;

public record InvoiceLineItem(
    String skuId,
    int quantity,
    double baseUnitPrice,
    double finalUnitPrice,
    double lineSubtotal,
    double discountAmount,
    double lineTotal,
    String[] appliedDiscounts,
    boolean approved
) {
    public InvoiceLineItem {
        ValidationUtils.requireNonBlank(skuId, "skuId");
        ValidationUtils.requireAtLeast(quantity, 1, "quantity");
        ValidationUtils.requireFiniteNonNegative(baseUnitPrice, "baseUnitPrice");
        ValidationUtils.requireFiniteNonNegative(finalUnitPrice, "finalUnitPrice");
        ValidationUtils.requireFiniteNonNegative(lineSubtotal, "lineSubtotal");
        ValidationUtils.requireFiniteNonNegative(discountAmount, "discountAmount");
        ValidationUtils.requireFiniteNonNegative(lineTotal, "lineTotal");
        if (appliedDiscounts == null) {
            throw new IllegalArgumentException("appliedDiscounts cannot be null");
        }
        appliedDiscounts = Arrays.copyOf(appliedDiscounts, appliedDiscounts.length);
    }

    @Override
    public String[] appliedDiscounts() {
        return Arrays.copyOf(appliedDiscounts, appliedDiscounts.length);
    }
}
