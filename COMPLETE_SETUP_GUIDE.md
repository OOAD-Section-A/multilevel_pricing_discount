# Complete Setup & Startup Guide

## Overview

This guide ensures your Pricing Subsystem GUI runs correctly with:
- ✅ Automatic database schema bootstrap
- ✅ Proper database adapter usage
- ✅ Exception handling with Windows Event Viewer
- ✅ Full compliance with database module best practices

---

## Part 1: Initial Setup (One-Time)

### Step 1: Install MySQL Server
If not already installed:
- Download MySQL Community Server: https://dev.mysql.com/downloads/mysql/
- Install with default settings (port 3306)
- Create user with credentials used in `RUN_GUI.bat`:
  - Username: `root`
  - Password: `1977`
  - Privileges: `CREATE`, `SELECT`, `INSERT`, `UPDATE`, `DELETE`

### Step 2: Install Java 17+
If not already installed:
- Download JDK 17+: https://www.oracle.com/java/technologies/downloads/
- Set `JAVA_HOME` environment variable:
  ```batch
  setx JAVA_HOME "C:\Program Files\Java\jdk-17"
  ```
- Verify installation:
  ```batch
  java -version
  ```

### Step 3: Install Maven
If not already installed:
- Download Maven: https://maven.apache.org/download.cgi
- Extract to `C:\Program Files\Apache\maven` (or similar)
- Add to PATH:
  ```batch
  setx PATH "%PATH%;C:\Program Files\Apache\maven\bin"
  ```
- Verify installation:
  ```batch
  mvn --version
  ```

### Step 4: Verify All JARs in `/lib` Folder
The following files should exist in the `lib/` folder relative to this project root:
- `scm-exception-handler-v3.jar` ✓
- `scm-exception-viewer-gui.jar` ✓
- `jna-5.18.1.jar` ✓
- `jna-platform-5.18.1.jar` ✓
- `database-module-1.0.0-SNAPSHOT-standalone.jar` ✓

### Step 5: Register Windows Event Log Source (Windows Only, Admin Required)
Run this **once as Administrator** to enable exception viewing in Windows Event Viewer:

```batch
SETUP_EVENT_LOG_SOURCE.bat
```

Or manually:
```batch
reg add "HKLM\SYSTEM\CurrentControlSet\Services\EventLog\Application\SCM-DatabaseDesign" /v EventMessageFile /t REG_SZ /d "%SystemRoot%\System32\EventCreate.exe" /f
```

---

## Part 2: Database Initialization

### First Run - Automatic Schema Bootstrap
**Important**: You do NOT need to manually run `schema.sql`

When you run `RUN_GUI.bat` for the first time:
1. The GUI connects via `SupplyChainDatabaseFacade`
2. Facade automatically:
   - Creates database `OOAD` if it doesn't exist
   - Applies schema.sql if needed
   - Sets up connection pool
   - Initializes adapters

**That's all that's needed!** No manual SQL execution required.

### Optional: Manual Database Reset
If you need a clean database state:

```batch
REM Option 1: Using DatabaseSetup utility
cd pricing
java -cp target/classes;../lib/* com.pricingos.pricing.db.DatabaseSetup
cd ..
REM Restart RUN_GUI.bat

REM Option 2: Using MySQL directly
mysql -u root -p1977 -e "DROP DATABASE OOAD;"
REM Restart RUN_GUI.bat (will auto-bootstrap)
```

---

## Part 3: Running the GUI

### Standard Startup
```batch
RUN_GUI.bat
```

This script automatically:
1. ✅ Resolves Maven dependencies
2. ✅ Includes all exception handler JARs
3. ✅ Sets database credentials
4. ✅ Initializes SupplyChainDatabaseFacade
5. ✅ Launches the GUI

