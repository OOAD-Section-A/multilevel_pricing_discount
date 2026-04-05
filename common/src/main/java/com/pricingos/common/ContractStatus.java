package com.pricingos.common;

/**
 * Lifecycle states for a customer contract.
 */
public enum ContractStatus {
    DRAFT,
    PENDING_APPROVAL,
    ACTIVE,
    EXPIRING,
    EXPIRED
}