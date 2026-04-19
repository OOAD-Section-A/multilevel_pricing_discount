# Analysis Integration Guide

## Overview

This guide tells the Reporting and Analytics Dashboard team how to integrate two new interface contracts into their subsystem:

- **Requirement 1 — Margin Profitability Tracking**: Interfaces for querying how much revenue was lost to approved discounts and how much margin was saved by rejecting them, over a given time period.
- **Requirement 2 — Customer Tier Distribution**: Interfaces for grouping customers by pricing tier and computing the average discount rate across a customer base.

Both sets of interfaces live in the `common` module (`com.pricingos.common` package) and are shipped as part of the common JAR. Your team provides the implementation — no method bodies exist yet.

---

## Requirement 1 — Margin Profitability Tracking

### Files to Use

| File | Path | What It Contains |
|------|------|-----------------|
| `IProfitabilityAnalyticsObserver.java` | `common/src/main/java/com/pricingos/common/IProfitabilityAnalyticsObserver.java` | Interface with two date-filtered query methods |
| `IMarginProfitabilityService.java` | `common/src/main/java/com/pricingos/common/IMarginProfitabilityService.java` | Interface returning a combined summary result |
| `MarginProfitabilityResult.java` | `common/src/main/java/com/pricingos/common/MarginProfitabilityResult.java` | Record type returned by the service, includes nested `DateRange` |

#### Reference files (do not modify, but read for context):
| File | Path | Why It Matters |
|------|------|---------------|
| `ProfitabilityAnalyticsObserver.java` | `pricing/src/main/java/com/pricingos/pricing/approval/ProfitabilityAnalyticsObserver.java` | Existing concrete class that writes rows into the `profitability_analytics` table on every approval/rejection — this is what populates the data your implementation will query |
| `ApprovalWorkflowEngine.java` | `pricing/src/main/java/com/pricingos/pricing/approval/ApprovalWorkflowEngine.java` | The `approve()` method (line 78) and `reject()` method (line 99) are the hook points where profitability data originates |

### Interfaces to Implement

#### `IProfitabilityAnalyticsObserver`

```java
import com.pricingos.common.IProfitabilityAnalyticsObserver;
import java.time.LocalDateTime;
```

| Method | Signature | What to Return |
|--------|-----------|---------------|
| `getApprovedRevenueDelta` | `double getApprovedRevenueDelta(LocalDateTime startDate, LocalDateTime endDate)` | Sum of `discount_amount` from `profitability_analytics` where `final_status = 'APPROVED'` and `recorded_at` is between `startDate` and `endDate` |
| `getRejectedSavings` | `double getRejectedSavings(LocalDateTime startDate, LocalDateTime endDate)` | Sum of `discount_amount` from `profitability_analytics` where `final_status = 'REJECTED'` and `recorded_at` is between `startDate` and `endDate` |

#### `IMarginProfitabilityService`

```java
import com.pricingos.common.IMarginProfitabilityService;
import com.pricingos.common.MarginProfitabilityResult;
import com.pricingos.common.MarginProfitabilityResult.DateRange;
import java.time.LocalDateTime;
```

| Method | Signature | What to Return |
|--------|-----------|---------------|
| `getMarginProfitabilitySummary` | `MarginProfitabilityResult getMarginProfitabilitySummary(LocalDateTime startDate, LocalDateTime endDate)` | A `MarginProfitabilityResult` containing `marginConceded` (approved sum), `marginProtected` (rejected sum), and the `DateRange` used |

#### `MarginProfitabilityResult` (record — no implementation needed, just use it)

```java
public record MarginProfitabilityResult(
    double marginConceded,      // total discount_amount for APPROVED rows
    double marginProtected,     // total discount_amount for REJECTED rows
    DateRange period            // the query time window
)

public record DateRange(
    LocalDateTime start,        // inclusive lower bound
    LocalDateTime end           // inclusive upper bound
)
```

### How to Integrate

