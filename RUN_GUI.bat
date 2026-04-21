@echo off
setlocal EnableExtensions
echo Starting Pricing Subsystem GUI...
echo Connecting to MySQL database OOAD...

cd /d "%~dp0"
if "%DB_URL%"=="" set "DB_URL=jdbc:mysql://localhost:3306/OOAD"
if "%DB_USERNAME%"=="" set "DB_USERNAME=root"
if "%DB_PASSWORD%"=="" set "DB_PASSWORD="
if "%PRICING_SEED_DEMO_DATA%"=="" set "PRICING_SEED_DEMO_DATA=false"

set "JAVA_CMD="
if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\java.exe" (
        set "JAVA_CMD=%JAVA_HOME%\bin\java.exe"
    )
)
if not defined JAVA_CMD (
    where java >nul 2>nul
    if %errorlevel% equ 0 (
        set "JAVA_CMD=java"
    )
)
if not defined JAVA_CMD (
    echo ERROR: Java not found. Set JAVA_HOME or install Java 17+.
    pause
    exit /b 1
)

where mvn >nul 2>nul
if %errorlevel% neq 0 (
    echo ERROR: Maven not found. Install Maven or add mvn to PATH.
    pause
    exit /b 1
)

if /I "%PRICING_SEED_DEMO_DATA%"=="true" (
    echo Demo data seeding is enabled for this run.
)

echo Building project...
call mvn -q -DskipTests package
if %errorlevel% neq 0 exit /b %errorlevel%

echo Launching via Java...
"%JAVA_CMD%" "-Ddb.url=%DB_URL%" "-Ddb.username=%DB_USERNAME%" "-Ddb.password=%DB_PASSWORD%" "-Dpricing.seed.demo=%PRICING_SEED_DEMO_DATA%" -cp "common\target\classes;pricing\target\classes;lib\*" com.pricingos.pricing.gui.PricingSubsystemGUI

if %errorlevel% neq 0 (
    echo.
    echo ERROR: Failed to start Pricing Subsystem GUI
    echo Error code: %errorlevel%
)

pause
endlocal
