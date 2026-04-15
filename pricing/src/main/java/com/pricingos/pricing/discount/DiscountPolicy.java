package com.pricingos.pricing.discount;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain object representing a discount policy with priority and stacking rules.
 * Immutable except for the isActive flag. Use Builder pattern for construction.
 *
 * <p>Policies control which discounts can be combined and in what order they're applied.
 * Example stacking rules: "EXCLUSIVE" (no other discounts allowed), "ADDITIVE" (can combine).
 */
public final class DiscountPolicy {

    private final String policyId;
    private final String policyName;
    private final int priorityLevel;
    private final String stackingRule;
    private final LocalDateTime createdAt;
    private volatile boolean isActive;

    private DiscountPolicy(Builder builder) {
        this.policyId = builder.policyId;
        this.policyName = builder.policyName;
        this.priorityLevel = builder.priorityLevel;
        this.stackingRule = builder.stackingRule;
        this.createdAt = builder.createdAt;
        this.isActive = builder.isActive;
    }

    // ── Static Builder ────────────────────────────────────────────────────────────

    /**
     * Entry point for the Builder pattern.
     *
     * @param policyName human-readable policy name
     * @return a new Builder configured with this policy name
     */
    public static Builder builder(String policyName) {
        return new Builder(policyName);
    }

    /**
     * Inner static builder class for safe, readable construction of DiscountPolicy.
     */
    public static final class Builder {
        private final String policyId = "POLICY-" + UUID.randomUUID();
        private final String policyName;
        private int priorityLevel = 0;
        private String stackingRule = "ADDITIVE"; // default
        private LocalDateTime createdAt = LocalDateTime.now();
        private boolean isActive = true;

        private Builder(String policyName) {
            this.policyName = requireNonBlank(policyName, "policyName");
        }

        /**
         * Sets the priority level (higher values = applied first).
         *
         * @param priority priority level; must be >= 1
         * @return this builder
         */
        public Builder priorityLevel(int priority) {
            if (priority < 1)
                throw new IllegalArgumentException("priorityLevel must be >= 1");
            this.priorityLevel = priority;
            return this;
        }

        /**
         * Sets the stacking rule: "EXCLUSIVE" or "ADDITIVE".
         *
         * @param rule the stacking rule
         * @return this builder
         */
        public Builder stackingRule(String rule) {
            this.stackingRule = requireNonBlank(rule, "stackingRule");
            return this;
        }

        /**
         * Sets the creation timestamp.
         *
         * @param timestamp the creation time
         * @return this builder
         */
        public Builder createdAt(LocalDateTime timestamp) {
            this.createdAt = Objects.requireNonNull(timestamp, "createdAt cannot be null");
            return this;
        }

        /**
         * Sets whether the policy is initially active.
         *
         * @param active active state
         * @return this builder
         */
        public Builder isActive(boolean active) {
            this.isActive = active;
            return this;
        }

        /**
         * Builds and returns the immutable DiscountPolicy.
         *
         * @return the constructed policy
         */
        public DiscountPolicy build() {
            return new DiscountPolicy(this);
        }

        private static String requireNonBlank(String v, String field) {
            Objects.requireNonNull(v, field + " cannot be null");
            if (v.trim().isEmpty())
                throw new IllegalArgumentException(field + " cannot be blank");
            return v.trim();
        }
    }

    // ── Domain logic ──────────────────────────────────────────────────────────────

    /**
     * Toggles the active state of this policy. Used by policy management operations.
     *
     * @param active new active state
     */
    public void setActive(boolean active) {
        this.isActive = active;
    }

    // ── Accessors ─────────────────────────────────────────────────────────────────

    public String getPolicyId()          { return policyId; }
    public String getPolicyName()        { return policyName; }
    public int getPriorityLevel()        { return priorityLevel; }
    public String getStackingRule()      { return stackingRule; }
    public LocalDateTime getCreatedAt()  { return createdAt; }
    public boolean isActive()            { return isActive; }

    @Override
    public String toString() {
        return String.format("DiscountPolicy{id=%s, name=%s, priority=%d, stacking=%s, active=%b}",
            policyId, policyName, priorityLevel, stackingRule, isActive);
    }
}
