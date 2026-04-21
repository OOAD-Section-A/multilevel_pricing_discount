# Database Module Requirements - Pricing & Discount Team

**Date:** April 20, 2026  
**Team:** Multilevel Pricing & Discount Team  
**Status:** 🔴 BLOCKING - Cannot proceed with migration until these features are provided  
**Contact:** Database Team

---

## Executive Summary

The Pricing & Discount subsystem needs **23 additional methods** and **8 new model classes** in the database module to complete migration from our custom DAOs. Currently, the `PricingAdapter` and `PricingSubsystemFacade` only provide **CREATE operations** but lack **READ, UPDATE, and DELETE** functionality essential for our operations.

**Critical Blockers:**
- ❌ Cannot read rebate programs created by current code
- ❌ Cannot read bundle promotions from database
- ❌ Cannot read/update volume discount schedules
- ❌ No pagination or filtering support
- ❌ Zero update/delete operations
- ❌ Exception handlers not integrated

---

## Current State vs. Required

### Current PricingAdapter Capabilities (Only Create Operations)
```java
public PricingAdapter {
    public void publishPrice(PriceList priceList)
    public void createTierDefinition(TierDefinition tierDefinition)
    public void createCustomerSegmentation(CustomerSegmentation segmentation)
    public void createPriceConfiguration(PriceConfiguration priceConfiguration)
    public void createDiscountRuleResult(DiscountRuleResult discountRuleResult)
    public void createPromotion(Promotion promotion)
    public void createDiscountPolicy(DiscountPolicy discountPolicy)
    public void createContractPricing(ContractPricing contractPricing)
    public void createPriceApproval(PriceApproval priceApproval)
}
```

**Total: 9 methods**  
**Status: INCOMPLETE - Only 33% of required functionality**

---

## Part 1: Missing Model Classes

### Location
File: `src/main/java/com/jackfruit/scm/database/model/PricingModels.java`

### 8 Missing Record Models

#### 1. RebateProgram
```java
public record RebateProgram(
    String programId,           // VARCHAR(100) - PRIMARY KEY
    String customerId,          // VARCHAR(100)
    String skuId,               // VARCHAR(100)
    BigDecimal targetSpend,     // DECIMAL(19,4)
    BigDecimal accumulatedSpend,// DECIMAL(19,4)
    BigDecimal rebatePct        // DECIMAL(5,4) - 0 to 1
) {}
```
**Database Table:** `rebate_programs`

#### 2. BundlePromotion
```java
public record BundlePromotion(
    String promoId,             // VARCHAR(50) - PRIMARY KEY
    String promoName,           // VARCHAR(200)
    BigDecimal discountPct,     // DECIMAL(5,4) - 0 to 1
    LocalDate startDate,        // DATE
    LocalDate endDate,          // DATE
    boolean expired             // BOOLEAN
) {}
```
**Database Table:** `bundle_promotions`

#### 3. BundlePromotionSku
```java
public record BundlePromotionSku(
    long id,                    // BIGINT - PRIMARY KEY AUTO_INCREMENT
    String promoId,             // VARCHAR(50) - FK
    String skuId                // VARCHAR(100)
) {}
```
**Database Table:** `bundle_promotion_skus`

#### 4. VolumeDiscountSchedule
```java
public record VolumeDiscountSchedule(
    String scheduleId,          // VARCHAR(50) - PRIMARY KEY
    String skuId                // VARCHAR(100)
) {}
```
**Database Table:** `volume_discount_schedules`

#### 5. VolumeTierRule
```java
public record VolumeTierRule(
    long id,                    // BIGINT - PRIMARY KEY AUTO_INCREMENT
    String scheduleId,          // VARCHAR(50) - FK
    int minQty,                 // INT
    int maxQty,                 // INT
    BigDecimal discountPct      // DECIMAL(5,4) - 0 to 1
) {}
```
**Database Table:** `volume_tier_rules`

