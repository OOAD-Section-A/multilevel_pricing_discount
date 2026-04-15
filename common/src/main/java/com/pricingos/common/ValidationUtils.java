package com.pricingos.common;

import java.util.Objects;

public final class ValidationUtils {

    private ValidationUtils() {

    }

    public static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " cannot be null");
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " cannot be blank");
        }
        return normalized;
    }

    public static double requireFiniteNonNegative(double value, String fieldName) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(fieldName + " must be a non-negative finite number");
        }
        return value;
    }

    public static double requireFinitePositive(double value, String fieldName) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(fieldName + " must be a positive finite number");
        }
        return value;
    }

    public static int requireAtLeast(int value, int minimum, String fieldName) {
        if (value < minimum) {
            throw new IllegalArgumentException(fieldName + " must be >= " + minimum);
        }
        return value;
    }
}
