package com.pricingos.pricing.facade;

import com.pricingos.common.IContractPricingService;
import com.pricingos.common.ICustomerTierService;
import com.pricingos.common.IPricingFacade;
import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;

/**
 * Structural pattern (Facade): unifies contract pricing and tier discount workflows.
 * GRASP Indirection: clients integrate through this class rather than coupling to many services.
 */
public class PricingFacade implements IPricingFacade {

    private final IContractPricingService contractPricingService;
    private final ICustomerTierService customerTierService;

    public PricingFacade(IContractPricingService contractPricingService, ICustomerTierService customerTierService) {
        this.contractPricingService = Objects.requireNonNull(contractPricingService, "contractPricingService cannot be null");
        this.customerTierService = Objects.requireNonNull(customerTierService, "customerTierService cannot be null");
    }

    @Override
    public String createContract(String customerId, LocalDate startDate, LocalDate endDate, Map<String, Double> skuPrices) {
        String normalizedCustomerId = requireId(customerId, "customerId");
        Objects.requireNonNull(startDate, "startDate cannot be null");
        Objects.requireNonNull(endDate, "endDate cannot be null");
        Objects.requireNonNull(skuPrices, "skuPrices cannot be null");
        return contractPricingService.createContract(normalizedCustomerId, startDate, endDate, skuPrices);
    }

    @Override
    public void evaluateCustomerTier(String customerId) {
        customerTierService.evaluateTier(requireId(customerId, "customerId"));
    }

    @Override
    public double resolveFinalUnitPrice(String customerId, String skuId, double basePrice) {
        String normalizedCustomerId = requireId(customerId, "customerId");
        String normalizedSkuId = requireId(skuId, "skuId");
        if (!Double.isFinite(basePrice) || basePrice < 0.0) {
            throw new IllegalArgumentException("basePrice must be a non-negative finite number");
        }

        double effectiveUnitPrice = contractPricingService
                .getContractPrice(normalizedCustomerId, normalizedSkuId)
                .orElse(basePrice);

        double discountRate = customerTierService.getDiscountRate(normalizedCustomerId);
        if (!Double.isFinite(discountRate) || discountRate < 0.0 || discountRate > 1.0) {
            throw new IllegalStateException("discountRate must be between 0 and 1");
        }

        return effectiveUnitPrice * (1.0 - discountRate);
    }

    private static String requireId(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " cannot be null");
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " cannot be blank");
        }
        return normalized;
    }
}
