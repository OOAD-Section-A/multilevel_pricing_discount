package com.pricingos.common;

// Interface for the Vertex Team
public interface IOrderService {
    double getTotalSpendLastYear(String customerId);
    int getOrderCountLastYear(String customerId);
}

