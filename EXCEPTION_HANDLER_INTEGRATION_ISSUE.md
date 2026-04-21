# Exception Handler Integration Issue

**Date:** April 21, 2026  
**Subsystem:** Multi-level Pricing  
**Status:** ⚠️ **BLOCKING - Need Resolution**

---

## Problem Summary

We need to log **unregistered exceptions** (exceptions not in your master exception list) using the placeholder method you provided: `exceptions.raise(0, "EXCEPTION_NAME", "message", Severity.MINOR)`.

However, **the `raise()` method has private access** and cannot be called from our subsystem code.

---

## What We're Trying to Do

When an exception occurs that's **not on your official list**, we need to log it as ID 0 (unregistered). Your documentation states:

> "If an exception in your code is not listed in Exception-List-Final.xlsx, use ID 0 as a placeholder"

**Example from your instructions:**
```java
try {
    // your logic
} catch (SomeUnknownException e) {
    exceptions.raise(0, "UNREGISTERED_EXCEPTION",
                     "What happened: " + e.getMessage(),
                     Severity.MINOR);
    return;
}
```

---

## Real Code Examples We're Using

### **Example 1: Invalid Promo Code Creation (Duplicate)**

**File:** `pricing/src/main/java/com/pricingos/pricing/gui/PricingSubsystemGUI.java` - Line 415

**Scenario:** User tries to create a promotion with a coupon code that already exists

```java
try {
    String promoId = promotionManager.createPromotion(name, couponCode, type, 
                                                       discountValue, startDate, 
                                                       endDate, eligibleSkus, 
                                                       minCartValue, maxUses);
} catch (IllegalArgumentException e) {
    // This exception: "Coupon code 'SUMMER24' already exists" 
    // is NOT in your official list
    try {
        if (getExceptions() != null) {
            exceptions.raise(0, "DUPLICATE_PROMO_CODE",
                           "Promotion with code already exists: " + e.getMessage(),
                           Severity.MINOR);
        }
    } catch (Exception ex) {
        // ❌ COMPILATION ERROR: private access
    }
}
```

---

### **Example 2: Unsupported Currency Conversion**

**File:** `pricing/src/main/java/com/pricingos/pricing/simulation/CurrencySimulator.java` - Line 52

**Scenario:** User tries to convert between unsupported currencies

```java
if (fromToInr == null || toToInr == null) {
    try {
        if (getExceptions() != null) {
            exceptions.raise(0, "UNSUPPORTED_CURRENCY_CONVERSION",
                           "Currency conversion not supported: " + from + " -> " + to,
                           Severity.MINOR);
        }
    } catch (Exception e) {
        // ❌ COMPILATION ERROR: private access
    }
    throw new IllegalArgumentException("Unsupported currency conversion: " + from + " -> " + to);
}
```

---

### **Example 3: Invalid Numeric Input (GUI Input Validation)**

**File:** `pricing/src/main/java/com/pricingos/pricing/gui/PricingSubsystemGUI.java` - Line 406

**Scenario:** User enters non-numeric values in promotion creation form

```java
try {
    discountValue = Double.parseDouble(valueField.getText());
    minCartValue = Double.parseDouble(minCartField.getText());
    maxUses = Integer.parseInt(maxUsesField.getText());
} catch (NumberFormatException nfe) {
    // Would try to log as unregistered exception
    try {
        if (getExceptions() != null) {
            exceptions.raise(0, "INVALID_INPUT", 
                           "Invalid numeric input: " + nfe.getMessage(), 
                           Severity.MINOR);
        }
    } catch (Exception ex) {
        // ❌ COMPILATION ERROR: private access
    }
    return;
}
```

---

## The Error We Get

When we try to compile this code:

```
[ERROR] /D:/Akash/B.Tech/6th Sem/OOAD/Project/multilevel_pricing_discount/pricing/src/main/java/com/pricingos/pricing/gui/PricingSubsystemGUI.java:[406,39]
raise(int,java.lang.String,java.lang.String,com.scm.core.Severity) has private access in com.scm.subsystems.MultiLevelPricingSubsystem
```

**Translation:** The `raise()` method is marked as **private** in `MultiLevelPricingSubsystem` class, so external code cannot call it.

---

## Workaround for Now

Since we can't call `raise()`, we've implemented placeholder exception logging using local loggers. 

### **Placeholder Approach:**
```java
private static void logUnregisteredException(String exceptionName, String message) {
    LOGGER.warning("[UNREGISTERED_EXCEPTION] " + exceptionName + ": " + message);
    System.err.println("[UNREGISTERED_EXCEPTION] " + exceptionName + ": " + message);
}
```

---

## Official Exception Handlers Still Working

These continue to work without any issues:

| ID | Handler Method | Severity | Status |
|---|---|---|---|
| **7** | `onInvalidPromoCode()` | MINOR | ✅ Working |
| **8** | `onPriceFloorConfigError()` | MAJOR | ✅ Working |
| **55** | `onExternalDataTimeout()` | MAJOR | ✅ Working |
| **163** | `onBasePriceNotFound()` | MAJOR | ✅ Working |
| **206** | `onContractExpiredAlert()` | WARNING | ✅ Working |
| **309** | `onDuplicateContractConflict()` | MAJOR | ✅ Working |
| **310** | `onPolicyStackingConflict()` | WARNING | ✅ Working |
| **311** | `onNegativeMarginCalculation()` | MAJOR | ✅ Working |

---

## Locations Where Placeholders Were Added

These locations currently have **placeholder logging code** that needs to be updated once the `raise()` method becomes accessible:

### **1. PricingSubsystemGUI.java**

- **Line 407-411:** NumberFormatException handling during promotion creation
  - Exception: Invalid numeric input for discount value, min cart value, or max uses
  - Current: Local logger placeholder
  - Should map to: `exceptions.raise(0, "INVALID_PROMOTION_INPUT", ...)`

- **Line 480-484:** InvalidPromoCodeException handling during code validation  
  - Exception: Promo code validation failed
  - Current: Calls `onInvalidPromoCode()` (ID 7) - ✅ **Working**
  - Note: This already uses official handler

### **2. CurrencySimulator.java**

- **Line 48-57:** Unsupported currency conversion
  - Exception: Currency pair not supported (e.g., ZZZ → XXX)
  - Current: None (removed due to private access)
  - Should map to: `exceptions.raise(0, "UNSUPPORTED_CURRENCY_CONVERSION", ...)`

---

## Questions for Exception Handler Team

1. **Can you make `raise()` public** so we can log unregistered exceptions as ID 0?

2. **Alternative:** Should we map all our custom exceptions to existing official handler IDs instead?

3. **Or:** Is there an alternative public method to log exceptions ID 0?

---

## Current Compilation Status

✅ **Project compiles successfully** with official handlers  
⚠️ **Cannot use `raise(0, ...)` for unregistered exceptions** (private access)

---

## Next Steps

1. **Exceptions Team:** Please make `raise()` method public OR provide alternative way to log ID 0 exceptions
2. **Once resolved:** Update all placeholder locations listed above to use the official `raise()` method
3. **Verification:** Re-test exception logging for all unregistered exception scenarios

---

**Contact:** Multi-level Pricing Team  
**Date Raised:** April 21, 2026
