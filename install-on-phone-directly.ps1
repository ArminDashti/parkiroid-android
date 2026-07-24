#Requires -Version 5.1
<#
.SYNOPSIS
    Install Dogan on a USB-connected Android device via ADB.

.DESCRIPTION
    Locates adb, waits for a device with USB debugging enabled, builds a fresh
    APK from your current sources (unless skipped), then runs `adb install -r`.
    Optionally launches the app.

.PARAMETER Apk
    Path to an APK to install. Skips the Gradle build and installs this file.

.PARAMETER Variant
    Build variant used when resolving or building: Debug (default) or Release.

.PARAMETER SkipBuild
    Do not rebuild; install the newest APK already under exports/ or
    app/build/outputs/apk/. Use only when you know that APK is current.

.PARAMETER Clean
    Run a clean Gradle build before assembling the APK.

.PARAMETER Serial
    Target a specific device serial (adb -s). Use when multiple devices are connected.

.PARAMETER Launch
    Start the Dogan launcher activity after a successful install.

.PARAMETER UninstallFirst
    Uninstall com.dogan before installing (clears app data).

.EXAMPLE
    .\install-on-phone-directly.ps1

.EXAMPLE
    .\install-on-phone-directly.ps1 -Launch

.EXAMPLE
    .\install-on-phone-directly.ps1 -SkipBuild

.EXAMPLE
    .\install-on-phone-directly.ps1 -Apk .\exports\dogan-debug-v1.0.0-20260101-120000.apk
#>
[CmdletBinding()]
param(
    [string] $Apk = '',

    [ValidateSet('Debug', 'Release')]
    [string] $Variant = 'Debug',

    [switch] $SkipBuild,

    [switch] $Clean,

    [string] $Serial = '',

    [switch] $Launch,

    [switch] $UninstallFirst
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$packageId = 'com.dogan'
$launcherActivity = 'com.dogan/.MainActivity'
$projectRoot = Resolve-Path $PSScriptRoot

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
            $sdkDir = $Matches[1].Trim() -replace '\\:', ':' -replace '\\\\', [string][char]0x5C
            $candidates.Add($sdkDir)
        }
    }

    foreach ($sdkDir in ($candidates | Select-Object -Unique)) {
        if ([string]::IsNullOrWhiteSpace($sdkDir)) {
            continue
        }
        if ((Test-Path $sdkDir) -and (Test-Path (Join-Path $sdkDir 'platform-tools'))) {
            return $sdkDir
        }
    }

    return $null
}

function Resolve-AdbPath {
    $adbCmd = Get-Command adb -ErrorAction SilentlyContinue
    if ($adbCmd) {
        return $adbCmd.Source
    }

    $sdkDir = Resolve-AndroidSdkDir
    if ($sdkDir) {
        $sdkAdb = Join-Path $sdkDir 'platform-tools\adb.exe'
        if (Test-Path $sdkAdb) {
            return $sdkAdb
        }
    }

    return $null
}

function Invoke-Adb {
    param(
        [Parameter(Mandatory)]
        [string[]] $AdbArgs
    )

    $allArgs = @()
    if (-not [string]::IsNullOrWhiteSpace($Serial)) {
        $allArgs += @('-s', $Serial)
    }
    $allArgs += $AdbArgs

    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $output = & $adbPath @allArgs 2>&1 | ForEach-Object { "$_" }
        $exitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }

    return [pscustomobject]@{
        ExitCode = $exitCode
        Output   = ($output -join [Environment]::NewLine)
    }
}

function Get-AdbDevices {
    $result = Invoke-Adb -AdbArgs @('devices')
    if ($result.ExitCode -ne 0) {
        throw "adb devices failed:`n$($result.Output)"
    }

    $devices = @()
    foreach ($line in ($result.Output -split "`r?`n")) {
        if ($line -match '^(\S+)\s+(device|unauthorized|offline|recovery|sideload)\s*$') {
            $devices += [pscustomobject]@{
                Serial = $Matches[1]
                State  = $Matches[2]
            }
        }
    }
    return $devices
}

function Wait-ForAdbDevice {
    Write-Host 'Looking for a USB-debugging device...'

    $deadline = (Get-Date).AddSeconds(60)
    while ((Get-Date) -lt $deadline) {
        $devices = @(Get-AdbDevices)

        if (-not [string]::IsNullOrWhiteSpace($Serial)) {
            $match = $devices | Where-Object { $_.Serial -eq $Serial } | Select-Object -First 1
            if (-not $match) {
                Write-Host "  Waiting for device $Serial ..."
            }
            elseif ($match.State -eq 'unauthorized') {
                Write-Host '  Device unauthorized - accept the USB debugging prompt on the phone.'
            }
            elseif ($match.State -eq 'device') {
                Write-Host "  Using device: $($match.Serial)"
                return $match.Serial
            }
            else {
                Write-Host "  Device $($match.Serial) is $($match.State); waiting..."
            }
        }
        else {
            $ready = @($devices | Where-Object { $_.State -eq 'device' })
            $unauthorized = @($devices | Where-Object { $_.State -eq 'unauthorized' })

            if ($ready.Count -eq 1) {
                Write-Host "  Using device: $($ready[0].Serial)"
                return $ready[0].Serial
            }
            if ($ready.Count -gt 1) {
                $serials = ($ready | ForEach-Object { $_.Serial }) -join ', '
                throw @(
                    "Multiple devices connected: $serials",
                    'Re-run with -Serial <id>, e.g.:',
                    "  .\install-on-phone-directly.ps1 -Serial $($ready[0].Serial)"
                ) -join [Environment]::NewLine
            }
            if ($unauthorized.Count -gt 0) {
                Write-Host '  Device unauthorized - accept the USB debugging prompt on the phone.'
            }
            else {
                Write-Host '  No device yet. Enable Developer options > USB debugging, then plug in USB.'
            }
        }

        Start-Sleep -Seconds 2
    }

    throw @(
        'No ready ADB device found within 60 seconds.',
        'Checklist:',
        '  1. Enable Developer options and USB debugging on the phone',
        '  2. Connect via USB (not charge-only)',
        '  3. Accept the Allow USB debugging prompt',
        '  4. Run: adb devices  (should show device, not unauthorized)'
    ) -join [Environment]::NewLine
}

