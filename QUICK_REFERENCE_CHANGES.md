# Quick Reference: Locations to Change After Exceptions Team Update

## Two Files Modified - Two Locations Each

### File 1: PricingSubsystemGUI.java
**Path:** `pricing/src/main/java/com/pricingos/pricing/gui/PricingSubsystemGUI.java`

| Action | Location | Current | Change To |
|--------|----------|---------|-----------|
| **ADD** | ~Line 57-75 | Helper method added | **REMOVE** this method when `raise()` is public |
| **CHANGE** | ~Line 427 | `logUnregistegeredException(0, "INVALID_NUMBER_INPUT", ...)` | `exceptions.raise(0, "INVALID_NUMBER_INPUT", ..., Severity.MINOR)` |

---

### File 2: CurrencySimulator.java  
**Path:** `pricing/src/main/java/com/pricingos/pricing/simulation/CurrencySimulator.java`

| Action | Location | Current | Change To |
|--------|----------|---------|-----------|
| **ADD** | ~Line 30-48 | Helper method added | **REMOVE** this method when `raise()` is public |
| **CHANGE** | ~Line 75 | `logUnregistegeredException(0, "UNSUPPORTED_CURRENCY_CONVERSION", ...)` | `exceptions.raise(0, "UNSUPPORTED_CURRENCY_CONVERSION", ..., Severity.MINOR)` |

---

## Exception Details

### Exception 1: Invalid Number Input
- **Exception Type:** `NumberFormatException`
- **Trigger:** User enters non-numeric value in promotion creation (discount amount, min cart value, or max uses)
- **File:** PricingSubsystemGUI.java
- **Line:** ~427
- **Exception ID:** 0 (unregistered)
- **Name:** INVALID_NUMBER_INPUT
- **Suggested Severity:** MINOR

### Exception 2: Unsupported Currency Conversion
- **Exception Type:** `IllegalArgumentException`
- **Trigger:** User tries to convert unsupported currency (only INR, USD, EUR, GBP supported)
- **File:** CurrencySimulator.java
- **Line:** ~75
- **Exception ID:** 0 (unregistered)  
- **Name:** UNSUPPORTED_CURRENCY_CONVERSION
- **Suggested Severity:** MINOR

---

## Files to Reference

| File | Purpose | Lines |
|------|---------|-------|
| **TEMPORARY_EXCEPTION_WORKAROUNDS.md** | 📋 **MAIN REFERENCE** - Full before/after code, step-by-step instructions | All details |
| **WORKAROUND_IMPLEMENTATION.md** | Summary of what was done and testing guide | Quick overview |
| **PricingSubsystemGUI.java** | Implementation file #1 | 57-75, 427 |
| **CurrencySimulator.java** | Implementation file #2 | 30-48, 75 |

---

## Implementation Pattern (Use for Both Locations)

When `raise()` becomes public, replace:
```java
logUnregistegeredException(0, "EXCEPTION_NAME", "message");
```

With:
```java
try {
    if (getExceptions() != null) {
        exceptions.raise(0, "EXCEPTION_NAME",
                       "message details here",
                       Severity.MINOR);  // adjust if needed
    }
} catch (Exception ex) {
    // Windows Event Viewer not available on Linux
}
```

---

## Verification Steps

After making changes:

```bash
# 1. Recompile
mvn clean compile

# 2. Run GUI
java -cp target/classes com.pricingos.pricing.gui.PricingSubsystemGUI

# 3. Test both scenarios:
#    - Enter invalid number in promotion creation
#    - Try unsupported currency conversion
#    
# 4. Verify events in Windows Event Viewer:
#    Event Viewer > Windows Logs > Application
#    Look for entries under "SCM-Multi-levelPricing"
```

---

## Status Summary

| Item | Status |
|------|--------|
| ✅ Project Compiles | YES |
| ✅ GUI Runs | YES |
| ✅ Temporary Logging Works | YES |
| ⏳ Windows Event Viewer Logging | BLOCKED - waiting for `raise()` public |
| ⏳ Helper Methods in Place | YES - ready to remove later |

---

**Last Line:** Change `raise()` from PRIVATE to PUBLIC in MultiLevelPricingSubsystem, then follow the pattern above for both files.