1. **Add the common JAR** to your module's dependencies (it is already a sibling module in the Maven reactor build).

2. **Create your implementation class** in your own package, e.g.:
   ```java
   package com.pricingos.reporting;

   public class MarginProfitabilityServiceImpl implements IMarginProfitabilityService {
       // your implementation here
   }
   ```

3. **Query the database** — all data lives in a single table:
   ```
   Table:  profitability_analytics
   Columns you need:
     - discount_amount  (DECIMAL(19,4)) — the dollar value of the discount
     - final_status     (VARCHAR(20))   — either 'APPROVED' or 'REJECTED'
     - recorded_at      (TIMESTAMP)     — when the approval/rejection happened
   ```

4. **Sample SQL** your implementation should execute:
   ```sql
   -- For getApprovedRevenueDelta:
   SELECT COALESCE(SUM(discount_amount), 0)
   FROM profitability_analytics
   WHERE final_status = 'APPROVED'
     AND recorded_at >= ?
     AND recorded_at <= ?;

   -- For getRejectedSavings:
   SELECT COALESCE(SUM(discount_amount), 0)
   FROM profitability_analytics
   WHERE final_status = 'REJECTED'
     AND recorded_at >= ?
     AND recorded_at <= ?;
   ```

5. **Wire it up** — inject or instantiate your implementation wherever your dashboard subsystem needs margin data.

6. **Data flow summary**:
   ```
   User approves/rejects → ApprovalWorkflowEngine.approve()/reject()
     → notifyApproved()/notifyRejected()
       → ProfitabilityAnalyticsObserver.onRequestApproved()/onRequestRejected()
         → AnalyticsDao.save() → INSERT INTO profitability_analytics
           → Your implementation queries this table via the interface methods
   ```

---

## Requirement 2 — Customer Tier Distribution

### Files to Use

| File | Path | What It Contains |
|------|------|-----------------|
| `ICustomerTierDistributionService.java` | `common/src/main/java/com/pricingos/common/ICustomerTierDistributionService.java` | Interface with grouping and average discount methods |
| `TierBucket.java` | `common/src/main/java/com/pricingos/common/TierBucket.java` | Record type for a single tier's customer count and percentage |
| `TierDistributionResult.java` | `common/src/main/java/com/pricingos/common/TierDistributionResult.java` | Record type combining all buckets and the global average discount |

#### Existing types you will use (do not modify):
| File | Path | Why It Matters |
|------|------|---------------|
| `CustomerTier.java` | `common/src/main/java/com/pricingos/common/CustomerTier.java` | Enum with 4 values: `STANDARD(0.0)`, `SILVER(0.05)`, `GOLD(0.10)`, `PLATINUM(0.15)` — each has a `getDiscountRate()` method |
| `ICustomerTierService.java` | `common/src/main/java/com/pricingos/common/ICustomerTierService.java` | Existing interface with `getTier(customerId)` and `getDiscountRate(customerId)` — your implementation can delegate to this |

#### Reference files (do not modify, but read for context):
| File | Path | Why It Matters |
|------|------|---------------|
| `CustomerTierEngine.java` | `pricing/src/main/java/com/pricingos/pricing/tier/CustomerTierEngine.java` | Concrete implementation of `ICustomerTierService` — the `getTier()` method (line 29) and `getDiscountRate()` method (line 41) are the hook points that resolve a customer's tier from the database |

### Interfaces to Implement

#### `ICustomerTierDistributionService`

```java
import com.pricingos.common.ICustomerTierDistributionService;
import com.pricingos.common.TierDistributionResult;
import com.pricingos.common.TierBucket;
import com.pricingos.common.CustomerTier;
```

| Method | Signature | What to Return |
|--------|-----------|---------------|
| `groupCustomersByTier` | `TierDistributionResult groupCustomersByTier(String[] customerIds)` | A `TierDistributionResult` containing one `TierBucket` per tier found, plus the global average discount rate across all provided customers |
| `getGlobalAverageDiscountRate` | `double getGlobalAverageDiscountRate(String[] customerIds)` | The mean of `CustomerTier.getDiscountRate()` across all provided customer IDs after resolving each customer's tier |