#### 6. CustomerTierCache
```java
public record CustomerTierCache(
    String customerId,          // VARCHAR(100) - PRIMARY KEY
    String tier,                // VARCHAR(20)
    Instant evaluatedAt         // TIMESTAMP
) {}
```
**Database Table:** `customer_tier_cache`

#### 7. CustomerTierOverride
```java
public record CustomerTierOverride(
    String customerId,          // VARCHAR(100) - PRIMARY KEY
    String overrideTier,        // VARCHAR(20)
    Instant overrideSetAt       // TIMESTAMP
) {}
```
**Database Table:** `customer_tier_overrides`

#### 8. RegionalPricingMultiplier
```java
public record RegionalPricingMultiplier(
    String regionCode,          // VARCHAR(20) - PRIMARY KEY
    BigDecimal multiplier       // DECIMAL(6,4) - must be > 0
) {}
```
**Database Table:** `regional_pricing_multipliers`

---

## Part 2: Missing CREATE Operations in PricingAdapter

### Location
File: `src/main/java/com/jackfruit/scm/database/adapter/PricingAdapter.java`

### 8 Missing Create Methods

```java
// ==================== REBATE PROGRAMS ====================
public void createRebateProgram(RebateProgram rebateProgram) {
    // INSERT INTO rebate_programs (...) VALUES (...)
}

// ==================== BUNDLE PROMOTIONS ====================
public void createBundlePromotion(BundlePromotion bundlePromotion) {
    // INSERT INTO bundle_promotions (...) VALUES (...)
}

public void createBundlePromotionSku(BundlePromotionSku bundlePromotionSku) {
    // INSERT INTO bundle_promotion_skus (...) VALUES (...)
}

// ==================== VOLUME DISCOUNTS ====================
public void createVolumeDiscountSchedule(VolumeDiscountSchedule schedule) {
    // INSERT INTO volume_discount_schedules (...) VALUES (...)
}

public void createVolumeTierRule(VolumeTierRule tierRule) {
    // INSERT INTO volume_tier_rules (...) VALUES (...)
}

// ==================== CUSTOMER TIER MANAGEMENT ====================
public void createCustomerTierCache(CustomerTierCache tierCache) {
    // INSERT INTO customer_tier_cache (...) VALUES (...)
}

public void createCustomerTierOverride(CustomerTierOverride tierOverride) {
    // INSERT INTO customer_tier_overrides (...) VALUES (...)
}

// ==================== REGIONAL PRICING ====================
public void createRegionalPricingMultiplier(RegionalPricingMultiplier multiplier) {
    // INSERT INTO regional_pricing_multipliers (...) VALUES (...)
}
```

**Priority:** 🔴 **CRITICAL** - Blocks all create operations for 6 subsystem features

---

## Part 3: Missing READ Operations (Single/By ID)

### Location
File: `src/main/java/com/jackfruit/scm/database/adapter/PricingAdapter.java`

### 10 Missing Single Read Methods

