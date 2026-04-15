package com.pricingos.pricing.tier;

import com.pricingos.common.CustomerTier;
import com.pricingos.common.ICustomerTierService;
import com.pricingos.common.IOrderService;
import com.pricingos.common.ValidationUtils;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Customer tier service implementation.
 * Behavioral pattern: delegates tier rules to a TierEvaluationStrategy.
 * SOLID DIP/OCP: depends on strategy abstraction and can extend rules without modifying this class.
 */
public class CustomerTierEngine implements ICustomerTierService {
    private static final long EXTERNAL_FETCH_TIMEOUT_SECONDS = 2L;
    private final IOrderService orderService;
    private final TierEvaluationStrategy tierEvaluationStrategy;
    private final ConcurrentHashMap<String, CustomerTier> tierByCustomer = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CustomerTier> overrideByCustomer = new ConcurrentHashMap<>();

    public CustomerTierEngine(IOrderService orderService) {
        this(orderService, new SpendBasedTierEvaluationStrategy());
    }

    public CustomerTierEngine(IOrderService orderService, TierEvaluationStrategy tierEvaluationStrategy) {
        this.orderService = Objects.requireNonNull(orderService, "orderService cannot be null");
        this.tierEvaluationStrategy = Objects.requireNonNull(tierEvaluationStrategy, "tierEvaluationStrategy cannot be null");
    }

    @Override
    public CustomerTier getTier(String customerId) {
        String normalizedCustomerId = ValidationUtils.requireNonBlank(customerId, "customerId");
        CustomerTier overridden = overrideByCustomer.get(normalizedCustomerId);
        if (overridden != null) {
            return overridden;
        }
        CustomerTier evaluated = tierByCustomer.get(normalizedCustomerId);
        return evaluated == null ? CustomerTier.STANDARD : evaluated;
    }

    @Override
    public double getDiscountRate(String customerId) {
        return getTier(customerId).getDiscountRate();
    }

    @Override
    public void evaluateTier(String customerId) {
        String normalizedCustomerId = ValidationUtils.requireNonBlank(customerId, "customerId");
        if (overrideByCustomer.containsKey(normalizedCustomerId)) {
            return;
        }

        if (normalizedCustomerId.startsWith("UNKNOWN")) {
            tierByCustomer.put(normalizedCustomerId, CustomerTier.STANDARD);
            return;
        }

        double annualSpend;
        int annualOrderCount;
        try {
            annualSpend = CompletableFuture
                .supplyAsync(() -> orderService.getTotalSpendLastYear(normalizedCustomerId))
                .get(EXTERNAL_FETCH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            annualOrderCount = CompletableFuture
                .supplyAsync(() -> orderService.getOrderCountLastYear(normalizedCustomerId))
                .get(EXTERNAL_FETCH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            tierByCustomer.put(normalizedCustomerId, CustomerTier.STANDARD);
            return;
        } catch (ExecutionException | TimeoutException e) {
            tierByCustomer.put(normalizedCustomerId, CustomerTier.STANDARD);
            return;
        }

        CustomerTier evaluatedTier = tierEvaluationStrategy.evaluate(
                normalizedCustomerId,
                annualSpend,
                annualOrderCount
        );

        tierByCustomer.put(normalizedCustomerId, evaluatedTier);
    }

    @Override
    public void overrideTier(String customerId, CustomerTier tier) {
        String normalizedCustomerId = ValidationUtils.requireNonBlank(customerId, "customerId");
        Objects.requireNonNull(tier, "tier cannot be null");
        overrideByCustomer.put(normalizedCustomerId, tier);
        tierByCustomer.put(normalizedCustomerId, tier);
    }
}
