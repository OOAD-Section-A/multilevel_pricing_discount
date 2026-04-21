package com.pricingos.pricing.db;

import com.jackfruit.scm.database.model.PricingModels.CustomerTierCache;
import com.pricingos.common.CustomerTier;
import java.time.Instant;

public final class TierDao {

    private static final String OVERRIDE_PREFIX = "OVERRIDE:";

    private TierDao() {
    }

    public static CustomerTier getEvaluatedTier(String customerId) {
        return DatabaseModuleSupport.withPricingAdapter(adapter ->
                adapter.getCustomerTierCache(customerId)
                        .map(CustomerTierCache::tier)
                        .map(TierDao::decodeTier)
                        .orElse(null));
    }

    public static void saveEvaluatedTier(String customerId, CustomerTier tier) {
        DatabaseModuleSupport.usePricingAdapter(adapter -> {
            String storedTier = adapter.getCustomerTierCache(customerId)
                    .map(CustomerTierCache::tier)
                    .filter(TierDao::isOverrideValue)
                    .map(ignored -> encodeOverride(tier))
                    .orElse(tier.name());
            upsertTierCache(adapter, customerId, storedTier);
        });
    }

    public static CustomerTier getOverrideTier(String customerId) {
        return DatabaseModuleSupport.withPricingAdapter(adapter ->
                adapter.getCustomerTierCache(customerId)
                        .map(CustomerTierCache::tier)
                        .filter(TierDao::isOverrideValue)
                        .map(TierDao::decodeTier)
                        .orElse(null));
    }

    public static void saveOverrideTier(String customerId, CustomerTier tier) {
        DatabaseModuleSupport.usePricingAdapter(adapter ->
                upsertTierCache(adapter, customerId, encodeOverride(tier)));
    }

    public static boolean hasOverride(String customerId) {
        return getOverrideTier(customerId) != null;
    }

    private static void upsertTierCache(com.jackfruit.scm.database.adapter.PricingAdapter adapter,
                                        String customerId,
                                        String storedTier) {
        CustomerTierCache tierCache = new CustomerTierCache(customerId, storedTier, Instant.now());
        if (adapter.getCustomerTierCache(customerId).isPresent()) {
            adapter.updateCustomerTierCache(tierCache);
        } else {
            adapter.createCustomerTierCache(tierCache);
        }
    }

    private static boolean isOverrideValue(String storedTier) {
        return storedTier != null && storedTier.startsWith(OVERRIDE_PREFIX);
    }

    private static String encodeOverride(CustomerTier tier) {
        return OVERRIDE_PREFIX + tier.name();
    }

    private static CustomerTier decodeTier(String storedTier) {
        String normalized = storedTier;
        if (isOverrideValue(storedTier)) {
            normalized = storedTier.substring(OVERRIDE_PREFIX.length());
        }
        return CustomerTier.valueOf(normalized);
    }
}
