param([string]$JavaHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
if (-not $JavaHome) {
    $bundledJdk = Join-Path $PSScriptRoot '..\.tools\jdk\jdk-17.0.20.1+1'
    if (Test-Path -LiteralPath $bundledJdk) { $JavaHome = (Resolve-Path -LiteralPath $bundledJdk).Path }
}
if (-not $JavaHome -or -not (Test-Path -LiteralPath (Join-Path $JavaHome 'bin\java.exe'))) {
    throw 'Set JAVA_HOME or pass -JavaHome with a JDK 17 installation.'
}
if (-not (Test-Path -LiteralPath (Join-Path $PSScriptRoot 'local.properties'))) {
    throw 'Create local.properties from local.properties.example and set sdk.dir first.'
}
$previousJavaHome = $env:JAVA_HOME
Push-Location $PSScriptRoot
try {
    $env:JAVA_HOME = $JavaHome
    & .\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :preflight:assembleDebug :preflight:lintDebug --console=plain
    if ($LASTEXITCODE -ne 0) { throw "Gradle failed with exit code $LASTEXITCODE" }
} finally {
    $env:JAVA_HOME = $previousJavaHome
    Pop-Location
}
