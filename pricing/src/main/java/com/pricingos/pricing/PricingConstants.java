package com.pricingos.pricing;

/**
 * Shared constants for the pricing subsystem.
 * Centralises values used across multiple packages to prevent cyclic dependencies.
 */
public final class PricingConstants {

    /** Default currency code applied when no explicit currency is specified. */
    public static final String DEFAULT_CURRENCY = "INR";

    private PricingConstants() {
        // utility class
    }
}
