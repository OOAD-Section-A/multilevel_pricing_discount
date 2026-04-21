package com.pricingos.pricing.discount;

import com.pricingos.common.IDiscountPolicyService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class DiscountPolicyStore implements IDiscountPolicyService {

    
    private final boolean strictComplianceMode;
    private final ConcurrentHashMap<String, DiscountPolicy> policyCache = new ConcurrentHashMap<>();

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
        // Cache the policy in memory. Note: Database team's PricingAdapter 
        // does not expose a discount policy persistence API, so we maintain this cache.
        policyCache.put(policy.getPolicyId(), policy);
    }

    @Override
    public String[] getActivePolicies() {
        return policyCache.values().stream()
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
        for (DiscountPolicy policy : policyCache.values()) {
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
        return policyCache.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue()));
    }
}