```java
// ==================== READ BY ID/KEY ====================

public Optional<PriceList> getPrice(String priceId) {
    // SELECT * FROM price_list WHERE price_id = ?
    // Returns: Optional<PriceList>
}

public Optional<TierDefinition> getTierDefinition(int tierId) {
    // SELECT * FROM tier_definitions WHERE tier_id = ?
    // Returns: Optional<TierDefinition>
}

public Optional<CustomerSegmentation> getCustomerSegmentation(String customerId) {
    // SELECT * FROM customer_segmentation WHERE customer_id = ?
    // Returns: Optional<CustomerSegmentation>
}

public Optional<PriceConfiguration> getPriceConfiguration(String priceConfigId) {
    // SELECT * FROM price_configuration WHERE price_config_id = ?
    // Returns: Optional<PriceConfiguration>
}

public Optional<Promotion> getPromotion(String promoId) {
    // SELECT * FROM promotions WHERE promo_id = ?
    // Returns: Optional<Promotion>
}

public Optional<Promotion> getPromotionByCouponCode(String couponCode) {
    // SELECT * FROM promotions WHERE coupon_code = ?
    // Returns: Optional<Promotion> - UNIQUE KEY lookup
}

public Optional<DiscountPolicy> getDiscountPolicy(String policyId) {
    // SELECT * FROM discount_policies WHERE policy_id = ?
    // Returns: Optional<DiscountPolicy>
}

public Optional<ContractPricing> getContractPricing(String contractId) {
    // SELECT * FROM contract_pricing WHERE contract_id = ?
    // Returns: Optional<ContractPricing>
}

public Optional<PriceApproval> getPriceApproval(String approvalId) {
    // SELECT * FROM price_approvals WHERE approval_id = ?
    // Returns: Optional<PriceApproval>
}

// ==================== REBATE & VOLUME ====================

public Optional<RebateProgram> getRebateProgram(String programId) {
    // SELECT * FROM rebate_programs WHERE program_id = ?
    // Returns: Optional<RebateProgram>
}

public Optional<VolumeDiscountSchedule> getVolumeDiscountSchedule(String scheduleId) {
    // SELECT * FROM volume_discount_schedules WHERE schedule_id = ?
    // Returns: Optional<VolumeDiscountSchedule>
}

public Optional<BundlePromotion> getBundlePromotion(String promoId) {
    // SELECT * FROM bundle_promotions WHERE promo_id = ?
    // Returns: Optional<BundlePromotion>
}

public Optional<CustomerTierCache> getCustomerTierCache(String customerId) {
    // SELECT * FROM customer_tier_cache WHERE customer_id = ?
    // Returns: Optional<CustomerTierCache>
}

public Optional<RegionalPricingMultiplier> getRegionalMultiplier(String regionCode) {
    // SELECT * FROM regional_pricing_multipliers WHERE region_code = ?
    // Returns: Optional<RegionalPricingMultiplier>
}
```

**Priority:** 🔴 **CRITICAL** - Required for all read operations in our subsystem  
**Impact:** Cannot fetch existing data; breaks all workflows requiring lookups

---

## Part 4: Missing READ Operations (List/Filter)

### Location
File: `src/main/java/com/jackfruit/scm/database/adapter/PricingAdapter.java`

### 9 Missing List/Filter Methods

