# SCMS Pricing Subsystem

## Component 1 & 3 — Implementation Notes

- **Files created (Component 1):**
  - `pricing/src/main/java/com/pricingos/pricing/pricelist/PriceListManager.java`
  - `pricing/src/main/java/com/pricingos/pricing/pricelist/PriceRecord.java`
  - `pricing/src/main/java/com/pricingos/pricing/pricelist/IPriceStore.java`
  - `pricing/src/main/java/com/pricingos/pricing/pricelist/InMemoryPriceStore.java`
  - `pricing/src/main/java/com/pricingos/pricing/pricelist/IPriceUpdateListener.java`
  - `pricing/src/main/java/com/pricingos/pricing/pricelist/PriceAuditLogger.java`
- **Files created (Component 3):**
  - `pricing/src/main/java/com/pricingos/pricing/baseprice/IBasePriceConfig.java`
  - `pricing/src/main/java/com/pricingos/pricing/baseprice/BasePriceConfig.java`
  - `pricing/src/main/java/com/pricingos/pricing/baseprice/BasePriceRecord.java`
- **Exception ownership note:**
  - Custom exception class definitions are intentionally not included in this module.
  - Components 1 and 3 only declare and throw exception types; concrete exception classes are expected from the dedicated exception subsystem on the classpath.

- **Interfaces reused vs. new:**
  - Reused existing repository contracts and DTOs by avoiding redefinition of shared `common` module types (`InvoiceLineItem`, `VolumeTierRule`, `IPricingFacade`, and related existing contracts).
  - Created new interfaces required for this scope: `IBasePriceConfig`, `IPriceStore`, `IPriceUpdateListener`.

- **Design patterns applied:**
  - **Builder:** `BasePriceRecord.Builder` constructs immutable configuration records.
  - **Facade:** `PriceListManager` provides a single API for active lookup, history retrieval, cache refresh, and update/versioning.
  - **Observer:** `PriceListManager` notifies `IPriceUpdateListener` implementations on every `updatePrice`; default listener is `PriceAuditLogger`.

- **SOLID principles applied:**
  - **SRP:** `BasePriceConfig` only validates/calculates pricing configuration; `PriceListManager` handles retrieval/versioning.
  - **OCP:** `IBasePriceConfig` supports introducing alternate pricing configuration strategies without changing existing clients.
  - **DIP:** `PriceListManager` depends on `IPriceStore`, not directly on concrete map storage.

- **GRASP principles applied:**
  - **Information Expert:** `BasePriceConfig` computes margin/base/floor from cost inputs; `PriceListManager` owns record lookup/versioning behavior.
  - **Controller:** `PriceListManager` acts as the primary entry point for price lookup requests.
