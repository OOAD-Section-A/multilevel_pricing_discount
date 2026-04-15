package com.pricingos.pricing.discount;

import com.pricingos.common.IPromotionAnalyticsService;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory analytics service tracking promotion performance and redemption patterns.
 * Implements IPromotionAnalyticsService.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Records redemption events when promotions are applied</li>
 *   <li>Aggregates total discount amounts per promotion</li>
 *   <li>Tracks redemption counts per promotion</li>
 *   <li>Segments redemptions by sales channel for channel analytics</li>
 * </ul>
 *
 * <p>Thread safety: all registry maps use ConcurrentHashMap. Increments and aggregations
 * use synchronized access where necessary.
 */
public class PromotionAnalyticsService implements IPromotionAnalyticsService {

    /**
     * Registry of total discount amounts given by promotion.
     * Key: promoId, Value: total discount amount.
     */
    private final Map<String, Double> totalDiscountByPromo = new ConcurrentHashMap<>();

    /**
     * Registry of redemption counts by promotion.
     * Key: promoId, Value: number of times redeemed.
     */
    private final Map<String, Integer> redemptionCountByPromo = new ConcurrentHashMap<>();

    /**
     * Nested map of redemptions segmented by channel.
     * Key: promoId, Value: map of (channel -> total discount on that channel).
     */
    private final Map<String, Map<String, Double>> redemptionsByChannel = new ConcurrentHashMap<>();

    // ── IPromotionAnalyticsService implementation ─────────────────────────────────

    /**
     * {@inheritDoc}
     *
     * <p>Records a redemption event by:
     * 1. Adding the discount amount to the total for this promo
     * 2. Incrementing the redemption count
     * 3. Recording the channel-specific discount
     */
    @Override
    public void recordRedemption(String promoId, String skuId, double discountApplied, String customerId) {
        Objects.requireNonNull(promoId, "promoId cannot be null");
        Objects.requireNonNull(skuId, "skuId cannot be null");
        Objects.requireNonNull(customerId, "customerId cannot be null");

        if (!Double.isFinite(discountApplied) || discountApplied < 0) {
            throw new IllegalArgumentException("discountApplied must be a non-negative finite number");
        }

        // Accumulate total discount
        totalDiscountByPromo.merge(promoId, discountApplied, Double::sum);

        // Increment redemption count
        redemptionCountByPromo.merge(promoId, 1, Integer::sum);

        // For now, record under a generic "DEFAULT" channel since channel info is not passed
        // In a real implementation, extract channel from the context or pass it as a parameter
        String channel = "DEFAULT";
        redemptionsByChannel.computeIfAbsent(promoId, k -> new ConcurrentHashMap<>())
            .merge(channel, discountApplied, Double::sum);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Returns the total cumulative discount issued for a promotion across all redemptions.
     * Returns 0.0 if the promo has not been redeemed or does not exist.
     */
    @Override
    public double getTotalDiscountGiven(String promoId) {
        Objects.requireNonNull(promoId, "promoId cannot be null");
        return totalDiscountByPromo.getOrDefault(promoId, 0.0);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Returns the number of times a promotion has been redeemed.
     * Returns 0 if the promo has not been redeemed or does not exist.
     */
    @Override
    public int getRedemptionCount(String promoId) {
        Objects.requireNonNull(promoId, "promoId cannot be null");
        return redemptionCountByPromo.getOrDefault(promoId, 0);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Returns an unmodifiable map of (channel -> total discount) for the given promotion.
     * Returns an empty map if the promo has not been redeemed or does not exist.
     */
    @Override
    public Map<String, Double> getRedemptionsByChannel(String promoId) {
        Objects.requireNonNull(promoId, "promoId cannot be null");
        Map<String, Double> channelData = redemptionsByChannel.get(promoId);
        return (channelData != null) ? Collections.unmodifiableMap(new HashMap<>(channelData))
                                     : Collections.emptyMap();
    }

    // ── Package-private helpers for testing ──────────────────────────────────────

    /**
     * Returns the total discount registry (package-private for tests).
     *
     * @return copy of the total discount map
     */
    Map<String, Double> getTotalDiscountRegistry() {
        return Map.copyOf(totalDiscountByPromo);
    }

    /**
     * Returns the redemption count registry (package-private for tests).
     *
     * @return copy of the redemption count map
     */
    Map<String, Integer> getRedemptionCountRegistry() {
        return Map.copyOf(redemptionCountByPromo);
    }

    /**
     * Returns the channel analytics registry (package-private for tests).
     *
     * @return copy of the channel redemptions map
     */
    Map<String, Map<String, Double>> getRedemptionsByChannelRegistry() {
        Map<String, Map<String, Double>> copy = new HashMap<>();
        for (Map.Entry<String, Map<String, Double>> entry : redemptionsByChannel.entrySet()) {
            copy.put(entry.getKey(), new HashMap<>(entry.getValue()));
        }
        return copy;
    }
}