```java
// ==================== PRICE LIST QUERIES ====================

public List<PriceList> getPricesBySku(String skuId) {
    // SELECT * FROM price_list WHERE sku_id = ?
    // Returns: List of all price versions for a SKU
}

public List<PriceList> getPricesByRegion(String regionCode) {
    // SELECT * FROM price_list WHERE region_code = ?
    // Returns: List of all prices in a region
}

public List<PriceList> getActivePrices() {
    // SELECT * FROM price_list WHERE status = 'ACTIVE'
    // Returns: Currently active prices only
}

// ==================== PROMOTION QUERIES ====================

public List<Promotion> listActivePromotions() {
    // SELECT * FROM promotions 
    // WHERE start_date <= NOW() AND end_date > NOW() AND expired = FALSE
    // Returns: Only currently active promotions
}

public List<Promotion> listPromotionsBySku(String skuId) {
    // SELECT p.* FROM promotions p
    // JOIN promotion_eligible_skus pes ON p.promo_id = pes.promo_id
    // WHERE pes.sku_id = ?
    // Returns: All promotions applicable to a SKU
}

public List<Promotion> listExpiredPromotions() {
    // SELECT * FROM promotions WHERE end_date < NOW() OR expired = TRUE
    // Returns: All expired promotions
}

// ==================== APPROVAL QUERIES ====================

public List<PriceApproval> listPendingApprovals() {
    // SELECT * FROM price_approvals WHERE approval_status = 'PENDING'
    // Returns: Approvals awaiting action
}

public List<PriceApproval> listApprovalsByStatus(String status) {
    // SELECT * FROM price_approvals WHERE approval_status = ?
    // Returns: Approvals filtered by status (PENDING, APPROVED, REJECTED, ESCALATED)
}

public List<PriceApproval> listApprovalsByRequestedBy(String employeeId) {
    // SELECT * FROM price_approvals WHERE requested_by = ?
    // Returns: All approvals requested by an employee
}

// ==================== REBATE & VOLUME QUERIES ====================

public List<RebateProgram> listRebateProgramsByCustomer(String customerId) {
    // SELECT * FROM rebate_programs WHERE customer_id = ?
    // Returns: All active rebate programs for a customer
}

public List<RebateProgram> listRebateProgramsBySku(String skuId) {
    // SELECT * FROM rebate_programs WHERE sku_id = ?
    // Returns: All rebate programs for a product
}

public List<VolumeDiscountSchedule> listVolumeDiscountSchedules() {
    // SELECT * FROM volume_discount_schedules
    // Returns: All volume discount schedules
}

public List<VolumeTierRule> getVolumeTierRules(String scheduleId) {
    // SELECT * FROM volume_tier_rules WHERE schedule_id = ? ORDER BY min_qty ASC
    // Returns: Tier rules for a volume schedule (ordered by quantity)
}

// ==================== TIER MANAGEMENT QUERIES ====================

public List<TierDefinition> listAllTierDefinitions() {
    // SELECT * FROM tier_definitions ORDER BY tier_id ASC
    // Returns: All tier definitions
}

public List<CustomerSegmentation> listCustomersInTier(int tierId) {
    // SELECT * FROM customer_segmentation WHERE assigned_tier_id = ?
    // Returns: All customers in a specific tier
}
```

**Priority:** 🟠 **HIGH** - Required for filtering and business logic  
**Impact:** Cannot perform searches, reports, or tier-based operations

---

## Part 5: Missing UPDATE Operations

### Location
File: `src/main/java/com/jackfruit/scm/database/adapter/PricingAdapter.java`

### 9 Missing Update Methods

```java
// ==================== PRICE UPDATES ====================

public void updatePriceStatus(String priceId, String status) {
    // UPDATE price_list SET status = ? WHERE price_id = ?
    // Status values: DRAFT, ACTIVE, EXPIRED, ARCHIVED
}

// ==================== TIER & CUSTOMER UPDATES ====================

public void updateTierDefinition(TierDefinition tierDefinition) {
    // UPDATE tier_definitions
    // SET tier_name=?, min_volume=?, max_volume=?, discount_pct=?
    // WHERE tier_id = ?
}

public void updateCustomerSegmentation(CustomerSegmentation segmentation) {
    // UPDATE customer_segmentation
    // SET assigned_tier_id=?, segment_value=?, last_reassessed=NOW()
    // WHERE customer_id = ?
}

// ==================== PROMOTION UPDATES ====================

public void updatePromotion(Promotion promotion) {
    // UPDATE promotions SET promo_name=?, discount_value=?, start_date=?, 
    // end_date=?, eligible_sku_ids=?, min_cart_value=?, max_uses=?, expired=?
    // WHERE promo_id = ?
}

public void updatePromotionUseCount(String promoId, int newCount) {
    // UPDATE promotions SET current_use_count = ? WHERE promo_id = ?
    // Atomic operation to track redemptions
}

public void updatePromotionExpired(String promoId, boolean expired) {
    // UPDATE promotions SET expired = ? WHERE promo_id = ?
}

// ==================== APPROVAL STATUS UPDATES ====================

public void updatePriceApprovalStatus(String approvalId, String newStatus) {
    // UPDATE price_approvals 
    // SET approval_status = ?, approval_timestamp = NOW()
    // WHERE approval_id = ?
    // Status values: PENDING, APPROVED, REJECTED, ESCALATED
}

public void updatePriceApprovalManager(String approvalId, String managerId) {
    // UPDATE price_approvals SET approving_manager_id = ? WHERE approval_id = ?
}

// ==================== REBATE PROGRAM UPDATES ====================

public void updateRebateAccumulatedSpend(String programId, BigDecimal newAmount) {
    // UPDATE rebate_programs SET accumulated_spend = ? WHERE program_id = ?
}

// ==================== CUSTOMER TIER CACHE UPDATES ====================

public void updateCustomerTierCache(CustomerTierCache tierCache) {
    // UPDATE customer_tier_cache 
    // SET tier = ?, evaluated_at = NOW()
    // WHERE customer_id = ?
}

public void updateCustomerTierOverride(CustomerTierOverride tierOverride) {
    // UPDATE customer_tier_overrides
    // SET override_tier = ?, override_set_at = NOW()
    // WHERE customer_id = ?
}

// ==================== PRICE CONFIGURATION UPDATES ====================

public void updatePriceConfiguration(PriceConfiguration priceConfiguration) {
    // UPDATE price_configuration
    // SET cogs_value=?, desired_margin_pct=?, computed_base_price=?
    // WHERE price_config_id = ?
}

// ==================== DISCOUNT RULE RESULTS UPDATES ====================

public void updateDiscountRuleResult(DiscountRuleResult discountRuleResult) {
    // UPDATE discount_rule_results
    // SET final_price=?, applied_discount_pct=?, discount_breakdown=?
    // WHERE order_line_id = ?
}
```

