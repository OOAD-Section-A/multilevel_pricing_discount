package com.pricingos.common;

/**
 * Service interface for managing discount policies and their compliance.
 * Policies define stacking rules, priorities, and exclusivity constraints.
 */
public interface IDiscountPolicyService {

    /**
     * Creates and registers a new discount policy.
     *
     * @param name         human-readable policy name (e.g., "Black Friday Exclusive")
     * @param priority     policy priority level (higher = applied first); must be >= 1
     * @param stackingRule stacking behavior: "EXCLUSIVE" (no other discounts allowed)
     *                     or "ADDITIVE" (can combine with other additive policies)
     * @throws IllegalArgumentException if input validation fails
     */
    void createPolicy(String name, int priority, String stackingRule);

    /**
     * Returns names of all currently active policies as a string array.
     *
     * @return array of active policy names, possibly empty
     */
    String[] getActivePolicies();

    /**
     * Validates that the given discount combinations comply with active policy rules.
     * If an EXCLUSIVE policy is active, no other policies may be applied simultaneously.
     *
     * @param appliedDiscounts array of discount/policy names that have been applied
     * @return true if the combination is compliant; false if it violates exclusivity rules
     */
    boolean validateCompliance(String[] appliedDiscounts);
}
