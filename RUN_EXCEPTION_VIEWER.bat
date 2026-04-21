@echo off
REM Exception Viewer GUI for SCM-Multi-levelPricing
echo Running Exception Viewer GUI for SCM-Multi-levelPricing...
echo.

REM Try to find Java installation
set JAVA_FOUND=0

REM Check if JAVA_HOME environment variable is set
if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\java.exe" (
        set JAVA_CMD=%JAVA_HOME%\bin\java.exe
        set JAVA_FOUND=1
        echo Using JAVA_HOME: %JAVA_HOME%
    )
)

REM If JAVA_HOME didn't work, try common Java install locations
if %JAVA_FOUND% equ 0 (
    if exist "C:\Program Files\Java\jdk-25\bin\java.exe" (
        set JAVA_CMD=C:\Program Files\Java\jdk-25\bin\java.exe
        set JAVA_FOUND=1
        echo Found Java at: C:\Program Files\Java\jdk-25
    )
)

if %JAVA_FOUND% equ 0 (
    if exist "C:\Program Files\Java\jdk-17\bin\java.exe" (
        set JAVA_CMD=C:\Program Files\Java\jdk-17\bin\java.exe
        set JAVA_FOUND=1
        echo Found Java at: C:\Program Files\Java\jdk-17
    )
)

if %JAVA_FOUND% equ 0 (
    if exist "C:\Program Files\Java\jdk-21\bin\java.exe" (
        set JAVA_CMD=C:\Program Files\Java\jdk-21\bin\java.exe
        set JAVA_FOUND=1
        echo Found Java at: C:\Program Files\Java\jdk-21
    )
)

if %JAVA_FOUND% equ 0 (
    if exist "C:\Program Files\Java\jdk-20\bin\java.exe" (
        set JAVA_CMD=C:\Program Files\Java\jdk-20\bin\java.exe
        set JAVA_FOUND=1
        echo Found Java at: C:\Program Files\Java\jdk-20
    )
)

if %JAVA_FOUND% equ 0 (
    echo ERROR: Java not found. Please set JAVA_HOME or install Java 17+
    pause
    exit /b 1
)

REM Navigate to lib folder where JARs are located
cd /d lib
if %errorlevel% neq 0 (
    echo ERROR: Could not navigate to lib folder
    pause
    exit /b 1
)

REM Verify all required JARs are present
echo.
echo Verifying JAR files...
if not exist "scm-exception-handler-v3.jar" (
    echo ERROR: scm-exception-handler-v3.jar not found
    pause
    exit /b 1
)
echo   OK: scm-exception-handler-v3.jar

if not exist "scm-exception-viewer-gui.jar" (
    echo ERROR: scm-exception-viewer-gui.jar not found
    pause
    exit /b 1
)
echo   OK: scm-exception-viewer-gui.jar

if not exist "jna-5.18.1.jar" (
    echo ERROR: jna-5.18.1.jar not found
    pause
    exit /b 1
)
echo   OK: jna-5.18.1.jar

if not exist "jna-platform-5.18.1.jar" (
    echo ERROR: jna-platform-5.18.1.jar not found
    pause
    exit /b 1
)
echo   OK: jna-platform-5.18.1.jar

echo.
echo Starting Exception Viewer GUI...
echo.

REM Run the GUI with all required JARs on classpath
"%JAVA_CMD%" -cp .;"scm-exception-handler-v3.jar";"scm-exception-viewer-gui.jar";"jna-5.18.1.jar";"jna-platform-5.18.1.jar" com.scm.gui.ExceptionViewerGUI

if %errorlevel% neq 0 (
    echo.
    echo ERROR: Failed to start Exception Viewer GUI
    echo Error code: %errorlevel%
    pause
)