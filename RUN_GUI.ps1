# Run Pricing Subsystem GUI on Windows PowerShell
Write-Host "Starting Pricing Subsystem GUI..."
Write-Host "Connecting to MySQL database OOAD..."
Write-Host ""

$dbUrl = if ($env:DB_URL) { $env:DB_URL } else { "jdbc:mysql://localhost:3306/OOAD" }
$dbUsername = if ($env:DB_USERNAME) { $env:DB_USERNAME } else { "root" }
$dbPassword = if ($env:DB_PASSWORD) { $env:DB_PASSWORD } else { "" }
$seedDemo = if ($env:PRICING_SEED_DEMO_DATA) { $env:PRICING_SEED_DEMO_DATA } else { "false" }

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

$mavenCommand = Get-Command mvn -ErrorAction SilentlyContinue
if (-not $mavenCommand) {
    Write-Host "ERROR: Maven not found. Install Maven or add mvn to PATH."
    exit 1
}

if ($seedDemo -eq "true") {
    Write-Host "Demo data seeding is enabled for this run."
}

Push-Location $PSScriptRoot
try {
    Write-Host "Building project..."
    & $mavenCommand.Source -q -DskipTests package
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }

    Write-Host "Launching pricing GUI..."
    & $javaPath "-Ddb.url=$dbUrl" "-Ddb.username=$dbUsername" "-Ddb.password=$dbPassword" "-Dpricing.seed.demo=$seedDemo" -cp "common\target\classes;pricing\target\classes;lib\*" com.pricingos.pricing.gui.PricingSubsystemGUI
    if ($LASTEXITCODE -ne 0) {
        Write-Host ""
        Write-Host "ERROR: Failed to start Pricing Subsystem GUI"
        exit $LASTEXITCODE
    }
} finally {
    Pop-Location
}
