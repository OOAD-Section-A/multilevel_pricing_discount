package com.pricingos.pricing.discount;

import com.pricingos.common.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Central orchestrator for calculating final prices after applying all eligible discounts.
 * Implements IDiscountRulesEngine.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Applies all eligible discount strategies in priority order</li>
 *   <li>Enforces discount stacking compliance rules</li>
 *   <li>Validates final prices against minimum floor prices</li>
 *   <li>Submits pricing overrides to the approval workflow</li>
 * </ul>
 *
 * <p>Design patterns: GRASP Controller (single entry point for discount calculations).
 * All dependencies are injected via constructor as interfaces (SOLID DIP).
 */
public class DiscountRulesEngine implements IDiscountRulesEngine {

    private final IPriceStore priceStore;
    private final ICustomerTierService tierService;
    private final IDiscountPolicyService policyStore;
    private final IPromotionService promoManager;
    private final IContractPricingService contractModule;
    private final IApprovalWorkflowService approvalEngine;
    private final ILandedCostService landedCostService;
    private final IFloorPriceService floorPriceService;
    private final List<IDiscountStrategy> strategies;
    private final int maxStackableDiscounts;

    /**
     * Constructs the discount engine with all required dependencies.
     * All services are injected as interfaces, not concrete classes (SOLID DIP).
     *
     * @param priceStore            price lookup service
     * @param tierService           customer tier evaluation service
     * @param policyStore           discount policy compliance service
     * @param promoManager          promotion/coupon management service
     * @param contractModule        contract pricing service
     * @param approvalEngine        approval workflow service
     * @param landedCostService     regional cost adjustment service (from Aniruddha)
     * @param floorPriceService     margin floor protection service
     * @param strategies            list of discount strategies to apply (order matters)
     * @param maxStackableDiscounts maximum number of discounts that can be stacked (default: 3)
     */
    public DiscountRulesEngine(
            IPriceStore priceStore,
            ICustomerTierService tierService,
            IDiscountPolicyService policyStore,
            IPromotionService promoManager,
            IContractPricingService contractModule,
            IApprovalWorkflowService approvalEngine,
            ILandedCostService landedCostService,
            IFloorPriceService floorPriceService,
            List<IDiscountStrategy> strategies,
            int maxStackableDiscounts) {

        this.priceStore = Objects.requireNonNull(priceStore, "priceStore cannot be null");
        this.tierService = Objects.requireNonNull(tierService, "tierService cannot be null");
        this.policyStore = Objects.requireNonNull(policyStore, "policyStore cannot be null");
        this.promoManager = Objects.requireNonNull(promoManager, "promoManager cannot be null");
        this.contractModule = Objects.requireNonNull(contractModule, "contractModule cannot be null");
        this.approvalEngine = Objects.requireNonNull(approvalEngine, "approvalEngine cannot be null");
        this.landedCostService = Objects.requireNonNull(landedCostService, "landedCostService cannot be null");
        this.floorPriceService = Objects.requireNonNull(floorPriceService, "floorPriceService cannot be null");
        this.strategies = Objects.requireNonNull(strategies, "strategies cannot be null");
        if (maxStackableDiscounts < 1)
            throw new IllegalArgumentException("maxStackableDiscounts must be >= 1");
        this.maxStackableDiscounts = maxStackableDiscounts;
    }

    // ── IDiscountRulesEngine implementation ───────────────────────────────────────

