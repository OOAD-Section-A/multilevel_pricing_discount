package com.pricingos.common;

/**
 * Enumeration of discount modes supported by the Promotion & Campaign Manager.
 * GRASP Information Expert: each constant owns its own display label.
 * Matches the discount_type field in the Data Dictionary (Component 5).
 */
public enum DiscountType {
    /** Reduces price by a percentage of the line-item subtotal (e.g., 20% off). */
    PERCENTAGE_OFF,

    /** Reduces price by a fixed monetary amount (e.g., ₹100 off). */
    FIXED_AMOUNT,

    /** Buy-X-Get-Y free promotions (e.g., buy 2 get 1 free). */
    BUY_X_GET_Y
}