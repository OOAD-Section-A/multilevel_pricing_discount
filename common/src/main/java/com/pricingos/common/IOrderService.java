package com.pricingos.common;

/**
 * Integration boundary for order metrics owned by the order subsystem.
 * Pricing depends on this abstraction rather than any concrete order implementation.
 */
public interface IOrderService {
    double getTotalSpendLastYear(String customerId);
    int getOrderCountLastYear(String customerId);
}

