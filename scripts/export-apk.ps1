#Requires -Version 5.1
<#
.SYNOPSIS
    Build and export a Parkiroid APK to the exports folder.

.DESCRIPTION
    Runs Gradle assembleDebug or assembleRelease, then copies the APK to
    exports/ with a versioned filename.

.PARAMETER Variant
    Build variant: Debug (default, auto-signed) or Release (unsigned unless
    signing is configured in app/build.gradle.kts).

.PARAMETER OutputDirectory
    Folder for the exported APK. Defaults to exports/ at the repo root.

.PARAMETER Clean
    Run a clean build before assembling the APK.

.EXAMPLE
    .\scripts\export-apk.ps1

.EXAMPLE
    .\scripts\export-apk.ps1 -Variant Release -Clean
#>
[CmdletBinding()]
param(
    [ValidateSet('Debug', 'Release')]
    [string] $Variant = 'Debug',

    [string] $OutputDirectory = '',

    [switch] $Clean
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$projectRoot = Resolve-Path (Join-Path $PSScriptRoot '..')
$gradlew = Join-Path $projectRoot 'gradlew.bat'

if (-not (Test-Path $gradlew)) {
    Write-Error "Gradle wrapper not found at $gradlew"
}

if ([string]::IsNullOrWhiteSpace($OutputDirectory)) {
    $OutputDirectory = Join-Path $projectRoot 'exports'
}

$OutputDirectory = [System.IO.Path]::GetFullPath($OutputDirectory)
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null

$gradleArgs = @('--no-daemon')
if ($Clean) {
    $gradleArgs += 'clean'
}

$assembleTask = if ($Variant -eq 'Release') { 'assembleRelease' } else { 'assembleDebug' }
$gradleArgs += $assembleTask

Write-Host "Building $Variant APK..."
Push-Location $projectRoot
try {
    & $gradlew @gradleArgs
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle exited with code $LASTEXITCODE"
    }
}
finally {
    Pop-Location
}

$apkSearchRoot = Join-Path $projectRoot 'app\build\outputs\apk'
$variantFolder = if ($Variant -eq 'Release') { 'release' } else { 'debug' }
$apkDirectory = Join-Path $apkSearchRoot $variantFolder

if (-not (Test-Path $apkDirectory)) {
    Write-Error "APK output folder not found: $apkDirectory"
}

$apkCandidates = @(
    Get-ChildItem -Path $apkDirectory -Filter '*.apk' -File |
        Sort-Object LastWriteTime -Descending
)

if ($apkCandidates.Count -eq 0) {
    Write-Error "No APK found in $apkDirectory"
}

$sourceApk = $apkCandidates[0].FullName
$versionName = 'unknown'

$buildGradlePath = Join-Path $projectRoot 'app\build.gradle.kts'
if (Test-Path $buildGradlePath) {
    $buildGradle = Get-Content -Raw -Path $buildGradlePath
    if ($buildGradle -match 'versionName\s*=\s*"([^"]+)"') {
        $versionName = $Matches[1]
    }
}

$timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$variantLabel = $Variant.ToLowerInvariant()
$exportFileName = "parkiroid-$variantLabel-v$versionName-$timestamp.apk"
$destinationApk = Join-Path $OutputDirectory $exportFileName

Copy-Item -Path $sourceApk -Destination $destinationApk -Force

Write-Host ''
Write-Host 'Export complete.'
Write-Host "  Source:      $sourceApk"
Write-Host "  Destination: $destinationApk"
Write-Host ''
Write-Host 'Install on a connected device:'
Write-Host "  adb install -r `"$destinationApk`""

if ($Variant -eq 'Release' -and ($sourceApk -match 'unsigned')) {
    Write-Host ''
    Write-Host 'Note: Release APK is unsigned. Configure signing in app/build.gradle.kts' `
        -ForegroundColor Yellow
    Write-Host '      or sign manually before distributing.' -ForegroundColor Yellow
}
