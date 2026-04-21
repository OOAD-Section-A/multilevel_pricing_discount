@echo off
REM Windows Event Log Source Registration for SCM Database Design Subsystem
REM This script MUST be run as Administrator (one-time setup)
REM
REM Purpose: Enables Windows Event Viewer to display exceptions from the 
REM          SCM Exception Handler subsystem

echo.
echo ============================================================
echo SCM Event Log Source Registration (Windows Only)
echo ============================================================
echo.

REM Check if running as Administrator
net session >nul 2>&1
if %errorlevel% neq 0 (
    echo ERROR: This script must be run as Administrator
    echo.
    echo How to fix:
    echo   1. Right-click on "Command Prompt"
    echo   2. Select "Run as administrator"
    echo   3. Run this script again
    echo.
    pause
    exit /b 1
)

echo This is a one-time setup to enable Windows Event Viewer integration.
echo.
echo What this does:
echo   - Registers "SCM-DatabaseDesign" as an Event Log source
echo   - Allows exceptions to appear in Event Viewer
echo   - Required for exception visualization
echo.

setlocal enabledelayedexpansion

echo Registering Event Log source...
echo.

reg add "HKLM\SYSTEM\CurrentControlSet\Services\EventLog\Application\SCM-DatabaseDesign" ^
        /v EventMessageFile ^
        /t REG_SZ ^
        /d "%SystemRoot%\System32\EventCreate.exe" ^
        /f

if %errorlevel% equ 0 (
    echo.
    echo SUCCESS: Event Log source registered successfully!
    echo.
    echo You can now:
    echo   1. Run RUN_GUI.bat to start the Pricing Subsystem
    echo   2. Trigger exceptions in the GUI
    echo   3. View them in Windows Event Viewer:
    echo      - Press Win+R
    echo      - Type: eventvwr
    echo      - Navigate: Windows Logs > Application
    echo      - Filter by Event sources: SCM-DatabaseDesign
    echo.
) else (
    echo.
    echo ERROR: Failed to register Event Log source (error code: %errorlevel%)
    echo.
    echo Troubleshooting:
    echo   1. Verify this script is running as Administrator
    echo   2. Check that %SystemRoot%\System32\EventCreate.exe exists
    echo   3. Try manually in Administrator Command Prompt:
    echo      reg add "HKLM\SYSTEM\CurrentControlSet\Services\EventLog\Application\SCM-DatabaseDesign" /v EventMessageFile /t REG_SZ /d "%%SystemRoot%%\System32\EventCreate.exe" /f
    echo.
)

pause
