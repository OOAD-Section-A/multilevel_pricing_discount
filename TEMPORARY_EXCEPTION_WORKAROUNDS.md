# Temporary Exception Handler Workarounds

## Summary

Due to the `raise()` method in `MultiLevelPricingSubsystem` being marked as **PRIVATE**, we cannot currently log unregistered exceptions (ID 0) using the intended approach:

```java
exceptions.raise(0, "EXCEPTION_NAME", "message", Severity.MINOR);
```

### Status
- ✅ Project compiles successfully
- ✅ GUI runs without errors  
- ⚠️ Unregistered exceptions use temporary local logging instead of Windows Event Viewer
- ⏳ Awaiting exceptions team to make `raise()` method PUBLIC

---

## Locations Requiring Future Updates

### 1. PricingSubsystemGUI.java

**File Path:** 
```
pricing/src/main/java/com/pricingos/pricing/gui/PricingSubsystemGUI.java
```

#### Location 1a: Helper Method Added (Line ~57-75)
**What:** Added temporary workaround method `logUnregistegeredException()`

**Current Code:**
```java
/**
 * Temporary workaround for logging unregistered exceptions (ID 0).
 * 
 * The raise(int id, String name, String message, Severity severity) method 
 * in MultiLevelPricingSubsystem is currently PRIVATE. This method logs the 
 * exception locally until exceptions team makes raise() public.
 * 
 * TODO: Replace with exceptions.raise(0, name, message, severity) after exceptions team update
 */
private void logUnregistegeredException(int exceptionId, String exceptionName, String message) {
    String logMessage = String.format(
        "[UNREGISTERED_EXCEPTION_ID_%d] %s: %s",
        exceptionId, exceptionName, message
    );
    LOGGER.warning(logMessage);
    log("WARNING: " + logMessage);
}
```

**After Exceptions Team Updates:**
- Can be **removed entirely** once `raise()` is public
- OR simplified to just do `exceptions.raise()` call

---

#### Location 1b: NumberFormatException Handler (Line ~403-410)

**Exception Type:** `NumberFormatException` in Promotion Creation

**Current Code:**
```java
} catch (NumberFormatException nfe) {
    // TEMPORARY WORKAROUND: Using local logging instead of exceptions.raise(0, ...)
    // because raise() method is PRIVATE in MultiLevelPricingSubsystem
    // TODO: Change to exceptions.raise(0, "INVALID_NUMBER_INPUT", ...) after exceptions team makes raise() public
    logUnregistegeredException(0, "INVALID_NUMBER_INPUT", 
        "Promotion creation input validation failed: " + nfe.getMessage());
    log("ERROR: Invalid numeric input for promotion creation");
    return;
}
```

**What Should Be Changed To:**
```java
} catch (NumberFormatException nfe) {
    try {
        if (getExceptions() != null) {
            exceptions.raise(0, "INVALID_NUMBER_INPUT",
                           "Promotion creation input validation failed: " + nfe.getMessage(),
                           Severity.MINOR);  // or appropriate severity
        }
    } catch (Exception ex) {
        // Windows Event Viewer not available on Linux
    }
    log("ERROR: Invalid numeric input for promotion creation");
    return;
}
```

**Details:**
- **Exception ID:** 0 (unregistered)
- **Exception Name:** INVALID_NUMBER_INPUT
- **Context:** User enters non-numeric values in promotion discount/min cart/max uses fields
- **Severity:** Suggested MINOR (validation error)
- **Impact:** Currently only logs to application logger; needs to go to Windows Event Viewer

---

### 2. CurrencySimulator.java

**File Path:**
```
pricing/src/main/java/com/pricingos/pricing/simulation/CurrencySimulator.java
```

#### Location 2a: Helper Method Added (Line ~30-48)
**What:** Added temporary workaround method `logUnregistegeredException()`

**Current Code:**
```java
/**
 * Temporary workaround for logging unregistered exceptions (ID 0).
 * 
 * The raise(int id, String name, String message, Severity severity) method 
 * in MultiLevelPricingSubsystem is currently PRIVATE. This method logs the 
 * exception locally until exceptions team makes raise() public.
 * 
 * TODO: Replace with exceptions.raise(0, name, message, severity) after exceptions team update
 */
private void logUnregistegeredException(int exceptionId, String exceptionName, String message) {
    String logMessage = String.format(
        "[UNREGISTERED_EXCEPTION_ID_%d] %s: %s",
        exceptionId, exceptionName, message
    );
    LOGGER.warning(logMessage);
}
```

**After Exceptions Team Updates:**
- Can be **removed entirely** once `raise()` is public
- OR can be kept in CurrencySimulator only if needed

---

#### Location 2b: Unsupported Currency Handler (Line ~67-72)

