# Run Exception Viewer GUI for SCM-Multi-levelPricing
Write-Host "Starting Exception Viewer GUI for SCM-Multi-levelPricing..."
Write-Host ""

# Set Java path
$javaPath = "C:\Program Files\Java\jdk-25\bin\java.exe"

# Verify Java exists
if (-not (Test-Path $javaPath)) {
    Write-Host "ERROR: Java not found at $javaPath"
    exit 1
}

# Navigate to lib folder
$libPath = "$PSScriptRoot\lib"
if (-not (Test-Path $libPath)) {
    Write-Host "ERROR: lib folder not found"
    exit 1
}

Set-Location $libPath

# Verify all JARs exist
$jars = @(
    "scm-exception-handler-v3.jar",
    "scm-exception-viewer-gui.jar",
    "jna-5.18.1.jar",
    "jna-platform-5.18.1.jar"
)

Write-Host "Verifying JAR files..."
foreach ($jar in $jars) {
    if (-not (Test-Path $jar)) {
        Write-Host "ERROR: $jar not found"
        exit 1
    }
    Write-Host "  OK: $jar"
}

Write-Host ""
Write-Host "Starting GUI..."
Write-Host ""

# Build classpath
$classpath = "." + ";" + ($jars -join ";")

# Run Java
& $javaPath -cp $classpath com.scm.gui.ExceptionViewerGUI
