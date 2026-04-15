# SCMS Pricing Subsystem

Implementation of the Multi-level Pricing & Discount Management subsystem.

## Current Scope

- Implemented components: 1-8
- Excluded by decision: component 9 (Invoice & Quote Price Generator)
- Exception foundation dependency is packaged at `lib/scm-exception-foundation.jar`

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


## Exception Foundation Jar

Use this artifact on classpath:

- `lib/scm-exception-foundation.jar`
