package com.pricingos.pricing.tier;

import com.pricingos.common.CustomerTier;
import com.pricingos.common.ICustomerTierService;
import com.pricingos.common.IOrderService;
import com.pricingos.common.ValidationUtils;
import com.scm.subsystems.MultiLevelPricingSubsystem;
import com.jackfruit.scm.database.adapter.PricingAdapter;
import com.jackfruit.scm.database.model.PricingModels.CustomerTierCache;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class CustomerTierEngine implements ICustomerTierService {
    private static final long EXTERNAL_FETCH_TIMEOUT_SECONDS = 2L;
    private final IOrderService orderService;
    private final TierEvaluationStrategy tierEvaluationStrategy;
    private final PricingAdapter pricingAdapter;
    private MultiLevelPricingSubsystem exceptions;
    private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");
    
    private MultiLevelPricingSubsystem getExceptions() {
        if (exceptions == null && IS_WINDOWS) {
            try {
                exceptions = MultiLevelPricingSubsystem.INSTANCE;
            } catch (Exception e) {
                // Windows Event Viewer initialization failed
                exceptions = null;
            }
        }
        return exceptions;
    }

    public CustomerTierEngine(IOrderService orderService, PricingAdapter pricingAdapter) {
        this(orderService, new SpendBasedTierEvaluationStrategy(), pricingAdapter);
    }

    public CustomerTierEngine(IOrderService orderService, TierEvaluationStrategy tierEvaluationStrategy, PricingAdapter pricingAdapter) {
        this.orderService = Objects.requireNonNull(orderService, "orderService cannot be null");
        this.tierEvaluationStrategy = Objects.requireNonNull(tierEvaluationStrategy, "tierEvaluationStrategy cannot be null");
        this.pricingAdapter = Objects.requireNonNull(pricingAdapter, "pricingAdapter cannot be null");
    }


    public CustomerTier getTier(String customerId) {
        String normalizedCustomerId = ValidationUtils.requireNonBlank(customerId, "customerId");
        
        // Check cached tier from database
        var cached = pricingAdapter.getCustomerTierCache(normalizedCustomerId);
        if (cached.isPresent()) {
            return CustomerTier.valueOf(cached.get().tier());
        }
        
        return CustomerTier.STANDARD;
    }

    @Override
    public double getDiscountRate(String customerId) {
        return getTier(customerId).getDiscountRate();
    }

    @Override
    public void evaluateTier(String customerId) {
        String normalizedCustomerId = ValidationUtils.requireNonBlank(customerId, "customerId");

        if (normalizedCustomerId.startsWith("UNKNOWN")) {
            pricingAdapter.createCustomerTierCache(
                new CustomerTierCache(normalizedCustomerId, CustomerTier.STANDARD.name(), Instant.now())
            );
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
            pricingAdapter.createCustomerTierCache(
                new CustomerTierCache(normalizedCustomerId, CustomerTier.STANDARD.name(), Instant.now())
            );
            return;
        } catch (ExecutionException | TimeoutException e) {
            try {
                if (getExceptions() != null) {
                    exceptions.onExternalDataTimeout("OrderService", (int) (EXTERNAL_FETCH_TIMEOUT_SECONDS * 1000));
                }
            } catch (Exception ex) {
                // Windows Event Viewer not available on Linux
            }
            pricingAdapter.createCustomerTierCache(
                new CustomerTierCache(normalizedCustomerId, CustomerTier.STANDARD.name(), Instant.now())
            );
            return;
        }

        CustomerTier evaluatedTier = tierEvaluationStrategy.evaluate(
                normalizedCustomerId,
                annualSpend,
                annualOrderCount
        );

        pricingAdapter.createCustomerTierCache(
            new CustomerTierCache(normalizedCustomerId, evaluatedTier.name(), Instant.now())
        );
    }

    @Override
    public void overrideTier(String customerId, CustomerTier tier) {
        String normalizedCustomerId = ValidationUtils.requireNonBlank(customerId, "customerId");
        Objects.requireNonNull(tier, "tier cannot be null");
        
        // Override the tier in cache
        pricingAdapter.createCustomerTierCache(
            new CustomerTierCache(normalizedCustomerId, tier.name(), Instant.now())
        );
    }
}
