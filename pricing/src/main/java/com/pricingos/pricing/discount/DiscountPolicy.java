package com.pricingos.pricing.discount;

import com.pricingos.common.ValidationUtils;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

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

    public static Builder builder(String policyName) {
        return new Builder(policyName);
    }

    public static final class Builder {
        private final String policyId = "POLICY-" + UUID.randomUUID();
        private final String policyName;
        private int priorityLevel = 0;
        private String stackingRule = "ADDITIVE";
        private LocalDateTime createdAt = LocalDateTime.now();
        private boolean isActive = true;

        private Builder(String policyName) {
            this.policyName = ValidationUtils.requireNonBlank(policyName, "policyName");
        }

        public Builder priorityLevel(int priority) {
            if (priority < 1)
                throw new IllegalArgumentException("priorityLevel must be >= 1");
            this.priorityLevel = priority;
            return this;
        }

        public Builder stackingRule(String rule) {
            this.stackingRule = ValidationUtils.requireNonBlank(rule, "stackingRule");
            return this;
        }

        public Builder createdAt(LocalDateTime timestamp) {
            this.createdAt = Objects.requireNonNull(timestamp, "createdAt cannot be null");
            return this;
        }

        public Builder isActive(boolean active) {
            this.isActive = active;
            return this;
        }

        public DiscountPolicy build() {
            return new DiscountPolicy(this);
        }
    }

    public void setActive(boolean active) {
        this.isActive = active;
    }

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
