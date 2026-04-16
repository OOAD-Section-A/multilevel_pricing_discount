package com.pricingos.pricing.invoice;

import com.pricingos.common.OrderLineItem;
import com.pricingos.common.PriceResult;
import com.pricingos.common.PricingOverrideRequest;
import com.pricingos.common.IDiscountRulesEngine;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class InvoiceServiceTest {

    @Test
    void shouldBuildInvoiceLinesFromPriceResults() {
        IDiscountRulesEngine rulesEngine = new IDiscountRulesEngine() {
            @Override
            public PriceResult[] calculateFinalPrice(OrderLineItem[] cart, String customerId) {
                return new PriceResult[] {
                    new PriceResult("SKU-1", 100.0, 90.0, new String[] {"PromoCodeStrategy"}, true)
                };
            }

            @Override
            public boolean submitPricingOverride(PricingOverrideRequest request) {
                return true;
            }
        };

        OrderLineItem[] cart = {
            new OrderLineItem("SKU-1", 2, "GLOBAL", "RETAIL")
        };

        InvoiceService invoiceService = new InvoiceService(rulesEngine);
        var lines = invoiceService.buildInvoiceLines(cart, "CUST-1");

        Assertions.assertEquals(1, lines.length);
        Assertions.assertEquals("SKU-1", lines[0].skuId());
        Assertions.assertEquals(2, lines[0].quantity());
        Assertions.assertEquals(100.0, lines[0].baseUnitPrice());
        Assertions.assertEquals(90.0, lines[0].finalUnitPrice());
        Assertions.assertEquals(200.0, lines[0].lineSubtotal());
        Assertions.assertEquals(20.0, lines[0].discountAmount());
        Assertions.assertEquals(180.0, lines[0].lineTotal());
        Assertions.assertTrue(lines[0].approved());
    }
}