function Resolve-ApkPath {
    param(
        [string] $VariantName,

        [switch] $PreferGradleOutput
    )

    if (-not [string]::IsNullOrWhiteSpace($Apk)) {
        $resolved = [System.IO.Path]::GetFullPath($Apk)
        if (-not (Test-Path $resolved)) {
            throw "APK not found: $resolved"
        }
        return $resolved
    }

    $variantFolder = $VariantName.ToLowerInvariant()
    $gradleApkDir = Join-Path $projectRoot "app\build\outputs\apk\$variantFolder"
    $exportsDir = Join-Path $projectRoot 'exports'

    # After a fresh build, always take Gradle output so we never reinstall a stale exports/ copy.
    if ($PreferGradleOutput) {
        $searchRoots = @($gradleApkDir)
    }
    else {
        $searchRoots = @($exportsDir, $gradleApkDir)
    }

    $candidates = @()
    foreach ($root in $searchRoots) {
        if (-not (Test-Path $root)) {
            continue
        }
        $filter = if ($root -like '*\exports') { "dogan-$variantFolder-*.apk" } else { '*.apk' }
        $candidates += @(
            Get-ChildItem -Path $root -Filter $filter -File -ErrorAction SilentlyContinue
        )
    }

    if ($candidates.Count -eq 0) {
        throw @(
            "No $VariantName APK found under exports/ or app/build/outputs/apk/$variantFolder/.",
            'Rebuild and install:',
            '  .\install-on-phone-directly.ps1'
        ) -join [Environment]::NewLine
    }

    $newest = $candidates | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    return $newest.FullName
}

# --- main ---

$adbPath = Resolve-AdbPath
if (-not $adbPath) {
    throw @(
        'adb not found. Install Android SDK Platform-Tools and either:',
        '  - add platform-tools to PATH, or',
        '  - set ANDROID_HOME (e.g. $env:LOCALAPPDATA\Android\Sdk)'
    ) -join [Environment]::NewLine
}

Write-Host "Using adb: $adbPath"

$shouldBuild = -not $SkipBuild -and [string]::IsNullOrWhiteSpace($Apk)
$builtThisRun = $false

if ($shouldBuild) {
    $exportScript = Join-Path $projectRoot 'scripts\export-apk.ps1'
    if (-not (Test-Path $exportScript)) {
        throw "Export script not found: $exportScript"
    }

    $exportArgs = @{ Variant = $Variant }
    if ($Clean) {
        $exportArgs['Clean'] = $true
    }

    Write-Host "Building $Variant APK from current sources via export-apk.ps1..."
    & $exportScript @exportArgs
    if ($LASTEXITCODE -and $LASTEXITCODE -ne 0) {
        throw "export-apk.ps1 failed with exit code $LASTEXITCODE"
    }
    $builtThisRun = $true
}
elseif ($SkipBuild) {
    Write-Host 'Skipping build (-SkipBuild). Installing newest existing APK on disk.'
}

$apkPath = Resolve-ApkPath -VariantName $Variant -PreferGradleOutput:$builtThisRun
Write-Host "APK: $apkPath"

$deviceSerial = Wait-ForAdbDevice
if ([string]::IsNullOrWhiteSpace($Serial)) {
    $Serial = $deviceSerial
}

if ($UninstallFirst) {
    Write-Host "Uninstalling $packageId (if present)..."
    $uninstall = Invoke-Adb -AdbArgs @('uninstall', $packageId)
    Write-Host $uninstall.Output
}

Write-Host 'Stopping existing app (if running)...'
Invoke-Adb -AdbArgs @('shell', 'am', 'force-stop', $packageId) | Out-Null

Write-Host 'Installing (adb install -r)...'
$install = Invoke-Adb -AdbArgs @('install', '-r', $apkPath)
Write-Host $install.Output

if ($install.ExitCode -ne 0 -or ($install.Output -notmatch '(?m)^Success\b')) {
    throw "Install failed (exit $($install.ExitCode))."
}

Write-Host 'Install complete.'

if ($Launch) {
    Write-Host "Launching $launcherActivity..."
    $start = Invoke-Adb -AdbArgs @(
        'shell', 'am', 'start',
        '-a', 'android.intent.action.MAIN',
        '-c', 'android.intent.category.LAUNCHER',
        '-n', $launcherActivity
    )
    Write-Host $start.Output
    if ($start.ExitCode -ne 0) {
        Write-Warning "Install succeeded but launch failed (exit $($start.ExitCode))."
    }
}
else {
    Write-Host 'Tip: force-close Dogan on the phone (or re-run with -Launch) so it starts from the new APK.'
}

Write-Host ''
Write-Host 'Done. Device is ready with Dogan installed over USB debugging.'
