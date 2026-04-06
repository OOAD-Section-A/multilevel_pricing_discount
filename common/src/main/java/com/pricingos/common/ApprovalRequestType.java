package com.pricingos.common;

/**
 * Categories of price override requests handled by Component 8.
 * Matches the request_type field in the Data Dictionary.
 */
public enum ApprovalRequestType {
    /** A cashier or sales rep manually applying a discount beyond normal policy. */
    MANUAL_DISCOUNT,

    /** Request to use pricing outside the terms of an existing B2B contract. */
    CONTRACT_BYPASS,

    /** Request for an exception to a configured discount policy rule. */
    POLICY_EXCEPTION
}