**Priority:** 🟠 **HIGH** - Required for state management  
**Impact:** Cannot track promotion usage, approval status, or rebate spending

---

## Part 6: Missing DELETE Operations (REQUIRED)

### Location
File: `src/main/java/com/jackfruit/scm/database/adapter/PricingAdapter.java`

### Required Delete Methods

```java
// ==================== DELETE OPERATIONS ====================

public void deletePromotion(String promoId) {
    // DELETE FROM promotions WHERE promo_id = ?
    // DELETE FROM promotion_eligible_skus WHERE promo_id = ?
    // IMPORTANT: Should use transaction to cascade delete related promotion_eligible_skus
    // Business Use Case: Admins need to remove invalid/incorrect promotions
}

public void deletePrice(String priceId) {
    // DELETE FROM price_list WHERE price_id = ?
    // Business Use Case: Remove incorrect price entries before effective_to date
}

public void deleteContractPricing(String contractId) {
    // DELETE FROM contract_pricing WHERE contract_id = ?
    // Business Use Case: Cancel contract pricing if terms change
}

public void deleteVolumeDiscountSchedule(String scheduleId) {
    // DELETE FROM volume_discount_schedules WHERE schedule_id = ?
    // DELETE FROM volume_tier_rules WHERE schedule_id = ?
    // IMPORTANT: Should use transaction and cascade delete volume_tier_rules
    // Business Use Case: Discontinue volume discount tiers for product
}

public void deleteBundlePromotion(String promoId) {
    // DELETE FROM bundle_promotions WHERE promo_id = ?
    // DELETE FROM bundle_promotion_skus WHERE promo_id = ?
    // IMPORTANT: Should use transaction and cascade delete bundle_promotion_skus
    // Business Use Case: Cancel bundle offers before end_date
}

public void deleteRebateProgram(String programId) {
    // DELETE FROM rebate_programs WHERE program_id = ?
    // Business Use Case: Terminate rebate program for customer-SKU combination
}
```

**Priority:** 🟠 **HIGH** - Required for production operations  
**Note:** ALL delete operations MUST use transactions to ensure cascading deletes work atomically  
**CRITICAL:** deletePromotion() is ESSENTIAL - admins frequently need to remove invalid promotions

---

## Part 7: Exception Handling Integration

### Location
File: `src/main/java/com/jackfruit/scm/database/facade/subsystem/PricingSubsystemFacade.java`

