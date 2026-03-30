package com.pricingos.pricing.tier;

import com.pricingos.common.CustomerTier;
import com.pricingos.common.ICustomerTierService;
import com.pricingos.common.IOrderService;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class CustomerTierEngine implements ICustomerTierService {
    private final IOrderService orderService;
    private final Map<String, CustomerTier> tierStore = new ConcurrentHashMap<>();
    private final Map<String, CustomerTier> manualOverrides = new ConcurrentHashMap<>();

    private static final double PLATINUM_MIN = 100000;
    private static final double GOLD_MIN = 50000;
    private static final double SILVER_MIN = 10000;

    public CustomerTierEngine(IOrderService orderService){
        this.orderService = Objects.requireNonNull(orderService, "orderService cannot be null");
    }

    @Override
    public CustomerTier getTier(String customerId){
        CustomerTier overriddenTier = manualOverrides.get(customerId);
        if (overriddenTier != null) {
            return overriddenTier;
        }
        return tierStore.getOrDefault(customerId,CustomerTier.STANDARD);
    }

    @Override
    public double getDiscountRate(String customerId){
        return getTier(customerId).getDiscountRate();
    }

    @Override 
    public void evaluateTier(String customerId){
        if(manualOverrides.containsKey(customerId)){
            return;
        }
        
        double spend = orderService.getTotalSpendLastYear(customerId);
        CustomerTier evaluatedTier = resolveTier(spend);
        tierStore.compute(customerId, (id, ignored) -> {
            CustomerTier overriddenTier = manualOverrides.get(id);
            return overriddenTier != null ? overriddenTier : evaluatedTier;
        });
    }

    @Override
    public void overrideTier(String customerId, CustomerTier tier){
        Objects.requireNonNull(tier, "tier cannot be null");
        manualOverrides.put(customerId,tier);
        tierStore.put(customerId,tier);
    }

    private CustomerTier resolveTier(double spend) {
        if(spend>=PLATINUM_MIN){
            return CustomerTier.PLATINUM;
        }
        if(spend>=GOLD_MIN){
            return CustomerTier.GOLD;
        }
        if(spend>=SILVER_MIN){
            return CustomerTier.SILVER;
        }
        return CustomerTier.STANDARD;
    }
}
