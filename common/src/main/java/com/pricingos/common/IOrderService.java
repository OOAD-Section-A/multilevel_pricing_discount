package com.pricingos.common;

public interface IOrderService {
    double getTotalSpendLastYear(String customerId);
    int getOrderCountLastYear(String customerId);
}
