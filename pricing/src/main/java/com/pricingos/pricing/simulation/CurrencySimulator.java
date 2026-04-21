package com.pricingos.pricing.simulation;

import com.pricingos.common.IExchangeRateService;
import com.pricingos.common.ValidationUtils;
import com.scm.subsystems.MultiLevelPricingSubsystem;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;
import java.util.logging.Level;

public class CurrencySimulator implements IExchangeRateService {

    private static final Logger LOGGER = Logger.getLogger(CurrencySimulator.class.getName());
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
    
    /**
     * Temporary workaround for logging unregistered exceptions (ID 0).
     * 
     * The raise(int id, String name, String message, Severity severity) method 
     * in MultiLevelPricingSubsystem is currently PRIVATE. This method logs the 
     * exception locally until exceptions team makes raise() public.
     * 
     * TODO: Replace with exceptions.raise(0, name, message, severity) after exceptions team update
     * 
     * @param exceptionId Exception type (ID 0 for unregistered)
     * @param exceptionName Name of the exception type
     * @param message Detailed error message
     */
    private void logUnregistegeredException(int exceptionId, String exceptionName, String message) {
        String logMessage = String.format(
            "[UNREGISTERED_EXCEPTION_ID_%d] %s: %s",
            exceptionId, exceptionName, message
        );
        LOGGER.warning(logMessage);
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
            // TEMPORARY WORKAROUND: Using local logging instead of exceptions.raise(0, ...)
            // because raise() method is PRIVATE in MultiLevelPricingSubsystem
            // TODO: Change to exceptions.raise(0, "UNSUPPORTED_CURRENCY_CONVERSION", ...) after exceptions team makes raise() public
            logUnregistegeredException(0, "UNSUPPORTED_CURRENCY_CONVERSION", 
                "Currency conversion not supported: " + from + " -> " + to);
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