### Expected Output
```
============================================================
Starting Pricing Subsystem GUI...
============================================================

[1/4] Checking prerequisites...
[2/4] Resolving Maven dependencies...
[3/4] Verifying exception handler JARs...
[4/4] Launching GUI...

GUI window appears with tabs:
- Price List
- Tier Definitions
- Promotions (DB)
- Rebate Programs
- Price Calculator
- Approval Workflows
- ... (more tabs)
```

---

## Part 4: Verify Exception Handling Works

### Test Exception Popup
The exception handler shows popups with format:
```
Exception ID: <id>
Name: <exception_name>
Message: <error_message>
Subsystem: SCM-Pricing
```

### View Exceptions in Exception Viewer
1. Run `RUN_EXCEPTION_VIEWER.bat` (from project root)
2. Select "Pricing Subsystem" from dropdown
3. Minimize and maximize to refresh

### View Exceptions in Windows Event Viewer
1. Press `Win + R`
2. Type `eventvwr` and press Enter
3. Navigate to: Windows Logs > Application
4. Look for events with Event sources filter: `SCM-DatabaseDesign`

---

## Part 5: Verify Database Connection

### Check MySQL Connectivity
```batch
mysql -h localhost -u root -p1977 -e "SELECT 1;"
```
Expected output: `1`

### Check Database Exists
```batch
mysql -h localhost -u root -p1977 -e "SHOW DATABASES;" | findstr OOAD
```
Expected: Row showing `OOAD`

### Check Schema Tables
```batch
mysql -h localhost -u root -p1977 OOAD -e "SHOW TABLES;"
```
Expected: List of all tables created from schema.sql

---

## Part 6: Configuration Files

### Main Configuration: database.properties
Location: `pricing/src/main/resources/database.properties`
```properties
db.url=jdbc:mysql://localhost:3306/OOAD
db.username=root
db.password=1977
db.pool.size=5
```

### GUI Configuration: scm-gui.properties
Location: `lib/scm-gui.properties`
- Contains GUI settings
- Automatically loaded by exception handler

---

## Troubleshooting

### Problem: "Failed to connect to database"
**Cause**: MySQL not running or credentials incorrect  
**Solution**:
```batch
REM Start MySQL
net start MySQL80
REM Or manually start MySQL Service from Services

REM Test connectivity
mysql -h localhost -u root -p1977 -e "SELECT 1;"
```

### Problem: "Access denied for user 'root'@'localhost'"
**Cause**: Wrong password in RUN_GUI.bat or database.properties  
**Solution**:
```batch
REM Verify MySQL password
mysql -h localhost -u root -p
REM Enter password: 1977

REM Update if needed:
REM 1. Edit RUN_GUI.bat: -Ddb.password=1977
REM 2. Edit database.properties: db.password=1977
```

### Problem: "Exception handler GUI shows nothing"
**Cause**: Event Log source not registered  
**Solution**:
```batch
REM Run as Administrator:
SETUP_EVENT_LOG_SOURCE.bat
REM Or manual command:
reg add "HKLM\SYSTEM\CurrentControlSet\Services\EventLog\Application\SCM-DatabaseDesign" /v EventMessageFile /t REG_SZ /d "%SystemRoot%\System32\EventCreate.exe" /f
```

### Problem: "Java not found"
**Cause**: Java not installed or not in PATH  
**Solution**:
```batch
REM Check if Java is installed
java -version

REM If not found, install from:
REM https://www.oracle.com/java/technologies/downloads/

REM Set JAVA_HOME
setx JAVA_HOME "C:\Program Files\Java\jdk-17"

REM Restart command prompt and try again
```

### Problem: "Maven command not found"
**Cause**: Maven not installed or not in PATH  
**Solution**:
```batch
REM Check if Maven is installed
mvn --version

REM If not found, install from:
REM https://maven.apache.org/download.cgi

REM Add to PATH or use full path in RUN_GUI.bat
```

### Problem: "JAR not found" in lib folder
**Cause**: Exception handler JARs missing  
**Solution**:
```batch
REM Verify all JARs exist
dir lib\scm-*.jar
dir lib\jna-*.jar
dir lib\database-module-*.jar

REM If missing, copy from distribution or rebuild
```

