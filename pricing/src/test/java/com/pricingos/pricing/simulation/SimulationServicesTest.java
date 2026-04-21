package com.pricingos.pricing.simulation;

import com.pricingos.db.PricingAdapter;
import com.jackfruit.scm.database.SupplyChainDatabaseFacade;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class SimulationServicesTest {

    @Test
    void shouldConvertAndNudgeCurrencyRates() {
        CurrencySimulator simulator = new CurrencySimulator();
        double usdToInr = simulator.getRate("USD", "INR");
        Assertions.assertTrue(usdToInr > 0.0);
        double inr = simulator.convert(10.0, "USD", "INR");
        Assertions.assertTrue(inr > 0.0);

        simulator.nudgeRates();
        double usdToInrAfter = simulator.getRate("USD", "INR");
        Assertions.assertTrue(usdToInrAfter > 0.0);
    }

    @Test
    void shouldApplyRegionalAdjustment() {
        // Create a minimal test adapter
        SupplyChainDatabaseFacade testFacade = new SupplyChainDatabaseFacade();
        PricingAdapter adapter = new PricingAdapter(testFacade);
        
        RegionalPricingService service = new RegionalPricingService(adapter);
        double adjusted = service.applyRegionalPricingAdjustment("SKU-1", 100.0, "SOUTH");
        Assertions.assertTrue(adjusted > 100.0);
    }

    @Test
    void shouldAdjustPriceFromMarketIndex() {
        MarketPriceSimulator market = new MarketPriceSimulator();
        DynamicPricingEngine engine = new DynamicPricingEngine(market);
        double before = engine.adjustBasePrice("SKU-1", 100.0);
        market.tick();
        double after = engine.adjustBasePrice("SKU-1", 100.0);
        Assertions.assertTrue(before > 0.0);
        Assertions.assertTrue(after > 0.0);
    }
}
