package com.pricingos.pricing.tier;

import com.pricingos.common.*;
import java.util.HashMap;
import java.util.Map;

public class CustomerTierEngine implements ICustomerTierService {
    private final IOrderService orderService;
    private final Map<String, CustomerTier> tierStore = new HashMap<>();
    private final Map<String, Boolean> overrides = new HashMap<>();

    private static final double PLATINUM_MIN = 100000;
    private static final double GOLD_MIN = 50000;
    private static final double SILVER_MIN = 10000;

    public CustomerTierEngine(IOrderService orderService){
        this.orderService = orderService;
    }

    @Override
    public CustomerTier getTier(String customerId){
        return tierStore.getOrDefault(customerId,CustomerTier.STANDARD);
    }

    @Override
    public double getDiscountRate(String customerId){
        return getTier(customerId).getDiscountRate();
    }

    @Override 
    public void evaluateTier(String customerId){
        if(overrides.getOrDefault(customerId,false)){
            return;
        }
        
        double spend = orderService.getTotalSpendLastYear(customerId);
        CustomerTier tier;

        if(spend>=PLATINUM_MIN){
            tier = CustomerTier.PLATINUM;
        }
        else if(spend>=GOLD_MIN){
            tier = CustomerTier.GOLD;
        }
        else if(spend>=SILVER_MIN){
            tier = CustomerTier.SILVER;
        }
        else{
            tier = CustomerTier.STANDARD;
        }
        tierStore.put(customerId,tier);
    }

    @Override
    public void overrideTier(String customerId, CustomerTier tier){
        tierStore.put(customerId,tier);
        overrides.put(customerId,true);
    }
}