### Current State
- **Status:** ❌ No exception handler integration
- **Issue:** All SQL errors thrown as generic RuntimeException
- **Missing:** DatabaseDesignSubsystem calls

### Required Integration

The PricingSubsystemFacade must integrate with `DatabaseDesignSubsystem` from `scm-exception-handler-v3.jar` for all 32 exception types.

```java
import com.scm.subsystems.DatabaseDesignSubsystem;

public class PricingSubsystemFacade {
    private final DatabaseDesignSubsystem exceptions = DatabaseDesignSubsystem.INSTANCE;
    
    // All methods must wrap database calls with try-catch
    // and call appropriate handler methods:
    
    public void createPromotion(Promotion promotion) {
        try {
            jdbcOperations.update(sql, statement -> {...});
        } catch (SQLException e) {
            if (e.getMessage().contains("Duplicate entry")) {
                exceptions.onDuplicatePrimaryKey("Promotion", promotion.promoId());  // ID 301
            } else if (e.getMessage().contains("foreign key")) {
                exceptions.onForeignKeyViolation("promotions", "related_table", "key");  // ID 302
            }
            // ... handle 30 more exception types
            throw new IllegalStateException("Failed to create promotion", e);
        }
    }
}
```

### 32 Exception Types to Handle
See EXCEPTION_HANDLING_IMPLEMENTATION.md in database module for complete list.

**Priority:** 🟠 **HIGH** - Required for production error handling  
**Impact:** Errors not properly notified to system or users

---

## Part 8: Summary Table - What's Missing

