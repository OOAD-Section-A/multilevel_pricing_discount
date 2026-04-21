package com.pricingos.pricing.discount;

import com.pricingos.common.IDiscountPolicyService;
import com.pricingos.common.IDiscountRulesEngine;
import com.pricingos.common.IApprovalWorkflowService;
import com.pricingos.common.IContractPricingService;
import com.pricingos.common.IFloorPriceService;
import com.pricingos.common.ILandedCostService;
import com.pricingos.common.OrderLineItem;
import com.pricingos.common.PriceResult;
import com.pricingos.common.PricingOverrideRequest;
import com.pricingos.common.ValidationUtils;
import com.pricingos.pricing.exception.PricingExceptionReporter;
import com.pricingos.pricing.pricelist.IPriceStore;
import com.pricingos.pricing.pricelist.PriceRecord;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Logger;

public class DiscountRulesEngine implements IDiscountRulesEngine {

    private static final Logger LOGGER = Logger.getLogger(DiscountRulesEngine.class.getName());

    private final IPriceStore priceStore;
    private final IDiscountPolicyService policyStore;
    private final IApprovalWorkflowService approvalEngine;
    private final IContractPricingService contractPricingService;
    private final ILandedCostService landedCostService;
    private final IFloorPriceService floorPriceService;
    private final List<IDiscountStrategy> strategies;
    private final int maxStackableDiscounts;

    public DiscountRulesEngine(
            IPriceStore priceStore,
            IDiscountPolicyService policyStore,
            IApprovalWorkflowService approvalEngine,
            IContractPricingService contractPricingService,
            ILandedCostService landedCostService,
            IFloorPriceService floorPriceService,
            List<IDiscountStrategy> strategies,
            int maxStackableDiscounts) {

        this.priceStore = Objects.requireNonNull(priceStore, "priceStore cannot be null");
        this.policyStore = Objects.requireNonNull(policyStore, "policyStore cannot be null");
        this.approvalEngine = Objects.requireNonNull(approvalEngine, "approvalEngine cannot be null");
        this.contractPricingService = Objects.requireNonNull(contractPricingService, "contractPricingService cannot be null");
        this.landedCostService = Objects.requireNonNull(landedCostService, "landedCostService cannot be null");
        this.floorPriceService = Objects.requireNonNull(floorPriceService, "floorPriceService cannot be null");
        this.strategies = List.copyOf(Objects.requireNonNull(strategies, "strategies cannot be null"));
        this.maxStackableDiscounts = ValidationUtils.requireAtLeast(maxStackableDiscounts, 1, "maxStackableDiscounts");
    }

    @Override
    public PriceResult[] calculateFinalPrice(OrderLineItem[] cart, String customerId) {
        Objects.requireNonNull(cart, "cart cannot be null");
        String normalizedCustomerId = ValidationUtils.requireNonBlank(customerId, "customerId");
        List<PriceResult> results = new ArrayList<>();
        for (OrderLineItem item : cart) {
            if (item == null) {
                throw new IllegalArgumentException("cart cannot contain null line items");
            }
            results.add(calculatePriceForLineItem(item, normalizedCustomerId));
        }
        return results.toArray(new PriceResult[0]);
    }

    @Override
    public boolean submitPricingOverride(PricingOverrideRequest request) {
        Objects.requireNonNull(request, "request cannot be null");
        String approvalId = approvalEngine.submitOverrideRequest(
            request.requestedBy(),
            request.requestType(),
            request.orderId(),
            request.requestedDiscountAmount(),
            request.justification()
        );
        return approvalId != null && !approvalId.isBlank();
    }

