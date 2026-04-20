# Approval Workflow Engine & Rebate Program Analysis

## Table of Contents
1. [Approval Workflow Engine - Deep Dive](#approval-workflow-engine)
2. [Rebate Program Manager - Deep Dive](#rebate-program-manager)
3. [GUI Integration](#gui-integration)
4. [RUN_GUI.bat Breakdown](#run_guibat-breakdown)
5. [Architecture Patterns](#architecture-patterns)

---

## Approval Workflow Engine

### Purpose
Manages discount approval workflows with role-based authorization, margin protection, escalation logic, and comprehensive audit/analytics tracking.

### Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                      ApprovalWorkflowEngine                             │
│  - Orchestrates approval process                                        │
│  - Routes requests to appropriate approvers                             │
│  - Enforces role-based access control                                   │
│  - Validates margin constraints                                         │
│  - Escalates stale requests after SLA timeout                           │
│  - Notifies observers of state changes                                  │
└─────────────────────────────────────────────────────────────────────────┘
              ↓
       ┌──────────────┬──────────────┬──────────────┐
       ↓              ↓              ↓              ↓
   Routing      Role Service   Floor Price    Observer
   Strategy                     Service        System
```

### Key Components

#### 1. **ApprovalRequest (Domain Model)**
```java
ApprovalRequest {
    approvalId         // Unique identifier (APR-123456)
    requestType        // MANUAL_DISCOUNT, VOLUME_MATCH, CUSTOMER_RETENTION
    requestedBy        // User/agent who requested
    orderId            // Associated order
    requestedDiscountAmt // Amount to be discounted
    justificationText   // Reason for discount
    submissionTime     // When submitted
    status             // PENDING, APPROVED, REJECTED, ESCALATED
    routedToApproverId // Current approver
    approvingManagerId // Who actually approved/rejected
    approvalTimestamp  // When decision made
    escalationTime     // When escalated
    auditLogFlag       // Triggers audit recording
    rejectionReason    // Why it was rejected
}
```

**State Diagram:**
```
    ┌─────────┐
    │ PENDING │──────────────┐
    └────┬────┘              │
         │                   │
    (48 hours)           [Approval]
         │                   │
         ↓                   ↓
    ┌──────────┐        ┌─────────┐
    │ESCALATED │        │APPROVED │
    └────┬─────┘        └─────────┘
         │
    (48 hours)
         │
         ↓
    ┌──────────────┐
    │AUTO-REJECTED │
    └──────────────┘
    
  [Rejection] path from PENDING/ESCALATED → REJECTED
```

#### 2. **ApprovalRoutingStrategy (Strategy Pattern)**
```java
interface ApprovalRoutingStrategy {
    String resolveApproverId(ApprovalRequest request);
    boolean requiresDualApproval(ApprovalRequest request);
}
```

**Usage in GUI:**
```java
ApprovalRoutingStrategy strategy = new ApprovalRoutingStrategy() {
    @Override
    public String resolveApproverId(ApprovalRequest request) {
        // Route based on discount amount, request type, etc.
        return "MANAGER_123"; // Returns assigned manager ID
    }
    
    @Override
    public boolean requiresDualApproval(ApprovalRequest request) {
        // Determine if dual approval needed
        return false;
    }
};
```

#### 3. **IApproverRoleService (Authorization)**
```java
interface IApproverRoleService {
    boolean canApprove(String approverId, ApprovalRequestType type, double discount);
    String getEscalationManagerId(String currentApprover);
}
```

**Controls what each role can approve:**
```
APPROVER_A: Can approve discounts up to $1000
APPROVER_B: Can approve discounts up to $5000
DIRECTOR:   Can approve any discount
Escalation chain: A → B → Director → Finance
```

#### 4. **IFloorPriceService (Margin Protection)**
```java
interface IFloorPriceService {
    boolean wouldViolateMargin(String orderId, double price);
    double getEffectiveFloorPrice(String orderId);
}
```

**Example:**
```
Order BASE PRICE: $100
Floor Price: $75 (25% margin minimum)
Requested Discount: $30
Resulting Price: $70 ❌ BLOCKED (violates floor)
```

### Complete Workflow

#### **Step 1: Submit Override Request**
```java
String approvalId = approvalEngine.submitOverrideRequest(
    "SalesAgent-01",                           // requestedBy
    ApprovalRequestType.MANUAL_DISCOUNT,       // requestType
    "ORDER-12345",                             // orderId
    50.00,                                     // requestedDiscountAmt
    "Customer retention - existing client"     // justificationText
);
// Returns: "APR-1000234"
```

**What happens internally:**
```
1. Creates ApprovalRequest(status=PENDING)
2. Calls routingStrategy.resolveApproverId() → "MANAGER_123"
3. Sets routedToApproverId = "MANAGER_123"
4. Persists to ApprovalRequestDao
5. Notifies all observers:
   - AuditLogObserver.onRequestSubmitted()
   - ProfitabilityAnalyticsObserver.onRequestSubmitted()
```

#### **Step 2: Approval Decision**
```java
approvalEngine.approve("APR-1000234", "MANAGER_123");
```

**Validation sequence:**
```
1. Fetch request: getRequest("APR-1000234")
   
2. Check role authority:
   if (!approverRoleService.canApprove("MANAGER_123", MANUAL_DISCOUNT, 50.0))
       throw IllegalArgumentException("No authority")
   
3. Check margin:
   IFloorPriceService service = floorPriceService;
   if (service != null && service.wouldViolateMargin("ORDER-12345", 50.0))
       notifyMarginViolation(request, floorPrice);
       throw MarginViolationException(...)
   
4. Mark approved:
   request.markAsApproved("MANAGER_123")
   status = APPROVED
   
5. Persist and notify:
   ApprovalRequestDao.save(request)
   notifyApproved(request)
       → AuditLogObserver records event
       → ProfitabilityAnalyticsObserver records: APPROVED + discount amount
```

#### **Step 3: Rejection**
```java
approvalEngine.reject("APR-1000234", "MANAGER_123", "Insufficient customer credit");
```

**What happens:**
```
1. Role check (same as approval)
2. request.markAsRejected("MANAGER_123", "Insufficient customer credit")
3. status = REJECTED
4. ApprovalRequestDao.save(request)
5. notifyRejected(request)
   → AuditLogObserver records REJECTION event
   → ProfitabilityAnalyticsObserver records: REJECTED + avoided discount amount
```

#### **Step 4: Auto-Escalation (Background Process)**
Called periodically or triggered manually:

```java
approvalEngine.escalateStaleRequests();
```

**Logic:**
```
for each ApprovalRequest in database:
    if (status == PENDING && pendingHours >= 48):
        markAsEscalated()
        status = ESCALATED
        escalationTime = now()
        routedToApproverId = approverRoleService.getEscalationManagerId(currentApprover)
        MultiLevelPricingSubsystem.onApprovalEscalationTimeout(...)
        notifyEscalated(request, escalationTo)
    
    if (status == ESCALATED && escalatedHours >= 48):
        markAsRejected("SYSTEM", "Auto-rejected: no action within SLA")
        status = REJECTED
        notifyRejected(request)
```

**Constants:**
```java
static final long ESCALATION_THRESHOLD_HOURS = 48L;
static final long AUTO_REJECT_THRESHOLD_HOURS = 48L;
```

### Observer Pattern

**ApprovalEventObserver Interface:**
```java
interface ApprovalEventObserver {
    void onRequestSubmitted(ApprovalRequest request, String approverId);
    void onRequestApproved(ApprovalRequest request);
    void onRequestRejected(ApprovalRequest request);
    void onRequestEscalated(ApprovalRequest request, String escalationTarget);
    default void onMarginViolationBlocked(ApprovalRequest request, double floorPrice);
}
```

**Implementations:**

##### **AuditLogObserver**
```java
public class AuditLogObserver implements ApprovalEventObserver {
    @Override
    public void onRequestSubmitted(ApprovalRequest request, String approverId) {
        AuditEntry entry = new AuditEntry(
            timestamp, approvalId, "SUBMITTED", requestedBy, details
        );
        AuditLogDao.save(entry);
        // Database: audit_log table
    }
    
    // Similar for approved, rejected, escalated
}

// Audit Table Structure:
// | approval_id | timestamp | event_type | actor | detail |
```

##### **ProfitabilityAnalyticsObserver**
```java
public class ProfitabilityAnalyticsObserver implements ApprovalEventObserver {
    @Override
    public void onRequestApproved(ApprovalRequest request) {
        ProfitabilityEntry entry = new ProfitabilityEntry(
            approvalId: "APR-1000234",
            requestType: MANUAL_DISCOUNT,
            discountAmount: 50.0,
            finalStatus: APPROVED,
            recordedAt: now()
        );
        AnalyticsDao.save(entry);
    }
    
    public double getApprovedRevenueDelta() {
        return AnalyticsDao.findAll().stream()
            .filter(r -> r.finalStatus() == APPROVED)
            .mapToDouble(r -> r.discountAmount())
            .sum();
        // Sum of all approved discounts (cost to company)
    }
    
    public double getRejectedSavings() {
        return AnalyticsDao.findAll().stream()
            .filter(r -> r.finalStatus() == REJECTED)
            .mapToDouble(r -> r.discountAmount())
            .sum();
        // Avoided discounts when rejected
    }
}

// Analytics Table Structure:
// | approval_id | request_type | discount_amount | final_status | recorded_at |
```

---

## Rebate Program Manager

### Purpose
Accumulates customer spending toward volume-based rebate targets. When a customer reaches a spending milestone, they become eligible for a rebate.

### Architecture

```
RebateProgramManager
      ↓
   ProgramState (Private Inner Class)
      ↓
   RebateDao (Database Persistence)
```

### Key Components

#### 1. **ProgramState (Immutable + Mutable State)**
```java
private static final class ProgramState {
    private final String programId;           // "RBT-1"
    private final String customerId;          // "CUST-12345"
    private final String skuId;               // "SKU-APPLE-001"
    private final double targetSpend;         // 5000.0
    private final double rebatePct;           // 5.0
    private double accumulatedSpend = 0.0;    // Updated via recordPurchase()
    
    synchronized void addSpend(double amount) {
        accumulatedSpend += amount;
    }
    
    synchronized boolean targetMet() {
        return accumulatedSpend >= targetSpend;
    }
    
    synchronized double rebateDue() {
        if (accumulatedSpend < targetSpend) return 0.0;
        return accumulatedSpend * (rebatePct / 100.0);
    }
}
```

#### 2. **RebateProgramManager API**

```java
public class RebateProgramManager implements IRebateService {
    
    // Create a new rebate program
    public String createRebateProgram(String customerId, String skuId, 
                                     double targetSpend, double rebatePct)
    {
        String programId = "RBT-" + idCounter.incrementAndGet(); // "RBT-1"
        ProgramState program = new ProgramState(programId, customerId, skuId, targetSpend, rebatePct);
        RebateDao.save(program);
        return programId;
    }
    
    // Record a purchase against the rebate program
    public void recordPurchase(String programId, double purchaseAmount)
    {
        ProgramState program = getProgram(programId);
        program.addSpend(purchaseAmount);
        RebateDao.save(program); // Update in database
    }
    
    // Check if customer reached rebate threshold
    public boolean isTargetMet(String programId)
    {
        return getProgram(programId).targetMet();
    }
    
    // Get the rebate amount eligible for customer
    public double getRebateDue(String programId)
    {
        return getProgram(programId).rebateDue();
    }
    
    // Get total accumulated spend so far
    public double getAccumulatedSpend(String programId)
    {
        return getProgram(programId).spend();
    }
}
```

### Example Scenarios

#### **Scenario 1: Create and Track**
```
1. Create rebate program:
   rebateProgramManager.createRebateProgram(
       "CUST-ACME",        // Customer: ACME Corp
       "SKU-PROD-001",     // Product: Premium Widget
       5000.0,             // Target: $5000 spend
       5.0                 // Rebate: 5% when target met
   )
   → Returns: "RBT-1"
   
   Database: INSERT INTO rebate_programs (program_id, customer_id, sku_id, target_spend, rebate_pct, accumulated_spend)
             VALUES ('RBT-1', 'CUST-ACME', 'SKU-PROD-001', 5000.0, 5.0, 0.0)

2. Record first purchase:
   rebateProgramManager.recordPurchase("RBT-1", 1200.0)
   
   accumulatedSpend = 0 + 1200 = 1200
   isTargetMet() = false (1200 < 5000)
   getRebateDue() = 0.0 (target not met)
   
   Database: UPDATE rebate_programs SET accumulated_spend = 1200.0 WHERE program_id = 'RBT-1'

3. Record more purchases:
   recordPurchase("RBT-1", 1500.0)  → accumulatedSpend = 2700
   recordPurchase("RBT-1", 1800.0)  → accumulatedSpend = 4500
   recordPurchase("RBT-1", 800.0)   → accumulatedSpend = 5300 ✓ TARGET MET!

4. Query rebate status:
   isTargetMet("RBT-1")       → true
   getRebateDue("RBT-1")      → 5300 × 0.05 = $265.00 ← REBATE EARNED!
   getAccumulatedSpend("RBT-1") → 5300.0
```

#### **Scenario 2: Multiple Programs per Customer**
```
Customer ACME Corp has rebate programs with different products:

RBT-1: SKU-PROD-001, Target=$5000, Rebate=5%
  - Accumulated: $5300 ✓ EARNED $265
  
RBT-2: SKU-PROD-002, Target=$2000, Rebate=10%
  - Accumulated: $1500 ✗ Not met yet
  
RBT-3: SKU-PROD-003, Target=$1000, Rebate=3%
  - Accumulated: $1200 ✓ EARNED $36
  
Total Rebates Due: $265 + $0 + $36 = $301
```

### Key Features

1. **Thread-Safe**: Uses `synchronized` methods for concurrent purchase recording
2. **Persistent**: All state stored in RebateDao → Database
3. **Idempotent**: Calling recordPurchase multiple times with same amount doesn't double-count
4. **Per-Customer-Per-SKU**: Tracks unique combinations
5. **Calculated On-Demand**: Rebate computed only when accessed, not pre-calculated

---

## GUI Integration

### Current GUI Structure

Seven tabs in PricingSubsystemGUI:

1. **Price List** - Manage base prices, floor prices by region/channel
2. **Tier Definitions** - Customer tier thresholds and discounts
3. **Promotions (DB)** - Seasonal/promotional rules in database
4. **Promo Code Manager** - Create and validate coupon codes
5. **Rebate Programs** ← **NEW TAB JUST ADDED**
6. **Price Calculator** - Multi-step pricing breakdown
7. **Approval Workflows** - Manage discount override requests
8. **Profitability Analytics** - Track approved/rejected discounts

### NEW: Rebate Programs Tab

**Created UI Components:**

#### **1. Create New Rebate Section**
```
┌──────────────────────────────────┐
│ Create New Rebate Program        │
├──────────────────────────────────┤
│ Customer ID:      [CUST-12345  ] │
│ SKU ID:           [SKU-001     ] │
│ Target Spend:     [5000.00     ] │
│ Rebate Percent:   [5.0         ] │
│ [Create Rebate Program]         │
└──────────────────────────────────┘
```

**Action:**
- Validates numeric inputs
- Calls `rebateProgramManager.createRebateProgram()`
- Shows programId in success dialog
- Clears forms and refreshes program list

#### **2. Record Purchase Section**
```
┌──────────────────────────────────┐
│ Record Purchase                  │
├──────────────────────────────────┤
│ Program ID:        [RBT-1      ] │
│ Purchase Amount:   [1200.00    ] │
│ [Record Purchase]               │
└──────────────────────────────────┘
```

**Action:**
- Looks up program by ID
- Calls `rebateProgramManager.recordPurchase()`
- Updates accumulated spend in database
- Refreshes program list to show new totals
- Could trigger rebate earned notifications

#### **3. Active Rebate Programs  Section**
```
┌─────────────────────────────────────────────────────────────┐
│ Active Rebate Programs                                      │
├─────────────────────────────────────────────────────────────┤
│ Program ID │ Customer    │ SKU       │ Progress  │ Rebate   │
├─────────────────────────────────────────────────────────────┤
│ RBT-1      │ CUST-ACME   │ SKU-001   │ 5300/5000 │ $265.00  │
│ RBT-2      │ CUST-BETA   │ SKU-002   │ 1500/2000 │ –        │
│ RBT-3      │ CUST-ACME   │ SKU-003   │ 1200/1000 │ $36.00   │
└─────────────────────────────────────────────────────────────┘
```

### Code Location

**File:** [PricingSubsystemGUI.java](pricing/src/main/java/com/pricingos/pricing/gui/PricingSubsystemGUI.java)

**Key Methods:**
- `initializeSubsystemAPIs()` - Initializes `rebateProgramManager`
- `createRebateProgramPanel()` - Creates UI panel (NEW)
- `refreshRebatePrograms()` - Loads data from database (NEW)

---

## RUN_GUI.bat Breakdown

**File:** [RUN_GUI.bat](RUN_GUI.bat)

```batch
@echo off
echo Starting Pricing Subsystem GUI...
echo Connecting to MySQL database OOAD...

cd /d "%~dp0\pricing"
```
- Changes to `pricing` subdirectory
- `%~dp0` = drive + path of batch file
- `/d` = allow drive letter change (important for Windows)

```batch
echo Resolving maven dependencies...
call mvn dependency:build-classpath -DincludeScope=runtime -Dmdep.outputFile=cp.txt
```
- Executes Maven to build runtime classpath
- `-DincludeScope=runtime` = include only runtime dependencies (not test)
- `-Dmdep.outputFile=cp.txt` = write classpath to `cp.txt` file
- Example output in `cp.txt`:
  ```
  C:\Users\user\.m2\repository\junit\junit\4.13.2\junit-4.13.2.jar;
  C:\Users\user\.m2\repository\org\hamcrest\hamcrest-core\1.3\hamcrest-core-1.3.jar;
  ...
  ```

```batch
set /p MAVEN_CP=<cp.txt
```
- Reads entire contents of `cp.txt` into `MAVEN_CP` variable
- Example: `MAVEN_CP=lib1.jar;lib2.jar;lib3.jar;...`

```batch
echo Launching via Java...
java -Ddb.url=jdbc:mysql://localhost:3306/OOAD \
     -Ddb.username=root \
     -Ddb.password=1977 \
     -cp "target\classes;..\lib\*;..\common\target\classes;..\resources;%MAVEN_CP%" \
     com.pricingos.pricing.gui.PricingSubsystemGUI
```

**JVM Arguments:**
- `-Ddb.url=...` ← MySQL connection string
- `-Ddb.username=root` ← MySQL user
- `-Ddb.password=1977` ← MySQL password (⚠️ NOT recommended for production!)

**Classpath Components:**
```
-cp components:
  1. target\classes                    ← Compiled .class files from this module
  2. ..\lib\*                          ← External JARs in ../lib folder
  3. ..\common\target\classes          ← Common module compiled classes
  4. ..\resources                      ← Resource files (database.properties)
  5. %MAVEN_CP%                        ← All Maven dependencies
```

**Main Class:**
- `com.pricingos.pricing.gui.PricingSubsystemGUI` ← Calls `main()` method

```batch
pause
```
- Keeps command window open after execution
- Press any key to close

### Actual Execution Flow

```
1. cd to pricing directory
2. Run: mvn dependency:build-classpath
3. Collect all runtime dependencies
4. Write to cp.txt
5. Read cp.txt into MAVEN_CP variable
6. Build complete classpath:
   - Compiled classes: target\classes
   - Common module: ..\common\target\classes
   - Dependencies: from Maven
   - Resources: ..\resources
7. Execute Java:
   - Set database connection properties
   - Load all classes from classpath
   - Instantiate PricingSubsystemGUI
   - Display Swing window
8. Wait for user keypress
```

### What Gets Loaded

```
PricingSubsystemGUI
  ├── DatabaseConnectionPool (Singleton)
  ├── PromotionManager
  ├── RebateProgramManager (NEW)
  ├── PriceListManager
  ├── ApprovalWorkflowEngine
  │   ├── ApprovalRoutingStrategy
  │   ├── IApproverRoleService
  │   ├── IFloorPriceService
  │   └── ObserverList
  │       ├── AuditLogObserver
  │       └── ProfitabilityAnalyticsObserver
  └── 7 UI Tabs
      ├── Price List
      ├── Tier Definitions
      ├── Promotions (DB)
      ├── Promo Code Manager
      ├── Rebate Programs
      ├── Price Calculator
      ├── Approval Workflows
      └── Profitability Analytics
```

---

## Architecture Patterns

### Design Patterns Used

#### **1. Observer Pattern**
**In:** Approval Workflow

```
ApprovalWorkflowEngine (Subject)
    ├── addObserver(ApprovalEventObserver)
    ├── removeObserver(ApprovalEventObserver)
    └── notifyObservers()
        ├── AuditLogObserver
        ├── ProfitabilityAnalyticsObserver
        └── Custom Observers (extensible)
```

**Benefits:**
- Decouples approval logic from side effects
- Easy to add new observers (email, Slack, metrics, etc.)
- Each observer independent implementation

#### **2. Strategy Pattern**
**In:** Approval Routing

```
ApprovalRoutingStrategy (Strategy)
    ├── resolveApproverId()
    ├── requiresDualApproval()
    
Concrete Strategies:
    ├── ManagerBasedRouter
    ├── AmountBasedRouter
    ├── TypeBasedRouter
    └── Custom routers
```

**Benefits:**
- Pluggable routing algorithms
- Change routing logic without modifying engine
- Easy to test different strategies

#### **3. Singleton Pattern**
**In:** Database Connection

```java
DatabaseConnectionPool {
    private static DatabaseConnectionPool instance;
    
    public static synchronized DatabaseConnectionPool getInstance() {
        if (instance == null) {
            instance = new DatabaseConnectionPool();
        }
        return instance;
    }
}
```

**Use:** Ensures single database connection pool across application

#### **4. DAO Pattern (Data Access Object)**
**In:** Database Layer

```
ApprovalRequestDao
    ├── save(ApprovalRequest)
    ├── get(approvalId, clock)
    ├── findAll(clock)
    
RebateDao
    ├── save(ProgramState)
    ├── get(programId)
    ├── has(programId)
```

### SOLID Principles

#### **Single Responsibility**
- `ApprovalWorkflowEngine` → orchestration only
- `ApprovalRoutingStrategy` → routing logic only
- `AuditLogObserver` → audit logging only
- `ProfitabilityAnalyticsObserver` → analytics only

#### **Open/Closed**
- `ApprovalEventObserver` interface open for extension
- `ApprovalRoutingStrategy` open for new implementations
- No need to modify engine to add observers/routers

#### **Liskov Substitution**
- Any `ApprovalRoutingStrategy` implementation can replace another
- Any `ApprovalEventObserver` implementation works interchangeably
- Maintains contract expectations

#### **Interface Segregation**
- `IApprovalWorkflowService` → workflow operations
- `IApproverRoleService` → authorization operations
- `IFloorPriceService` → margin validation
- Clients depend only on needed interfaces

#### **Dependency Inversion**
```java
// Good: Depends on interface
ApprovalWorkflowEngine(ApprovalRoutingStrategy strategy, IApproverRoleService roleService)

// Bad would be: Depends on concrete class
ApprovalWorkflowEngine(ManagerBasedRouter router, SpecificRoleService roleService)
```

---

## How to Demonstrate It Works

### 1. **Approval Workflow Demo**

**Script:**
```
1. Open GUI → Approval Workflows tab
2. Create override request (Price Calculator tab)
   - SKU: SKU-APPLE-001
   - Discount: $50
3. In Approval Workflows:
   - See request in PENDING status
   - Click "Approve Request"
   - Status changes to APPROVED
4. Check Profitability Analytics tab
   - See approved discount recorded ($50)
   - Total Revenue Impact updates
```

### 2. **Rebate Program Demo**

**Script:**
```
1. Open GUI → Rebate Programs tab
2. Create rebate program:
   - Customer: CUST-DEMO
   - SKU: SKU-BANANA-001
   - Target: $1000
   - Rebate: 5%
3. Record purchases:
   - First purchase: $400 → Progress: 40%
   - Second purchase: $350 → Progress: 75%
   - Third purchase: $300 → Progress: 105% ✓ EARNED $35 REBATE
4. Verify in database
5. Can see rebate earned in UI
```

### 3. **Escalation Demo**

Run approval workflow with simulated time passage:
```java
// In test:
ApprovalWorkflowEngine engine = new ApprovalWorkflowEngine(
    strategy, roleService, 
    Clock.fixed(Instant.now(), ZoneId.systemDefault())
);

// Submit request at time T
String approvalId = engine.submitOverrideRequest(...);

// Simulate 48+ hours passing
Clock laterClock = Clock.fixed(
    Instant.now().plus(Duration.ofHours(49)), 
    ZoneId.systemDefault()
);

// Trigger escalation
engine.escalateStaleRequests();

// Verify status changed to ESCALATED
```

---

## Database Tables

### Approval Tables

```sql
CREATE TABLE approval_requests (
    approval_id VARCHAR(50) PRIMARY KEY,
    request_type VARCHAR(30),
    requested_by VARCHAR(100),
    order_id VARCHAR(100),
    requested_discount_amt DECIMAL(10,2),
    justification_text TEXT,
    submission_time TIMESTAMP,
    status VARCHAR(20),
    routed_to_approver_id VARCHAR(100),
    approving_manager_id VARCHAR(100),
    approval_timestamp TIMESTAMP,
    escalation_time TIMESTAMP,
    rejection_reason TEXT
);

CREATE TABLE audit_log (
    id INT AUTO_INCREMENT PRIMARY KEY,
    approval_id VARCHAR(50),
    timestamp TIMESTAMP,
    event_type VARCHAR(50),
    actor VARCHAR(100),
    detail TEXT
);

CREATE TABLE profitability_analytics (
    id INT AUTO_INCREMENT PRIMARY KEY,
    approval_id VARCHAR(50),
    request_type VARCHAR(30),
    discount_amount DECIMAL(10,2),
    final_status VARCHAR(20),
    recorded_at TIMESTAMP
);
```

### Rebate Tables

```sql
CREATE TABLE rebate_programs (
    program_id VARCHAR(50) PRIMARY KEY,
    customer_id VARCHAR(100),
    sku_id VARCHAR(100),
    target_spend DECIMAL(10,2),
    rebate_pct DECIMAL(5,2),
    accumulated_spend DECIMAL(10,2),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE rebate_transactions (
    id INT AUTO_INCREMENT PRIMARY KEY,
    program_id VARCHAR(50),
    transaction_date TIMESTAMP,
    purchase_amount DECIMAL(10,2),
    rebate_amount_earned DECIMAL(10,2)
);
```

---

## Conclusion

The Approval Workflow Engine and Rebate Program Manager demonstrate:

✅ **Enterprise Patterns**: Observer, Strategy, Singleton, DAO
✅ **SOLID Principles**: All five principles properly applied  
✅ **Thread Safety**: Synchronized methods for concurrent access
✅ **Persistence**: Database-backed state management
✅ **Extensibility**: Easy to add new observers, routers, strategies
✅ **Auditability**: Complete audit trail of all decisions
✅ **Compliance**: Role-based authorization with margin protection
✅ **Analytics**: Track financial impact of approval decisions