#### `TierBucket` (record — just use it)

```java
public record TierBucket(
    CustomerTier tier,      // e.g. CustomerTier.GOLD
    int count,              // number of customers in this tier
    double percentage       // count / total customers (0.0 to 1.0)
)
```

#### `TierDistributionResult` (record — just use it)

```java
public record TierDistributionResult(
    TierBucket[] buckets,               // one per tier
    double globalAverageDiscount        // mean discount rate
)
```

### How to Integrate

1. **Add the common JAR** to your module's dependencies.

2. **Create your implementation class**:
   ```java
   package com.pricingos.reporting;

   public class CustomerTierDistributionServiceImpl implements ICustomerTierDistributionService {
       // your implementation here
   }
   ```

3. **Two options for resolving tiers**:

   **Option A — Use the existing `ICustomerTierService`** (recommended):
   ```java
   // Inject or obtain an ICustomerTierService instance
   // For each customerId: tier = tierService.getTier(customerId)
   // Group by tier, count per group, compute percentages
   ```

   **Option B — Query the database directly**:
   ```
   Table: customer_tier_cache
   Columns:
     - customer_id  (VARCHAR(100)) — the customer identifier
     - tier         (VARCHAR(20))  — values: STANDARD, SILVER, GOLD, PLATINUM

   Table: customer_tier_overrides (takes precedence if a row exists)
   Columns:
     - customer_id    (VARCHAR(100))
     - override_tier  (VARCHAR(20))
   ```

4. **Sample SQL** for direct database approach:
   ```sql
   -- Get tier for a customer (override takes precedence):
   SELECT COALESCE(o.override_tier, c.tier, 'STANDARD') AS effective_tier
   FROM customer_tier_cache c
   LEFT JOIN customer_tier_overrides o ON c.customer_id = o.customer_id
   WHERE c.customer_id = ?;

   -- Group all customers by tier:
   SELECT
     COALESCE(o.override_tier, c.tier, 'STANDARD') AS effective_tier,
     COUNT(*) AS customer_count
   FROM customer_tier_cache c
   LEFT JOIN customer_tier_overrides o ON c.customer_id = o.customer_id
   WHERE c.customer_id IN (?, ?, ...)
   GROUP BY effective_tier;
   ```

5. **Computing the global average discount**:
   ```java
   // After resolving each customer's tier:
   double sum = 0;
   for (String id : customerIds) {
       CustomerTier tier = resolveTier(id);  // from cache/override
       sum += tier.getDiscountRate();         // 0.0, 0.05, 0.10, or 0.15
   }
   double globalAvg = sum / customerIds.length;
   ```

6. **Data flow summary**:
   ```
   Your code receives customerIds[]
     → For each: look up tier from customer_tier_cache (with override precedence)
       → Convert tier string to CustomerTier enum via CustomerTier.valueOf(tierString)
         → Group into TierBucket[] (count per tier, percentage = count/total)
         → Compute globalAverageDiscount from CustomerTier.getDiscountRate()
           → Return TierDistributionResult(buckets, globalAverageDiscount)
   ```

---

## Notes

- **Do not modify the interface files** — they are contracts. Implement them in your own subsystem's package.
- **Follow existing conventions**: all interfaces use the `I` prefix (e.g. `IMarginProfitabilityService`), all types are in `com.pricingos.common`.
- **The common JAR has no dependencies on the pricing module** — you only need the common JAR to compile your implementation.
- **Database connection**: use the existing `DatabaseConnectionPool` singleton from the pricing module, or configure your own connection using `resources/database.properties`.
- **Testing**: the `populate_part1.sql` file includes seed data in `profitability_analytics` (2 rows: 1 APPROVED, 1 REJECTED) and `customer_tier_cache` (2 rows: GOLD, PLATINUM) for verifying your implementation.
