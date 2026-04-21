# Run Exception Viewer GUI for SCM-Multi-levelPricing
Write-Host "Starting Exception Viewer GUI for SCM-Multi-levelPricing..."
Write-Host ""
Write-Host "Note: run the Event Viewer source registration command once as Administrator before first use."
Write-Host ""

# Resolve Java
$candidateJavaPaths = @()
if ($env:JAVA_HOME) {
    $candidateJavaPaths += (Join-Path $env:JAVA_HOME "bin\java.exe")
}
$candidateJavaPaths += @(
    "C:\Program Files\Java\jdk-25\bin\java.exe",
    "C:\Program Files\Java\jdk-21\bin\java.exe",
    "C:\Program Files\Java\jdk-17\bin\java.exe"
)

$javaPath = $candidateJavaPaths | Where-Object { Test-Path $_ } | Select-Object -First 1
if (-not $javaPath) {
    $javaCommand = Get-Command java -ErrorAction SilentlyContinue
    if ($javaCommand) {
        $javaPath = $javaCommand.Source
    }
}
if (-not $javaPath) {
    Write-Host "ERROR: Java not found. Set JAVA_HOME or install Java 17+."
    exit 1
}

# Navigate to lib folder
$libPath = "$PSScriptRoot\lib"
if (-not (Test-Path $libPath)) {
    Write-Host "ERROR: lib folder not found"
    exit 1
}

$jars = @(
    "scm-exception-handler-v3.jar",
    "scm-exception-viewer-gui.jar",
    "jna-5.18.1.jar",
    "jna-platform-5.18.1.jar"
)

Push-Location $libPath
try {
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

    $classpath = "." + ";" + ($jars -join ";")
    & $javaPath -cp $classpath com.scm.gui.ExceptionViewerGUI
    if ($LASTEXITCODE -ne 0) {
        Write-Host ""
        Write-Host "ERROR: Failed to start Exception Viewer GUI"
        exit $LASTEXITCODE
    }
} finally {
    Pop-Location
}
