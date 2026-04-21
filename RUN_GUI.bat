@echo off
setlocal
echo Starting Pricing Subsystem GUI...
echo Connecting to MySQL database OOAD...

cd /d "%~dp0"
if "%DB_URL%"=="" set "DB_URL=jdbc:mysql://localhost:3306/OOAD"
if "%DB_USERNAME%"=="" set "DB_USERNAME=root"
if "%DB_PASSWORD%"=="" set "DB_PASSWORD="
if "%PRICING_SEED_DEMO_DATA%"=="" set "PRICING_SEED_DEMO_DATA=false"

echo Building project...
call mvn -q -DskipTests package
if %errorlevel% neq 0 exit /b %errorlevel%

echo Launching via Java...
java -Ddb.url=%DB_URL% -Ddb.username=%DB_USERNAME% -Ddb.password=%DB_PASSWORD% -Dpricing.seed.demo=%PRICING_SEED_DEMO_DATA% -cp "common\target\classes;pricing\target\classes;lib\*" com.pricingos.pricing.gui.PricingSubsystemGUI

pause
endlocal
