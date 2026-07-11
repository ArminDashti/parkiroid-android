#Requires -Version 5.1
<#
.SYNOPSIS
    Build and export a Dogan APK to the exports folder.

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

function Get-JavaMajorVersion {
    param([string] $JavaExe)

    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $output = & $JavaExe -version 2>&1 | ForEach-Object { "$_" } | Out-String
    }
    finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }

    if ($output -match 'version "(\d+)') {
        return [int] $Matches[1]
    }
    if ($output -match 'version "1\.(\d+)') {
        return [int] $Matches[1]
    }
    return 0
}

function Resolve-GradleJavaHome {
    $candidates = [System.Collections.Generic.List[string]]::new()

    if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
        $candidates.Add($env:JAVA_HOME)
    }

    $searchRoots = @(
        (Join-Path $env:ProgramFiles 'Android\Android Studio\jbr'),
        (Join-Path ${env:ProgramFiles(x86)} 'Android\Android Studio\jbr'),
        (Join-Path $env:ProgramFiles 'Eclipse Adoptium'),
        (Join-Path $env:ProgramFiles 'Java'),
        (Join-Path $env:LOCALAPPDATA 'Programs\Eclipse Adoptium')
    )

    foreach ($root in $searchRoots) {
        if (-not (Test-Path $root)) {
            continue
        }

        if (Test-Path (Join-Path $root 'bin\java.exe')) {
            $candidates.Add($root)
            continue
        }

        Get-ChildItem -Path $root -Directory -ErrorAction SilentlyContinue |
            ForEach-Object { $candidates.Add($_.FullName) }
    }

    foreach ($candidateHome in ($candidates | Select-Object -Unique)) {
        $javaExe = Join-Path $candidateHome 'bin\java.exe'
        if (-not (Test-Path $javaExe)) {
            continue
        }
        if ((Get-JavaMajorVersion -JavaExe $javaExe) -ge 11) {
            return $candidateHome
        }
    }

    return $null
}

function Resolve-AndroidSdkDir {
    $candidates = [System.Collections.Generic.List[string]]::new()

    foreach ($envName in @('ANDROID_HOME', 'ANDROID_SDK_ROOT')) {
        $value = [Environment]::GetEnvironmentVariable($envName)
        if ($value) {
            $candidates.Add($value)
        }
    }

    $searchRoots = @(
        (Join-Path $env:LOCALAPPDATA 'Android\Sdk'),
        (Join-Path $env:USERPROFILE 'AppData\Local\Android\Sdk'),
        'C:\Android\Sdk'
    )

    foreach ($root in $searchRoots) {
        $candidates.Add($root)
    }

    $localPropertiesPath = Join-Path $projectRoot 'local.properties'
    if (Test-Path $localPropertiesPath) {
        $localProperties = Get-Content -Raw -Path $localPropertiesPath
        if ($localProperties -match '(?m)^sdk\.dir=(.+)$') {
            $sdkDir = $Matches[1].Trim() -replace '\\:', ':' -replace '\\\\', '\'
            $candidates.Add($sdkDir)
        }
    }

    foreach ($sdkDir in ($candidates | Select-Object -Unique)) {
        if ([string]::IsNullOrWhiteSpace($sdkDir)) {
            continue
        }
        if ((Test-Path $sdkDir) -and (Test-Path (Join-Path $sdkDir 'platforms'))) {
            return $sdkDir
        }
    }

    return $null
}

function Ensure-LocalPropertiesSdkDir {
    param([string] $SdkDir)

    $localPropertiesPath = Join-Path $projectRoot 'local.properties'
    $escapedSdkDir = ($SdkDir -replace '\\', '\\')
    $sdkLine = "sdk.dir=$escapedSdkDir"

    if (-not (Test-Path $localPropertiesPath)) {
        @(
            '## This file is generated locally and must not be checked into version control.',
            $sdkLine
        ) | Set-Content -Path $localPropertiesPath -Encoding UTF8
        return
    }

    $localProperties = Get-Content -Raw -Path $localPropertiesPath
    if ($localProperties -match '(?m)^sdk\.dir=') {
        $localProperties = [regex]::Replace($localProperties, '(?m)^sdk\.dir=.*$', $sdkLine)
    }
    else {
        $localProperties = $localProperties.TrimEnd() + [Environment]::NewLine + $sdkLine + [Environment]::NewLine
    }

    Set-Content -Path $localPropertiesPath -Value $localProperties -Encoding UTF8 -NoNewline
}

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

$gradleJavaHome = Resolve-GradleJavaHome
if (-not $gradleJavaHome) {
    throw @(
        'No Java 11+ runtime found. Android Gradle Plugin 8.5 requires JDK 11 or newer.',
        'Install a JDK or set JAVA_HOME to Android Studio''s bundled runtime, e.g.:',
        '  $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"'
    ) -join [Environment]::NewLine
}

if ($env:JAVA_HOME -ne $gradleJavaHome) {
    Write-Host "Using Java from: $gradleJavaHome"
    $env:JAVA_HOME = $gradleJavaHome
}

$androidSdkDir = Resolve-AndroidSdkDir
if (-not $androidSdkDir) {
    throw @(
        'Android SDK not found. Install it via Android Studio:',
        '  Settings > Languages & Frameworks > Android SDK',
        'or set ANDROID_HOME to your SDK path, e.g.:',
        "  `$env:ANDROID_HOME = `"$env:LOCALAPPDATA\Android\Sdk`""
    ) -join [Environment]::NewLine
}

Ensure-LocalPropertiesSdkDir -SdkDir $androidSdkDir
if ($env:ANDROID_HOME -ne $androidSdkDir) {
    Write-Host "Using Android SDK from: $androidSdkDir"
    $env:ANDROID_HOME = $androidSdkDir
    $env:ANDROID_SDK_ROOT = $androidSdkDir
}

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
$exportFileName = "dogan-$variantLabel-v$versionName-$timestamp.apk"
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
