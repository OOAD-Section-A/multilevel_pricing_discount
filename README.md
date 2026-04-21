# SCMS Pricing Subsystem

Implementation of the Multi-level Pricing & Discount Management subsystem.

## Current Scope

- Implemented components: 1-8
- Excluded by decision: component 9 (Invoice & Quote Price Generator)
- Exception handler v3 with Windows Event Viewer integration enabled

## Module Layout

- `common/`
  - Shared contracts, enums, and DTOs
  - Key pricing DTOs moved here: `OrderLineItem`, `PriceResult`, `PricingOverrideRequest`
  - Shared validation helper: `ValidationUtils`
- `pricing/`
  - Pricing subsystem implementation (engines, managers, domain objects)

## Build

- Root Maven multi-module build:
  - `pom.xml`
  - `common/pom.xml`
  - `pricing/pom.xml`
- Run all tests:

```bash
mvn test
```

## Architecture Notes

- Core engine contracts are interface-driven (SOLID DIP):
  - `IDiscountRulesEngine`
  - `IDiscountPolicyService`
  - `IContractPricingService`
  - `ICustomerTierService`
  - `IApprovalWorkflowService`
- Discount extensibility preserved through strategy interface:
  - `IDiscountStrategy`
  - Implementations: `VolumeDiscountStrategy`, `TierDiscountStrategy`, `PromoCodeStrategy`


## Exception Handler System

The multilevel pricing system includes the updated exception handler v3 with Windows Event Viewer integration:

**JAR Dependencies (in `lib/` folder):**
- `scm-exception-handler-v3.jar` — Main exception handler (logs to Windows Event Viewer)
- `jna-5.18.1.jar` — JNA library for Event Viewer access
- `jna-platform-5.18.1.jar` — JNA platform extensions

**Features:**
- Exceptions logged directly to Windows Event Viewer
- Graceful degradation on non-Windows systems
- 8 exception handler methods integrated across pricing components
- GUI reads exceptions from Event Viewer (via `scm-exception-viewer-gui.jar`)
