package com.pricingos.pricing.discount;

import com.pricingos.common.IDiscountPolicyService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class DiscountPolicyStore implements IDiscountPolicyService {

    private final Map<String, DiscountPolicy> policyRegistry = new ConcurrentHashMap<>();
    private final boolean strictComplianceMode;

    public DiscountPolicyStore(boolean strictComplianceMode) {
        this.strictComplianceMode = strictComplianceMode;
    }

    @Override
    public void createPolicy(String name, int priority, String stackingRule) {
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(stackingRule, "stackingRule cannot be null");

        DiscountPolicy policy = DiscountPolicy.builder(name)
            .priorityLevel(priority)
            .stackingRule(stackingRule)
            .isActive(true)
            .build();
        policyRegistry.put(policy.getPolicyId(), policy);
    }

    @Override
    public String[] getActivePolicies() {
        return policyRegistry.values().stream()
            .filter(DiscountPolicy::isActive)
            .sorted(Comparator.comparingInt(DiscountPolicy::getPriorityLevel).reversed())
            .map(DiscountPolicy::getPolicyName)
            .toArray(String[]::new);
    }

    @Override
    public boolean validateCompliance(String[] appliedDiscounts) {
        Objects.requireNonNull(appliedDiscounts, "appliedDiscounts cannot be null");
        if (!strictComplianceMode) {
            return true;
        }

        List<DiscountPolicy> exclusivePolicies = new ArrayList<>();
        for (DiscountPolicy policy : policyRegistry.values()) {
            if (policy.isActive() && "EXCLUSIVE".equalsIgnoreCase(policy.getStackingRule())) {
                exclusivePolicies.add(policy);
            }
        }
        if (exclusivePolicies.isEmpty()) {
            return true;
        }
        if (appliedDiscounts.length != 1) {
            return appliedDiscounts.length == 0;
        }

        String appliedDiscount = appliedDiscounts[0];
        for (DiscountPolicy exclusivePolicy : exclusivePolicies) {
            if (exclusivePolicy.getPolicyName().equalsIgnoreCase(appliedDiscount)) {
                return true;
            }
        }
        return false;
    }

    Map<String, DiscountPolicy> getPolicyRegistry() {
        return Map.copyOf(policyRegistry);
    }
}
