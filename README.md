# SCMS Pricing Subsystem

Implementation of the Multi-level Pricing & Discount Management subsystem.

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

## Database Integration

```bash
export DB_URL=jdbc:mysql://localhost:3306/OOAD
export DB_USERNAME=root
export DB_PASSWORD=your_password
```

## Run

Build the modules:

```bash
mvn -q -DskipTests package
```

Launch the pricing GUI from the repo root:

```bash
java -cp "common/target/classes:pricing/target/classes:lib/*" com.pricingos.pricing.gui.PricingSubsystemGUI
```

Windows launchers:

- `RUN_GUI.bat`
- `RUN_GUI.ps1`

To launch with end-to-end demo data preloaded into the live GUI session:

```bash
PRICING_SEED_DEMO_DATA=true ./RUN_GUI.sh
```

PowerShell equivalent:

```powershell
$env:PRICING_SEED_DEMO_DATA = "true"
.\RUN_GUI.ps1
```

This seeds demo prices, tiers, promotions, rebates, and approval workflow examples through the pricing code and adapters. It does not use direct SQL or manual schema files.

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
- `scm-exception-handler-v3.jar` - Main exception handler (logs to Windows Event Viewer)
- `scm-exception-viewer-gui.jar` - Exception viewer GUI (reads from Windows Event Viewer)
- `jna-5.18.1.jar` - JNA library for Event Viewer access
- `jna-platform-5.18.1.jar` - JNA platform extensions

**One-time Windows setup:**

Run this once as Administrator so Event Viewer can register the pricing source:

```bat
reg add "HKLM\SYSTEM\CurrentControlSet\Services\EventLog\Application\SCM-Multi-levelPricing" /v EventMessageFile /t REG_SZ /d "%SystemRoot%\System32\EventCreate.exe" /f
```

**Features:**
- Exceptions logged directly to Windows Event Viewer
- Exception data is not stored in the database
- Graceful degradation on non-Windows systems
- Pricing exception methods are integrated across the pricing components
- Unregistered pricing errors use handler ID `0`
- Exception viewer GUI reads exceptions from Event Viewer

**Viewer launchers:**
- `RUN_EXCEPTION_VIEWER.bat`
- `RUN_EXCEPTION_VIEWER.ps1`

**Test behavior:**
- `mvn test` disables Event Viewer writes with `-Dscm.event.viewer.disabled=true`