    private PriceResult calculatePriceForLineItem(OrderLineItem item, String customerId) {
        double basePrice = fetchBasePrice(item);
        if (contractPricingService.hasContractConflict(customerId, item.getSkuId())) {
            PricingExceptionReporter.duplicateContractConflict(customerId, item.getSkuId());
            throw new IllegalStateException(
                "DUPLICATE_CONTRACT_CONFLICT: Multiple active contracts with conflicting prices for customer "
                    + customerId + " and SKU " + item.getSkuId());
        }
        double contractedBase = contractPricingService
            .getContractPrice(customerId, item.getSkuId())
            .orElse(basePrice);
        double adjustedPrice = ValidationUtils.requireFiniteNonNegative(
            landedCostService.applyRegionalPricingAdjustment(
            item.getSkuId(),
            contractedBase,
            item.getRegionCode()
            ),
            "adjustedPrice"
        );

        List<String> appliedDiscounts = new ArrayList<>();
        double currentPrice = adjustedPrice;
        for (IDiscountStrategy strategy : strategies) {
            if (strategy.isEligible(item, customerId)) {
                double newPrice = strategy.applyDiscount(currentPrice, item, customerId);
                if (newPrice < currentPrice) {
                    appliedDiscounts.add(strategy.getStrategyName());
                    currentPrice = newPrice;
                }
            }
        }

        if (!appliedDiscounts.isEmpty() && !policyStore.validateCompliance(appliedDiscounts.toArray(new String[0]))) {
            PricingExceptionReporter.policyStackingConflict(item.getSkuId(), String.join(",", appliedDiscounts));
            currentPrice = selectBestSingleDiscount(item, customerId, adjustedPrice, appliedDiscounts);
        }

        if (appliedDiscounts.size() > maxStackableDiscounts) {
            appliedDiscounts = new ArrayList<>(appliedDiscounts.subList(0, maxStackableDiscounts));
            currentPrice = recomputePrice(item, customerId, appliedDiscounts);
        }

        double floorPrice = floorPriceService.getEffectiveFloorPrice(item.getSkuId());
        if (currentPrice < floorPrice) {
            double calculatedPrice = currentPrice;
            double margin = (currentPrice - floorPrice) / currentPrice;
            PricingExceptionReporter.negativeMarginCalculation(item.getSkuId(), margin);
            LOGGER.warning(() -> "NEGATIVE_MARGIN_CALCULATION prevented for SKU " + item.getSkuId()
                + ": calculated=" + calculatedPrice + ", floor=" + floorPrice + ". Capping to floor.");
            currentPrice = floorPrice;
        }

        return new PriceResult(
            item.getSkuId(),
            contractedBase,
            currentPrice,
            appliedDiscounts.toArray(new String[0]),
            true
        );
    }

    private double fetchBasePrice(OrderLineItem item) {
        PriceRecord priceRecord = priceStore.findActive(item.getSkuId(), item.getRegionCode(), item.getChannel())
            .orElse(null);
        if (priceRecord == null) {
            PricingExceptionReporter.basePriceNotFound(item.getSkuId());
            throw new IllegalArgumentException(
                "BASE_PRICE_NOT_FOUND: SKU " + item.getSkuId()
                    + " in region " + item.getRegionCode()
                    + " for channel " + item.getChannel());
        }
        return ValidationUtils.requireFinitePositive(priceRecord.getBasePrice(), "basePrice");
    }

    private double recomputePrice(OrderLineItem item, String customerId, List<String> retainedDiscounts) {
        Set<String> retained = new HashSet<>(retainedDiscounts);
        double currentPrice = landedCostService.applyRegionalPricingAdjustment(item.getSkuId(), fetchBasePrice(item), item.getRegionCode());
        for (IDiscountStrategy strategy : strategies) {
            if (retained.contains(strategy.getStrategyName()) && strategy.isEligible(item, customerId)) {
                currentPrice = strategy.applyDiscount(currentPrice, item, customerId);
            }
        }
        return currentPrice;
    }

    private double selectBestSingleDiscount(
        OrderLineItem item,
        String customerId,
        double adjustedPrice,
        List<String> appliedDiscounts
    ) {
        double bestPrice = adjustedPrice;
        String bestName = null;
        for (IDiscountStrategy strategy : strategies) {
            if (!strategy.isEligible(item, customerId)) {
                continue;
            }
            double candidate = strategy.applyDiscount(adjustedPrice, item, customerId);
            if (candidate < bestPrice) {
                bestPrice = candidate;
                bestName = strategy.getStrategyName();
            }
        }

        appliedDiscounts.clear();
        if (bestName != null) {
            appliedDiscounts.add(bestName);
        }
        return bestPrice;
    }
}
