Param(
    [string]$JavaVersion = "17"
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot

Write-Host "Checking Java..."
$javaOk = $false
try {
    $javaVersionOutput = & java -version 2>&1
    if ($LASTEXITCODE -eq 0) {
        $javaOk = $true
        Write-Host "Java found:"
        $javaVersionOutput | Select-Object -First 1
    }
} catch {
    $javaOk = $false
}

if (-not $javaOk) {
    throw "Java is required. Install JDK $JavaVersion or newer and ensure java is on PATH."
}

Write-Host "Checking Maven..."
$mvnOk = $false
try {
    $mvnVersionOutput = & mvn -version 2>&1
    if ($LASTEXITCODE -eq 0) {
        $mvnOk = $true
        Write-Host "Maven found:"
        $mvnVersionOutput | Select-Object -First 1
    }
} catch {
    $mvnOk = $false
}

if (-not $mvnOk) {
    throw "Maven is required. Install Maven 3.9+ and ensure mvn is on PATH."
}

Set-Location $root
Write-Host "Building Java server..."
& mvn -q clean package

if ($LASTEXITCODE -ne 0) {
    throw "Build failed."
}

$jar = Join-Path $root "target/restprovider-java-server-1.0.0.jar"
Write-Host "Build successful."
Write-Host "Run with: java -jar $jar"
