package com.pricingos.common;

public interface IExchangeRateService {

    double convert(double amount, String fromCurrency, String toCurrency);

    double getRate(String fromCurrency, String toCurrency);
}