    /**
     * {@inheritDoc}
     *
     * <p>For each OrderLineItem in the cart:
     * 1. Fetch base price from price store
     * 2. Apply regional/landed cost adjustments
     * 3. Run each eligible discount strategy
     * 4. Enforce stacking compliance rules
     * 5. Validate against floor price
     * 6. Build and return PriceResult
     *
     * <p>Note: This implementation works with OrderLineItem objects. The interface
     * uses Object for type flexibility, but callers must pass OrderLineItem[] or similar.
     */
    @Override
    public Object calculateFinalPrice(Object cart, String customerId) {
        Objects.requireNonNull(cart, "cart cannot be null");
        Objects.requireNonNull(customerId, "customerId cannot be null");

        if (!(cart instanceof OrderLineItem[])) {
            throw new IllegalArgumentException("cart must be an OrderLineItem[]");
        }

        OrderLineItem[] items = (OrderLineItem[]) cart;
        List<PriceResult> results = new ArrayList<>();

        for (OrderLineItem item : items) {
            PriceResult result = calculatePriceForLineItem(item, customerId);
            results.add(result);
        }

        return results.toArray(new PriceResult[0]);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Delegates to the approval engine to submit a pricing override request.
     */
    @Override
    public boolean submitPricingOverride(Object request) {
        Objects.requireNonNull(request, "request cannot be null");

        // Note: In a real implementation, this would call the approval engine's API.
        // The interface signature uses Object to remain flexible.
        return true; // Placeholder: actual implementation would call approvalEngine.submitRequest()
    }

    // ── Private helper methods ────────────────────────────────────────────────────

    /**
     * Calculates the final price for a single order line item.
     * Applies all eligible strategies, enforces compliance, and validates floor prices.
     *
     * @param item       the order line item
     * @param customerId the customer ID
     * @return PriceResult with final price and applied discounts
     */
    private PriceResult calculatePriceForLineItem(OrderLineItem item, String customerId) {
        // Step 1: Fetch base price from price store
        double basePrice = fetchBasePrice(item);

        // Step 2: Apply regional/landed cost adjustment
        double adjustedPrice = landedCostService.applyRegionalPricingAdjustment(
            item.getSkuId(),
            basePrice,
            item.getRegionCode()
        );

        // Step 3: Apply discount strategies and collect their names
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

        // Step 4: Enforce stacking compliance
        if (!appliedDiscounts.isEmpty()) {
            String[] activePolicies = policyStore.getActivePolicies();
            boolean isCompliant = policyStore.validateCompliance(appliedDiscounts.toArray(new String[0]));
            if (!isCompliant) {
                // Policy violation: keep only the highest discount (EXCLUSIVE overrides others)
                currentPrice = adjustedPrice;
                appliedDiscounts.clear();
                // Reapply only the first (highest priority) eligible strategy
                for (IDiscountStrategy strategy : strategies) {
                    if (strategy.isEligible(item, customerId)) {
                        double newPrice = strategy.applyDiscount(adjustedPrice, item, customerId);
                        appliedDiscounts.add(strategy.getStrategyName());
                        currentPrice = newPrice;
                        break; // Only apply the first strategy for EXCLUSIVE policies
                    }
                }
            }
        }

        // Step 5: Enforce stacking cap
        if (appliedDiscounts.size() > maxStackableDiscounts) {
            // Too many discounts: reduce to the first N strategies
            appliedDiscounts.subList(maxStackableDiscounts, appliedDiscounts.size()).clear();
            // Recalculate price applying only the retained discounts
            currentPrice = recomputePrice(item, customerId, appliedDiscounts);
        }

        // Step 6: Validate against floor price
        double floorPrice = floorPriceService.getEffectiveFloorPrice(item.getSkuId());
        if (currentPrice < floorPrice) {
            // Violation: cap at floor price and log warning
            System.err.println("WARNING: Price " + currentPrice + " below floor " + floorPrice +
                " for SKU " + item.getSkuId() + "; capping at floor.");
            currentPrice = floorPrice;
        }

        // Step 7: Build and return PriceResult
        return new PriceResult(
            item.getSkuId(),
            basePrice,
            currentPrice,
            appliedDiscounts.toArray(new String[0]),
            true // isApproved = true for now; overrides would set this to false initially
        );
    }

    /**
     * Fetches the base price for a SKU from the price store.
     *
     * @param item the order line item
     * @return the base unit price
     * @throws IllegalArgumentException if price is not found
     */
    private double fetchBasePrice(OrderLineItem item) {
        String skuId = item.getSkuId();
        String regionCode = item.getRegionCode();

        // Attempt to fetch from price store (region-specific lookup)
        // Note: The actual implementation depends on PriceStore's API.
        // This is a simplified version using a reasonable convention.

        // For now, we assume a placeholder price; in a real implementation,
        // this would call priceStore.findActive(...) and resolve the price.
        // Placeholder logic:
        double price = 100.0; // Default for testing

        if (price <= 0) {
            throw new IllegalArgumentException(
                "BASE_PRICE_NOT_FOUND: SKU " + skuId + " in region " + regionCode
            );
        }
        return price;
    }

    /**
     * Recomputes the price applying only the specified retained discount strategies.
     * Used when discounts exceed the stacking cap.
     *
     * @param item              the order line item
     * @param customerId        the customer ID
     * @param retainedDiscounts list of discount names to apply
     * @return the recomputed price
     */
    private double recomputePrice(OrderLineItem item, String customerId, List<String> retainedDiscounts) {
        double basePrice = fetchBasePrice(item);
        double currentPrice = landedCostService.applyRegionalPricingAdjustment(
            item.getSkuId(),
            basePrice,
            item.getRegionCode()
        );

        for (IDiscountStrategy strategy : strategies) {
            if (retainedDiscounts.contains(strategy.getStrategyName())) {
                currentPrice = strategy.applyDiscount(currentPrice, item, customerId);
            }
        }
        return currentPrice;
    }
}
