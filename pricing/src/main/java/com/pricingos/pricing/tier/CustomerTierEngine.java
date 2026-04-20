package com.pricingos.pricing.tier;

import com.pricingos.common.CustomerTier;
import com.pricingos.common.ICustomerTierService;
import com.pricingos.common.IOrderService;
import com.pricingos.common.ValidationUtils;
import com.scm.subsystems.MultiLevelPricingSubsystem;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import com.pricingos.pricing.db.TierDao;
public class CustomerTierEngine implements ICustomerTierService {
    private static final long EXTERNAL_FETCH_TIMEOUT_SECONDS = 2L;
    private final IOrderService orderService;
    private final TierEvaluationStrategy tierEvaluationStrategy;
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

    public CustomerTierEngine(IOrderService orderService) {
        this(orderService, new SpendBasedTierEvaluationStrategy());
    }

    public CustomerTierEngine(IOrderService orderService, TierEvaluationStrategy tierEvaluationStrategy) {
        this.orderService = Objects.requireNonNull(orderService, "orderService cannot be null");
        this.tierEvaluationStrategy = Objects.requireNonNull(tierEvaluationStrategy, "tierEvaluationStrategy cannot be null");
    }


    public CustomerTier getTier(String customerId) {
        String normalizedCustomerId = ValidationUtils.requireNonBlank(customerId, "customerId");
        CustomerTier overridden = TierDao.getOverrideTier(normalizedCustomerId);
        if (overridden != null) {
            return overridden;
        }
        CustomerTier evaluated = TierDao.getEvaluatedTier(normalizedCustomerId);
        return evaluated == null ? CustomerTier.STANDARD : evaluated;
    }

    @Override

    public double getDiscountRate(String customerId) {
        return getTier(customerId).getDiscountRate();
    }

    @Override
    public void evaluateTier(String customerId) {
        String normalizedCustomerId = ValidationUtils.requireNonBlank(customerId, "customerId");
        if (TierDao.hasOverride(normalizedCustomerId)) {
            return;
        }

        if (normalizedCustomerId.startsWith("UNKNOWN")) {
            TierDao.saveEvaluatedTier(normalizedCustomerId, CustomerTier.STANDARD);
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
            TierDao.saveEvaluatedTier(normalizedCustomerId, CustomerTier.STANDARD);
            return;
        } catch (ExecutionException | TimeoutException e) {
            try {
                if (getExceptions() != null) {
                    exceptions.onExternalDataTimeout("OrderService", (int) (EXTERNAL_FETCH_TIMEOUT_SECONDS * 1000));
                }
            } catch (Exception ex) {
                // Windows Event Viewer not available on Linux
            }
            TierDao.saveEvaluatedTier(normalizedCustomerId, CustomerTier.STANDARD);
            return;
        }

        CustomerTier evaluatedTier = tierEvaluationStrategy.evaluate(
                normalizedCustomerId,
                annualSpend,
                annualOrderCount
        );

        TierDao.saveEvaluatedTier(normalizedCustomerId, evaluatedTier);
    }

    @Override
    public void overrideTier(String customerId, CustomerTier tier) {
        String normalizedCustomerId = ValidationUtils.requireNonBlank(customerId, "customerId");
        Objects.requireNonNull(tier, "tier cannot be null");
        TierDao.saveOverrideTier(normalizedCustomerId, tier);
        TierDao.saveEvaluatedTier(normalizedCustomerId, tier);
    }
}