**Exception Type:** `IllegalArgumentException` for unsupported currency conversion

**Current Code:**
```java
if (fromToInr == null || toToInr == null) {
    // TEMPORARY WORKAROUND: Using local logging instead of exceptions.raise(0, ...)
    // because raise() method is PRIVATE in MultiLevelPricingSubsystem
    // TODO: Change to exceptions.raise(0, "UNSUPPORTED_CURRENCY_CONVERSION", ...) after exceptions team makes raise() public
    logUnregistegeredException(0, "UNSUPPORTED_CURRENCY_CONVERSION", 
        "Currency conversion not supported: " + from + " -> " + to);
    throw new IllegalArgumentException("Unsupported currency conversion: " + from + " -> " + to);
}
```

**What Should Be Changed To:**
```java
if (fromToInr == null || toToInr == null) {
    try {
        if (getExceptions() != null) {
            exceptions.raise(0, "UNSUPPORTED_CURRENCY_CONVERSION",
                           "Currency conversion not supported: " + from + " -> " + to,
                           Severity.MINOR);  // or appropriate severity
        }
    } catch (Exception ex) {
        // Windows Event Viewer not available on Linux
    }
    throw new IllegalArgumentException("Unsupported currency conversion: " + from + " -> " + to);
}
```

**Details:**
- **Exception ID:** 0 (unregistered)
- **Exception Name:** UNSUPPORTED_CURRENCY_CONVERSION
- **Context:** User attempts to convert between currencies not in the supported list
- **Severity:** Suggested MINOR (unsupported operation)
- **Impact:** Currently only logs to application logger; needs to go to Windows Event Viewer

---

## Instructions for Future Updates

### When Exceptions Team Makes `raise()` Public

1. **Verify** the method signature is public:
   ```java
   public void raise(int id, String name, String message, Severity severity)
   ```

2. **Update Location 1b (PricingSubsystemGUI.java):**
   - Replace the `logUnregistegeredException()` call with direct `exceptions.raise()` call
   - Wrap in try-catch for platform compatibility
   - Keep line 403 catch block structure

3. **Update Location 2b (CurrencySimulator.java):**
   - Replace the `logUnregistegeredException()` call with direct `exceptions.raise()` call
   - Wrap in try-catch for platform compatibility
   - Keep the throw statement after logging

4. **Optional Cleanup:**
   - Remove `logUnregistegeredException()` helper methods from both files (lines ~57-75 in PricingSubsystemGUI and lines ~30-48 in CurrencySimulator)
   - Remove temporary import: `java.util.logging.Level` from CurrencySimulator (only used for potential future logging)

5. **Test:** 
   - Recompile: `mvn clean compile`
   - Run GUI: `java -cp target/classes com.pricingos.pricing.gui.PricingSubsystemGUI`
   - Trigger invalid numeric input in promotion creation
   - Trigger unsupported currency conversion
   - Verify events appear in Windows Event Viewer

---

## Current Workaround Behavior

### What Happens Now
- **NumberFormatException:** Logged to application logger with prefix `[UNREGISTERED_EXCEPTION_ID_0]`
- **UnsupportedCurrencyException:** Logged to application logger with prefix `[UNREGISTERED_EXCEPTION_ID_0]`

### What Should Happen After Fix
- Both exceptions should create entries in Windows Event Viewer
- Subsystem: SCM-Multi-levelPricing
- Category: INFORMATION or based on Severity parameter
- The exception popup window may appear (if exception handler framework monitors these)

---

## Related Files

- **Issue Documentation:** [EXCEPTION_HANDLER_INTEGRATION_ISSUE.md](EXCEPTION_HANDLER_INTEGRATION_ISSUE.md)
- **Integration Verification:** [EXCEPTION_INTEGRATION_VERIFICATION.md](EXCEPTION_INTEGRATION_VERIFICATION.md)
- **Exception Framework:** lib/scm-exception-handler-v3.jar (v3.0)

---

## Quick Reference Table

| File | Line | Exception Type | ID | Name | Current Status |
|------|------|---|---|---|---|
| PricingSubsystemGUI.java | 57-75 | N/A (helper) | 0 | logUnregistegeredException() | ⏳ Temporary |
| PricingSubsystemGUI.java | 403-410 | NumberFormatException | 0 | INVALID_NUMBER_INPUT | ⏳ Local logging only |
| CurrencySimulator.java | 30-48 | N/A (helper) | 0 | logUnregistegeredException() | ⏳ Temporary |
| CurrencySimulator.java | 67-72 | IllegalArgumentException | 0 | UNSUPPORTED_CURRENCY_CONVERSION | ⏳ Local logging only |

---

**Last Updated:** During exception handler integration verification
**Status:** Pending exceptions team to make `raise()` method public
