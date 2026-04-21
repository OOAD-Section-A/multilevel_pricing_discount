# Schema Setup & Database Integration Compliance Check

## ✅ FOLLOWING BEST PRACTICES

### 1. Automatic Schema Bootstrap
- **Status**: ✅ IMPLEMENTED
- **How**: `SupplyChainDatabaseFacade` in `PricingSubsystemGUI.initializeDatabaseConnection()`
- **Details**: 
  - Facade automatically reads DB configuration
  - Connects to MySQL
  - Creates target database if it doesn't exist
  - Applies embedded schema.sql only if needed
  - Does NOT drop/recreate database on every run
- **Location**: [PricingSubsystemGUI.java](pricing/src/main/java/com/pricingos/pricing/gui/PricingSubsystemGUI.java#L148)

### 2. Using Correct Adapter
- **Status**: ✅ IMPLEMENTED
- **Adapter**: `PricingAdapter(dbFacade)`
- **Methods Used**: 
  - Promotional data retrieval via `pricingFacade`
  - Contract pricing queries
  - Tier evaluation through adapters
- **Location**: [PricingSubsystemGUI.java](pricing/src/main/java/com/pricingos/pricing/gui/PricingSubsystemGUI.java#L143)

### 3. Database Configuration
- **Status**: ✅ IMPLEMENTED
- **Methods**:
  - Command-line properties: `-Ddb.url`, `-Ddb.username`, `-Ddb.password`
  - Properties file: [database.properties](pricing/src/main/resources/database.properties)
  ```properties
  db.url=jdbc:mysql://localhost:3306/OOAD
  db.username=root
  db.password=1977
  db.pool.size=5
  ```

### 4. NOT Manually Running Schema
- **Status**: ✅ CORRECT
- **Details**:
  - `DatabaseSetup.java` exists but is NOT called in normal flow
  - Schema is managed by `SupplyChainDatabaseFacade`
  - Users don't need to manually run `schema.sql`

### 5. Exception Handling Integration
- **Status**: ✅ PARTIALLY IMPLEMENTED
- **What's Working**:
  - Exception handler initialization in `getExceptions()` method
  - Windows Event Viewer support (Windows-only)
  - Graceful fallback on non-Windows platforms
  - Exception logging with `MultiLevelPricingSubsystem`
- **Location**: [PricingSubsystemGUI.java](pricing/src/main/java/com/pricingos/pricing/gui/PricingSubsystemGUI.java#L39)
- **JARs Available** (in `lib/` folder):
  - ✅ `scm-exception-handler-v3.jar`
  - ✅ `scm-exception-viewer-gui.jar`
  - ✅ `jna-5.18.1.jar`
  - ✅ `jna-platform-5.18.1.jar`

---

## ⚠️ NEEDS ATTENTION

### 1. Event Log Source Registration
- **Status**: ⚠️ MANUAL STEP REQUIRED
- **What**: Windows Event Viewer integration requires one-time setup
- **Action**: Run in Administrator Command Prompt (once):
  ```batch
  reg add "HKLM\SYSTEM\CurrentControlSet\Services\EventLog\Application\SCM-DatabaseDesign" /v EventMessageFile /t REG_SZ /d "%SystemRoot%\System32\EventCreate.exe" /f
  ```
- **Why**: Enables Windows Event Viewer to display SCM exceptions
- **When**: One-time setup per machine
- **Note**: Windows machines only; Unix/Linux skip this

### 2. Exception Handler JAR Loading
- **Status**: ⚠️ UPDATED IN RUN_GUI.bat
- **What**: `RUN_GUI.bat` now includes exception handler JARs in classpath
- **Updated Classpath**:
  ```batch
  set FULL_CP=target\classes;..\lib\*;..\common\target\classes;..\resources;%MAVEN_CP%
  ```
- **Details**: `..\lib\*` includes all JARs:
  - `scm-exception-handler-v3.jar`
  - `scm-exception-viewer-gui.jar`
  - `jna-5.18.1.jar`
  - `jna-platform-5.18.1.jar`

### 3. MySQL Prerequisites
- **Status**: ⚠️ MUST BE MET AT STARTUP
- **Requirements**:
  - ✅ MySQL Server running on `localhost:3306`
  - ✅ User `root` with password `1977`
  - ✅ User has `CREATE` privilege (for database bootstrap)
  - ✅ User has `CREATE TABLE` privilege

---

## 📋 Pre-Launch Checklist

Before running `RUN_GUI.bat`, verify:

### Windows Users (Complete Setup)
- [ ] MySQL Server is running
  ```batch
  mysql -u root -p -e "SELECT 1;"
  ```
- [ ] Java 17+ is installed
  ```batch
  java -version
  ```
- [ ] Exception Handler JARs verify the Event Log Source (one-time):
  ```batch
  reg add "HKLM\SYSTEM\CurrentControlSet\Services\EventLog\Application\SCM-DatabaseDesign" /v EventMessageFile /t REG_SZ /d "%SystemRoot%\System32\EventCreate.exe" /f
  ```

### All Users
- [ ] MySQL credentials correct in `RUN_GUI.bat`: `root` / `1977`
- [ ] `..\lib\*` folder contains all 4 JARs
- [ ] Maven is installed and on PATH
- [ ] No other application running on MySQL port `3306`

---

## 🚀 What Happens at Startup

1. **Maven Resolution** (2-3 seconds)
   - Reads POM files
   - Downloads/resolves dependencies
   - Builds classpath

2. **Database Initialization** (1-2 seconds)
   - `SupplyChainDatabaseFacade` connects to MySQL
   - Checks if database `OOAD` exists
   - If not → Creates database
   - If needed → Applies schema.sql

3. **Exception Handler Initialization**
   - Attempts to load `MultiLevelPricingSubsystem`
   - If on Windows → Registers Event Log listener
   - If not on Windows → Silently skips (no error)

4. **GUI Launch**
   - Initializes all subsystem APIs
   - Loads data from database
   - Displays interface

---

## 🔧 Optional: Manual Schema Reset

If you need to manually reset and rebuild the schema:

```batch
REM Using DatabaseSetup.java utility (from project root)
cd pricing
java -cp target/classes;../lib/* com.pricingos.pricing.db.DatabaseSetup
cd ..
```

Or via MySQL directly:
```sql
DROP DATABASE OOAD;
CREATE DATABASE OOAD;
-- SupplyChainDatabaseFacade will auto-bootstrap schema on next run
```

---

## 📞 Troubleshooting

| Problem | Solution |
|---------|----------|
| "Failed to connect to database" | Check MySQL is running: `mysql -u root -p` |
| "Access denied for user 'root'" | Verify password is `1977` in RUN_GUI.bat and database.properties |
| Exception handler not responding | Run Event Log registration as Administrator |
| GUI won't start | Check Java version: `java -version` (should be 17+) |
| "JAR not found" warnings | Verify all 4 exception JARs are in `lib/` folder |
| "Database creation failed" | Verify `root` user has `CREATE` privilege on MySQL |

---

## 📝 Summary

**Your project CORRECTLY follows the documentation for:**
- ✅ Automatic schema bootstrap via `SupplyChainDatabaseFacade`
- ✅ Using `PricingAdapter` for operations
- ✅ NOT manually running schema.sql in normal flow
- ✅ Database configuration via properties

**Recently Updated:**
- ✅ `RUN_GUI.bat` now includes all exception handler JARs
- ✅ Better error messages and troubleshooting info in startup script

**Still Required:**
- ⚠️ Windows Event Log registration (one-time admin command)
- ⚠️ MySQL server must be running and accessible