| Category | Item | Type | Status | Priority |
|----------|------|------|--------|----------|
| **Models** | RebateProgram | Record | ❌ Missing | 🔴 CRITICAL |
| **Models** | BundlePromotion | Record | ❌ Missing | 🔴 CRITICAL |
| **Models** | BundlePromotionSku | Record | ❌ Missing | 🔴 CRITICAL |
| **Models** | VolumeDiscountSchedule | Record | ❌ Missing | 🔴 CRITICAL |
| **Models** | VolumeTierRule | Record | ❌ Missing | 🔴 CRITICAL |
| **Models** | CustomerTierCache | Record | ❌ Missing | 🔴 CRITICAL |
| **Models** | CustomerTierOverride | Record | ❌ Missing | 🔴 CRITICAL |
| **Models** | RegionalPricingMultiplier | Record | ❌ Missing | 🔴 CRITICAL |
| **CREATE** | createRebateProgram | Method | ❌ Missing | 🔴 CRITICAL |
| **CREATE** | createBundlePromotion | Method | ❌ Missing | 🔴 CRITICAL |
| **CREATE** | createBundlePromotionSku | Method | ❌ Missing | 🔴 CRITICAL |
| **CREATE** | createVolumeDiscountSchedule | Method | ❌ Missing | 🔴 CRITICAL |
| **CREATE** | createVolumeTierRule | Method | ❌ Missing | 🔴 CRITICAL |
| **CREATE** | createCustomerTierCache | Method | ❌ Missing | 🔴 CRITICAL |
| **CREATE** | createCustomerTierOverride | Method | ❌ Missing | 🔴 CRITICAL |
| **CREATE** | createRegionalPricingMultiplier | Method | ❌ Missing | 🔴 CRITICAL |
| **READ** | getPrice | Method | ❌ Missing | 🔴 CRITICAL |
| **READ** | getTierDefinition | Method | ❌ Missing | 🔴 CRITICAL |
| **READ** | getCustomerSegmentation | Method | ❌ Missing | 🔴 CRITICAL |
| **READ** | getPriceConfiguration | Method | ❌ Missing | 🔴 CRITICAL |
| **READ** | getPromotion | Method | ❌ Missing | 🔴 CRITICAL |
| **READ** | getPromotionByCouponCode | Method | ❌ Missing | 🔴 CRITICAL |
| **READ** | getDiscountPolicy | Method | ❌ Missing | 🔴 CRITICAL |
| **READ** | getContractPricing | Method | ❌ Missing | 🔴 CRITICAL |
| **READ** | getPriceApproval | Method | ❌ Missing | 🔴 CRITICAL |
| **READ** | getRebateProgram | Method | ❌ Missing | 🔴 CRITICAL |
| **READ** | getVolumeDiscountSchedule | Method | ❌ Missing | 🔴 CRITICAL |
| **READ** | getBundlePromotion | Method | ❌ Missing | 🔴 CRITICAL |
| **LIST** | listActivePromotions | Method | ❌ Missing | 🟠 HIGH |
| **LIST** | listPromotionsBySku | Method | ❌ Missing | 🟠 HIGH |
| **LIST** | listPricesByRegion | Method | ❌ Missing | 🟠 HIGH |
| **LIST** | listPendingApprovals | Method | ❌ Missing | 🟠 HIGH |
| **LIST** | listRebateProgramsByCustomer | Method | ❌ Missing | 🟠 HIGH |
| **LIST** | listVolumeDiscountSchedules | Method | ❌ Missing | 🟠 HIGH |
| **UPDATE** | updatePriceStatus | Method | ❌ Missing | 🟠 HIGH |
| **UPDATE** | updateTierDefinition | Method | ❌ Missing | 🟠 HIGH |
| **UPDATE** | updateCustomerSegmentation | Method | ❌ Missing | 🟠 HIGH |
| **UPDATE** | updatePromotion | Method | ❌ Missing | 🟠 HIGH |
| **UPDATE** | updatePromotionUseCount | Method | ❌ Missing | 🟠 HIGH |
| **UPDATE** | updatePriceApprovalStatus | Method | ❌ Missing | 🟠 HIGH |
| **UPDATE** | updateRebateAccumulatedSpend | Method | ❌ Missing | 🟠 HIGH |
| **UPDATE** | updateCustomerTierCache | Method | ❌ Missing | 🟠 HIGH |
| **DELETE** | deletePromotion | Method | ❌ Missing | 🔴 CRITICAL |
| **DELETE** | deletePrice | Method | ❌ Missing | 🟠 HIGH |
| **DELETE** | deleteContractPricing | Method | ❌ Missing | 🟠 HIGH |
| **DELETE** | deleteVolumeDiscountSchedule | Method | ❌ Missing | 🟠 HIGH |
| **DELETE** | deleteBundlePromotion | Method | ❌ Missing | 🟠 HIGH |
| **DELETE** | deleteRebateProgram | Method | ❌ Missing | 🟠 HIGH |
| **EXCEPTION** | Exception Handler Integration | Integration | ❌ Missing | 🟠 HIGH |

**Total Missing:**
- 8 Model Classes
- 8 CREATE Methods
- 14 READ Methods (Single + List)
- 9 UPDATE Methods
- 6 DELETE Methods (including CRITICAL deletePromotion)
- 1 Exception Handling Integration
- **= 46 Total Items**

---

## Part 9: Current Workaround (Temporary)

Until the database team provides these features, the pricing team is using:

```java
// CURRENT WORKAROUND - In-Memory Mock Storage
public class DaoBulk {
    public static class BundleDao {
        private static final Map<String, Object> mockMap = new HashMap<>();  // ❌ NOT PERSISTED
        public static void save(Object promo) { mockMap.put(...); }
    }
    
    public static class RebateDao {
        private static final Map<String, Object> mockMap = new HashMap<>();  // ❌ NOT PERSISTED
        public static void save(Object p) { mockMap.put(...); }
    }
    
    public static class VolumeDao {
        private static final Map<String, Object> mockMap = new HashMap<>();  // ❌ NOT PERSISTED
        public static void save(String sku, Object sched) { mockMap.put(sku, sched); }
    }
}
```

**Problem:** Data is NOT persisted to database. Lost on restart.

---

## Part 10: Additional Requirements

