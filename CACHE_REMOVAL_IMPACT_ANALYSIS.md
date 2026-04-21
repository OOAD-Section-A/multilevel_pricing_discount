# Cache Removal Impact Analysis

## Summary of Caches in the Codebase

The application uses **4 main caching mechanisms**. Removing them would have different impacts:

---

## 1. **PriceListManager.activePriceCache** (IN-MEMORY)
- **Type:** `ConcurrentHashMap<String, PriceRecord>`
- **Key Structure:** `SKU|REGION|CHANNEL`
- **Purpose:** Cache active prices to avoid repeated database lookups

### Methods Using This Cache:
```java
getActivePrice()          // Checks cache first, then DB
refreshPriceCache()       // Clears and reloads from in-memory store
updatePrice()             // Updates cache after price change
deletePrice()             // Removes from cache
```

### Impact of Removal:
- ✅ **Minimal database load increase** - Prices are read frequently
- ✅ **Acceptable performance** - priceStore has its own in-memory storage
- ⚠️ **Moderate latency increase** - Every price lookup goes through priceStore→DB chain
- ✅ **No data consistency issues** - Database is still source of truth

### Affected Components:
- `PriceListPanel` (GUI) - Shows active prices
- `PricingCalculatorPanel` (GUI) - Calculates prices
- `ApprovalPanel` (GUI) - Uses prices in approvals
- `DynamicPricingEngine` - Uses prices in market-based adjustments

---

## 2. **VolumeDiscountManager.skuToScheduleId** (IN-MEMORY)
- **Type:** `ConcurrentHashMap<String, String>`
- **Stores:** Mapping of SKU → VolumeDiscountSchedule ID
- **Purpose:** Quick lookup to avoid scanning all discount schedules

### Methods Using This Cache:
```java
createVolumePromotion()   // Adds mapping to cache
getDiscountedUnitPrice()  // Checks cache, falls back to DB scan
hasVolumePromotion()      // Checks cache, falls back to DB scan
```

### Impact of Removal:
- ⚠️ **MODERATE PERFORMANCE DEGRADATION** - Falls back to scanning all schedules
- ✅ **Functional** - Still works with `listVolumeDiscountSchedules()` fallback
- ⚠️ **O(n) vs O(1) lookup** - From constant time to linear time
- ✅ **No data correctness issues** - Database still has complete data

### Affected Components:
- `PricingCalculatorPanel` (GUI) - Applies volume discounts during price calculation
- `DynamicPricingEngine` - Uses volume discounts in pricing simulation
- Any order processing that applies volume tiers

---

## 3. **PromotionManager.promotionCache** (IN-MEMORY)
- **Type:** `ConcurrentHashMap<String, PromotionState>`
- **Key:** Coupon code (normalized to uppercase)
- **Purpose:** Store promotion state (redemption counts, expiry status)
- **⚠️ CRITICAL:** This is the **ONLY place** promotion data is stored!

### Methods Using This Cache:
```java
createPromotion()         // Adds to cache (ONLY storage location)
validateAndGetDiscount()  // Reads from cache
recordRedemption()        // Updates cache (redemption count)
expireStalePromotions()   // Updates cache (expiry status)
getActivePromoCodes()     // Reads from cache
getRedemptionCount()      // Reads from cache
```

### Impact of Removal:
- 🔴 **CRITICAL - ALL PROMOTIONS LOST ON SERVER RESTART**
- 🔴 **Data Loss** - No database persistence (design limitation)
- 🔴 **Redemption tracking lost** - Cannot track how many times coupons used
- 🔴 **No promotion state management** - Expiry tracking fails
- ❌ **Non-functional promotion system** without external persistence

### Affected Components:
- `PromoCodeManagerPanel` (GUI) - Create/manage promotions
- `PriceCalculatorPanel` (GUI) - Apply promotions during calculation
- Any order processing using coupon codes

### Database Integration Gap:
```
COMMENT: "database team's PricingAdapter does not expose promotion persistence API"
         Only way to fix: Ask database team to expose:
         - updatePromotion()
         - updatePromotionRedemptionCount()
         - updatePromotionExpiry()
```

