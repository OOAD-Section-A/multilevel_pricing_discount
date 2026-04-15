package com.pricingos.common;

public interface IDiscountPolicyService {

    void createPolicy(String name, int priority, String stackingRule);

    String[] getActivePolicies();

    boolean validateCompliance(String[] appliedDiscounts);
}
