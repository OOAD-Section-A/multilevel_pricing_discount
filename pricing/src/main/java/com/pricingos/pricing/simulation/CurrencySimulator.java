package com.pricingos.pricing.simulation;

import com.pricingos.common.IExchangeRateService;
import com.pricingos.common.ValidationUtils;
import com.scm.subsystems.MultiLevelPricingSubsystem;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class CurrencySimulator implements IExchangeRateService {

    private final Map<String, Double> ratesToInr = new ConcurrentHashMap<>();
    private MultiLevelPricingSubsystem exceptions;
    private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");
    
    private MultiLevelPricingSubsystem getExceptions() {
        if (exceptions == null && IS_WINDOWS) {
            try {
                exceptions = MultiLevelPricingSubsystem.INSTANCE;
            } catch (Exception e) {
                // Windows Event Viewer initialization failed
                exceptions = null;
            }
        }
        return exceptions;
    }

    public CurrencySimulator() {
        ratesToInr.put("INR", 1.0);
        ratesToInr.put("USD", 83.0);
        ratesToInr.put("EUR", 90.0);
        ratesToInr.put("GBP", 105.0);
    }

    @Override
    public double convert(double amount, String fromCurrency, String toCurrency) {
        ValidationUtils.requireFiniteNonNegative(amount, "amount");
        return amount * getRate(fromCurrency, toCurrency);
    }

    @Override
    public double getRate(String fromCurrency, String toCurrency) {
        String from = ValidationUtils.requireNonBlank(fromCurrency, "fromCurrency").toUpperCase();
        String to = ValidationUtils.requireNonBlank(toCurrency, "toCurrency").toUpperCase();
        Double fromToInr = ratesToInr.get(from);
        Double toToInr = ratesToInr.get(to);
        if (fromToInr == null || toToInr == null) {
            try {
                if (getExceptions() != null) {
                    exceptions.onInvalidPromoCode(from + " -> " + to);
                }
            } catch (Exception e) {
                // Windows Event Viewer not available on Linux
            }
            throw new IllegalArgumentException("Unsupported currency conversion: " + from + " -> " + to);
        }
        return fromToInr / toToInr;
    }

    public void nudgeRates() {
        for (Map.Entry<String, Double> e : ratesToInr.entrySet()) {
            if ("INR".equals(e.getKey())) {
                continue;
            }
            double driftPct = ThreadLocalRandom.current().nextDouble(-0.005, 0.005);
            double next = e.getValue() * (1.0 + driftPct);
            ratesToInr.put(e.getKey(), Math.max(0.0001, next));
        }
    }
}