### Problem: "Cannot find main class: com.pricingos.pricing.gui.PricingSubsystemGUI"
**Cause**: Classpath not set correctly  
**Solution**:
```batch
REM Verify Maven classpath file was created
cd pricing
dir cp.txt
REM File should exist with classpath contents

REM Rebuild if missing:
mvn clean dependency:build-classpath -DincludeScope=runtime -Dmdep.outputFile=cp.txt
cd ..

REM Try RUN_GUI.bat again
```

---

## Architecture: What Happens at Runtime

```
RUN_GUI.bat
├─ Maven resolves dependencies
│  └─ Creates cp.txt with Maven libraries
│
├─ Builds Java classpath:
│  ├─ target/classes (compiled GUI code)
│  ├─ lib/* (all JARs including exception handler)
│  ├─ ../common/target/classes (common module)
│  ├─ ../resources (configuration)
│  └─ %MAVEN_CP% (Maven dependencies)
│
├─ Starts JVM with properties:
│  ├─ db.url=jdbc:mysql://localhost:3306/OOAD
│  ├─ db.username=root
│  └─ db.password=1977
│
├─ PricingSubsystemGUI initializes:
│  ├─ initializeDatabaseConnection()
│  │  └─ SupplyChainDatabaseFacade
│  │     ├─ Reads database.properties
│  │     ├─ Connects to MySQL
│  │     ├─ Creates OOAD database (if not exists)
│  │     └─ Applies schema.sql (if needed)
│  │
│  ├─ initializeSubsystemAPIs()
│  │  ├─ PromotionManager
│  │  ├─ PriceListManager
│  │  ├─ ApprovalWorkflowEngine
│  │  ├─ RebateProgramManager
│  │  └─ other subsystem components
│  │
│  ├─ getExceptions() (Windows only)
│  │  └─ MultiLevelPricingSubsystem
│  │     ├─ Loads exception handler JARs
│  │     ├─ Registers Windows Event Viewer listener
│  │     └─ Prepares for exception capture
│  │
│  └─ initializeUI()
│     └─ Displays tabbed interface with all features
│
└─ GUI Ready for use
   ├─ All database operations through PricingAdapter
   ├─ All exceptions logged to Windows Event Viewer (Windows only)
   └─ Data persisted to MySQL via SupplyChainDatabaseFacade
```

---

## Security Notes

⚠️ **Warning**: Credentials are visible in `RUN_GUI.bat`

For production environments:
1. Use environment variables instead of `-D` properties:
   ```batch
   set DB_URL=jdbc:mysql://localhost:3306/OOAD
   set DB_USER=safe_user
   set DB_PASS=secure_password
   ```
2. Use database.properties in secure location with restricted permissions
3. Use connection encryption: `useSSL=true&requireSSL=true`
4. Restrict MySQL user to specific database and operations

---

## References

- **Database Module**: SupplyChainDatabaseFacade
- **Exception Handler**: MultiLevelPricingSubsystem
- **Configuration**: database.properties, scm-gui.properties
- **Support Scripts**: RUN_GUI.bat, RUN_EXCEPTION_VIEWER.bat, SETUP_EVENT_LOG_SOURCE.bat
- **Documentation**: SCHEMA_SETUP_COMPLIANCE.md

---

## Summary Checklist ✓

- [ ] MySQL Server installed and running
- [ ] Java 17+ installed and in PATH
- [ ] Maven installed and in PATH
- [ ] All JARs in `lib/` folder
- [ ] Windows Event Log source registered (Windows only)
- [ ] database.properties configured (optional but recommended)
- [ ] RUN_GUI.bat is executable
- [ ] First run: Verify database schema was created automatically
- [ ] Exceptions appear in Exception Viewer GUI
- [ ] Exceptions appear in Windows Event Viewer (Windows only)

✅ **Ready to launch**: `RUN_GUI.bat`