---

## 4. **CustomerTierCache** (DATABASE-BASED)
- **Type:** `CustomerTierCache` record stored in database
- **Scope:** Evaluated customer tier classifications
- **Purpose:** Store computed tier (STANDARD, SILVER, GOLD, PLATINUM)

### Methods Using This Cache:
```java
getTier()                 // Checks DB cache for tier
evaluateTier()            // Evaluates tier and stores in DB cache
overrideTier()            // Creates DB cache with override
```

### Impact of Removal:
- ✅ **Recalculates on every getTier() call**
- ⚠️ **MODERATE PERFORMANCE HIT** - Calls OrderService each time
- ✅ **More accurate tier classification** - Always fresh, never stale
- ✅ **Still respects overrides** - Customer tier overrides remain functional
- ✅ **No data loss** - Just slower calculation

### Affected Components:
- `TierDefinitionsPanel` (GUI) - Shows tier info
- `ApprovalWorkflowEngine` - Uses tiers for approval limits
- Price calculation - Tier affects discount rates

### Implementation Details:
```java
// Current: Read from DB cache if exists
var cached = pricingAdapter.getCustomerTierCache(customerId);
if (cached.isPresent()) {
    return CustomerTier.valueOf(cached.get().tier());
}

// If removed: Call evaluateTier() to recalculate
// Risk: CompletableFuture timeout to OrderService (2 seconds)
```

---

## Summary Table

| Cache | Type | Removal Impact | Critical? | Data Loss? |
|-------|------|----------------|-----------|-----------|
| **activePriceCache** | In-Memory | ✅ Minor - Acceptable | No | No |
| **skuToScheduleId** | In-Memory | ⚠️ Moderate - Performance | No | No |
| **promotionCache** | In-Memory | 🔴 **CRITICAL** | **YES** | **YES** |
| **CustomerTierCache** | Database | ✅ Minor - Recalculates | No | No |

---

## Recommendation

### ✅ Can Safely Remove:
1. **activePriceCache** - priceStore is redundant fallback
2. **skuToScheduleId** - Database scan is acceptable fallback
3. **CustomerTierCache** - Recalculation is acceptable (2s timeout risk)

### 🔴 CANNOT Remove Without Data Loss:
1. **promotionCache** - Need database team to expose promotion persistence API:
   ```
   Required methods:
   - updatePromotion(Promotion)
   - updatePromotionRedemptionCount(String promoId, int count)
   - updatePromotionExpired(String promoId, boolean expired)
   ```

---

## Database Integration Issues

The database module (`PricingAdapter`) doesn't expose these methods:

**❌ Missing:**
```java
public void updatePromotion(Promotion promotion) { }
public void updatePromotionRedemptionCount(String promoId, int count) { }
```

**✅ Already available:**
```java
public void updateCustomerTierCache(CustomerTierCache tierCache) { }
public void updatePriceApprovalStatus(String approvalId, String newStatus) { }
```

---

## Action Items

1. **Short Term:** Keep all caches as-is (especially promotionCache)
2. **Medium Term:** Contact database team to expose promotion persistence:
   - `PROMOTION` table needs UPDATE operations
   - `REDEMPTION_COUNT` field needs updates
   - `EXPIRED` field needs updates
3. **Long Term:** Refactor PromotionManager to use database after persistence API available

---

## Code References

- **PriceListManager:** [pricelist/PriceListManager.java](../../pricing/src/main/java/com/pricingos/pricing/pricelist/PriceListManager.java#L28)
- **VolumeDiscountManager:** [promotion/VolumeDiscountManager.java](../../pricing/src/main/java/com/pricingos/pricing/promotion/VolumeDiscountManager.java#L26)
- **PromotionManager:** [promotion/PromotionManager.java](../../pricing/src/main/java/com/pricingos/pricing/promotion/PromotionManager.java#L28)
- **CustomerTierEngine:** [tier/CustomerTierEngine.java](../../pricing/src/main/java/com/pricingos/pricing/tier/CustomerTierEngine.java#L55)
