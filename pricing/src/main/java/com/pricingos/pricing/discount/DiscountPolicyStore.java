package com.pricingos.pricing.discount;

import com.pricingos.common.IDiscountPolicyService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory policy store implementing IDiscountPolicyService.
 * Manages discount policies, enforces compliance rules, and tracks active policies.
 *
 * <p>Thread-safe: all registry mutations use ConcurrentHashMap.
 */
public class DiscountPolicyStore implements IDiscountPolicyService {

    private final Map<String, DiscountPolicy> policyRegistry = new ConcurrentHashMap<>();
    private final boolean strictComplianceMode;

    /**
     * Constructs a policy store with optional strict compliance enforcement.
     *
     * @param strictComplianceMode if true, EXCLUSIVE policies are strictly enforced;
     *                             if false, compliance checks are more lenient
     */
    public DiscountPolicyStore(boolean strictComplianceMode) {
        this.strictComplianceMode = strictComplianceMode;
    }

    // ── IDiscountPolicyService implementation ─────────────────────────────────────

    /**
     * {@inheritDoc}
     *
     * <p>Creates a new policy using the Builder pattern and registers it in the registry.
     */
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

    /**
     * {@inheritDoc}
     *
     * <p>Returns names of all currently active policies as a String array.
     */
    @Override
    public String[] getActivePolicies() {
        return policyRegistry.values().stream()
            .filter(DiscountPolicy::isActive)
            .map(DiscountPolicy::getPolicyName)
            .toArray(String[]::new);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Validates that applied discounts comply with active policies.
     * In strict compliance mode: if any EXCLUSIVE policy is active, only that policy's
     * discounts are allowed. In lenient mode, this check is skipped.
     * Returns true if valid, false if a violation is detected.
     */
    @Override
    public boolean validateCompliance(String[] appliedDiscounts) {
        Objects.requireNonNull(appliedDiscounts, "appliedDiscounts cannot be null");

        if (!strictComplianceMode) {
            return true; // Lenient mode: no compliance check
        }

        // Strict mode: check for EXCLUSIVE policies
        List<DiscountPolicy> exclusivePolicies = new ArrayList<>();
        for (DiscountPolicy policy : policyRegistry.values()) {
            if (policy.isActive() && "EXCLUSIVE".equalsIgnoreCase(policy.getStackingRule())) {
                exclusivePolicies.add(policy);
            }
        }

        // If there are active EXCLUSIVE policies and multiple discounts are applied, it's a violation
        if (!exclusivePolicies.isEmpty() && appliedDiscounts.length > 1) {
            return false; // Violation: EXCLUSIVE policy + other discounts
        }

        return true; // Compliant
    }

    // ── Package-private helpers for testing ──────────────────────────────────────

    /**
     * Returns the policy registry (package-private for tests).
     *
     * @return the unmodifiable view of the policy registry
     */
    Map<String, DiscountPolicy> getPolicyRegistry() {
        return Map.copyOf(policyRegistry);
    }
}
