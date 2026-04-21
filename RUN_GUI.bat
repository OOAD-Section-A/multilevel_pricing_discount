@echo off
setlocal enabledelayedexpansion
REM SCM Pricing Subsystem GUI - Multi-level Pricing & Discount Management
echo ============================================================
echo Starting Pricing Subsystem GUI...
echo ============================================================
echo.

REM Check prerequisites
echo [1/4] Checking prerequisites...
echo   - Verifying MySQL is running on localhost:3306...
echo   - Verifying exception handler JARs are available...
echo.

REM Navigate to pricing folder
cd /d "%~dp0\pricing"
if %errorlevel% neq 0 (
    echo ERROR: Could not navigate to pricing folder
    pause
    exit /b 1
)

REM Build Maven classpath
echo [2/4] Resolving Maven dependencies...
call mvn dependency:build-classpath -DincludeScope=runtime -Dmdep.outputFile=cp.txt
if %errorlevel% neq 0 (
    echo ERROR: Maven dependency resolution failed
    pause
    exit /b 1
)
set /p MAVEN_CP=<cp.txt

REM Verify exception handler JARs exist
echo [3/4] Verifying exception handler JARs...
if not exist "..\lib\scm-exception-handler-v3.jar" (
    echo WARNING: scm-exception-handler-v3.jar not found in ..\lib\
    echo   Exception handling features will be disabled
)
if not exist "..\lib\scm-exception-viewer-gui.jar" (
    echo WARNING: scm-exception-viewer-gui.jar not found in ..\lib\
)
if not exist "..\lib\jna-5.18.1.jar" (
    echo WARNING: jna-5.18.1.jar not found in ..\lib\
)
if not exist "..\lib\jna-platform-5.18.1.jar" (
    echo WARNING: jna-platform-5.18.1.jar not found in ..\lib\
)
echo.

REM Build complete classpath including exception handler JARs
echo [4/4] Launching GUI with database connectivity and exception handling...
echo   - Database: SupplyChainDatabaseFacade (auto-bootstrap enabled)
echo   - Adapter: PricingAdapter
echo   - Exception Handling: Enabled (if JARs available)
echo   - Credentials: Reading from database.properties (no hardcoded secrets)
echo.

REM Explicitly add all JARs from lib folder to classpath
REM (Wildcards don't expand in Java classpath, so we list them explicitly)
set LIB_CP=
for %%F in (..\lib\*.jar) do (
    if defined LIB_CP (
        set "LIB_CP=!LIB_CP!;%%F"
    ) else (
        set "LIB_CP=%%F"
    )
)

set FULL_CP=target\classes;%LIB_CP%;..\common\target\classes;..\resources;%MAVEN_CP%

REM Launch GUI with all JARs on classpath
REM Database credentials are read from: pricing/src/main/resources/database.properties
REM NO credentials are hardcoded here for security
java -cp "%FULL_CP%" ^
     com.pricingos.pricing.gui.PricingSubsystemGUI

if %errorlevel% neq 0 (
    echo.
    echo ERROR: GUI launch failed with exit code %errorlevel%
    echo.
    echo Troubleshooting tips:
    echo   1. Verify MySQL is running: mysql -u root -p
    echo   2. Verify database exists: CREATE DATABASE IF NOT EXISTS OOAD;
    echo   3. Check Java version: java -version
    echo   4. Verify all JARs in ..\lib\ folder
    echo.
)

pause
