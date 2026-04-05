package com.pricingos.pricing.tier;

import com.pricingos.common.CustomerTier;
import com.pricingos.common.ICustomerTierService;
import com.pricingos.common.IOrderService;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Customer tier service implementation.
 * Behavioral pattern: delegates tier rules to a TierEvaluationStrategy.
 * SOLID DIP/OCP: depends on strategy abstraction and can extend rules without modifying this class.
 */
public class CustomerTierEngine implements ICustomerTierService {
    private final IOrderService orderService;
    private final TierEvaluationStrategy tierEvaluationStrategy;
    private final Map<String, CustomerTier> tierStore = new ConcurrentHashMap<>();
    private final Map<String, CustomerTier> manualOverrides = new ConcurrentHashMap<>();

    public CustomerTierEngine(IOrderService orderService) {
        this(orderService, new SpendBasedTierEvaluationStrategy());
    }

    public CustomerTierEngine(IOrderService orderService, TierEvaluationStrategy tierEvaluationStrategy) {
        this.orderService = Objects.requireNonNull(orderService, "orderService cannot be null");
        this.tierEvaluationStrategy = Objects.requireNonNull(tierEvaluationStrategy, "tierEvaluationStrategy cannot be null");
    }

    @Override
    public CustomerTier getTier(String customerId) {
        String normalizedCustomerId = requireCustomerId(customerId);
        CustomerTier overriddenTier = manualOverrides.get(normalizedCustomerId);
        if (overriddenTier != null) {
            return overriddenTier;
        }
        return tierStore.getOrDefault(normalizedCustomerId, CustomerTier.STANDARD);
    }

    @Override
    public double getDiscountRate(String customerId) {
        return getTier(customerId).getDiscountRate();
    }

    @Override
    public void evaluateTier(String customerId) {
        String normalizedCustomerId = requireCustomerId(customerId);
        if (manualOverrides.containsKey(normalizedCustomerId)) {
            return;
        }

        double annualSpend = orderService.getTotalSpendLastYear(normalizedCustomerId);
        int annualOrderCount = orderService.getOrderCountLastYear(normalizedCustomerId);

        CustomerTier evaluatedTier = tierEvaluationStrategy.evaluate(
                normalizedCustomerId,
                annualSpend,
                annualOrderCount
        );

        tierStore.compute(normalizedCustomerId, (id, ignored) -> {
            CustomerTier overriddenTier = manualOverrides.get(id);
            return overriddenTier != null ? overriddenTier : evaluatedTier;
        });
    }

    @Override
    public void overrideTier(String customerId, CustomerTier tier) {
        String normalizedCustomerId = requireCustomerId(customerId);
        Objects.requireNonNull(tier, "tier cannot be null");
        manualOverrides.put(normalizedCustomerId, tier);
        tierStore.put(normalizedCustomerId, tier);
    }

    private static String requireCustomerId(String customerId) {
        Objects.requireNonNull(customerId, "customerId cannot be null");
        String normalized = customerId.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("customerId cannot be blank");
        }
        return normalized;
    }
}