### A. Transaction Support Required
- All batch operations should support transactions
- Rollback capability for failed operations

### B. Pagination Support (Optional but Recommended)
```java
public List<Promotion> listPromotionsPageable(int pageNumber, int pageSize) {
    // SELECT * FROM promotions LIMIT ?, ?
}
```

### C. Bulk Operations (Optional but Recommended)
```java
public void createPromotionsBulk(List<Promotion> promotions) {
    // Batch insert for performance
}
```

### D. Timestamp Management
- All create operations should use database NOW()
- All update operations should use database NOW()
- No client-side timestamp manipulation

---

## Timeline & Urgency

| Phase | Item | Timeline |
|-------|------|----------|
| **Phase 1** | Model Classes (8) | Week 1 |
| **Phase 2** | CREATE Methods (8) | Week 1 |
| **Phase 3** | READ Methods (14) | Week 2 |
| **Phase 4** | UPDATE Methods (6) | Week 2 |
| **Phase 5** | DELETE Methods (6) - esp. **deletePromotion()** CRITICAL | Week 2 |
| **Phase 6** | Exception Integration (1) | Week 3 |

**Blocking:** Cannot proceed with GUI migration or automated tests until Phase 1-2 complete

**URGENT:** `deletePromotion()` needed by end of Week 2 for production admin operations

---

## Communication Template

**Email to Database Team:**

> **Subject:** URGENT - Missing PricingAdapter Features - 46 Methods & 8 Models Required
>
> Hello Database Team,
>
> The Pricing & Discount subsystem requires the following additions to proceed with database module migration:
>
> **Blockers (Week 1):**
> - [ ] 8 Model Classes (RebateProgram, BundlePromotion, VolumeDiscountSchedule, etc.)
> - [ ] 8 CREATE Methods in PricingAdapter
>
> **High Priority (Week 2):**
> - [ ] 14 READ Methods (Single + List/Filter operations)
> - [ ] 9 UPDATE Methods (including price status, tier definitions, customer segmentation)
> - [ ] 6 DELETE Methods (ESPECIALLY **deletePromotion()** - CRITICAL for production)
>
> **Additional (Week 3):**
> - [ ] Exception Handler Integration (DatabaseDesignSubsystem calls)
>
> **CRITICAL NOTE:**
> `deletePromotion()` is ESSENTIAL for business operations - admins frequently need to remove invalid/incorrect promotions immediately after creation. This must use transactions to cascade delete related `promotion_eligible_skus` records atomically.
>
> Detailed specification attached in DATABASE_TEAM_REQUIREMENTS.md
>
> Current status: Using in-memory mock storage (BundleDao.mockMap, RebateDao.mockMap, VolumeDao.mockMap) - NOT PERSISTED to database.
>
> Please confirm ETA.
>
> Thanks,  
> Pricing & Discount Team

---

## Checklist for Verification

Once Database Team Provides Updates, Verify:

- [ ] All 8 model classes compile with correct record definitions
- [ ] All 8 CREATE methods insert records correctly
- [ ] All 14 READ methods return data (not null or empty)
- [ ] All 6 UPDATE methods modify records properly
- [ ] All 6 DELETE methods remove records correctly
- [ ] **deletePromotion() cascades to promotion_eligible_skus** ✓ CRITICAL
- [ ] DELETE operations use transactions (not individual statements)
- [ ] Foreign key relationships work correctly
- [ ] Unique constraints enforced
- [ ] Timestamps use database NOW()
- [ ] Exception handlers called on errors
- [ ] No in-memory mock storage fallback used
- [ ] JAR updated and re-released

---

## Contacts & Escalation

**If not received by:** April 25, 2026

**Escalate to:** Project Management / Architecture Team

---

**Document Version:** 1.0  
**Last Updated:** April 20, 2026  
**Team:** Multilevel Pricing & Discount  
**Status:** 🔴 AWAITING DATABASE TEAM RESPONSE
