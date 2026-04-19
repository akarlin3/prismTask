# PrismTask Sync Diagnostic Capture v2
# Tests steady-state sync (not initial app load) with a warmup period
# and analyzes events in time windows relative to when the test was performed.

param(
    [string]$DeviceA = "",
    [string]$DeviceB = "",
    [string]$AdbPath = "C:\Users\avery_yy1vm3l\AppData\Local\Microsoft\WinGet\Packages\Google.PlatformTools_Microsoft.Winget.Source_8wekyb3d8bbwe\platform-tools\adb.exe"
)

Write-Host "=== PrismTask Sync Diagnostic v2 (steady-state) ===" -ForegroundColor Cyan
Write-Host ""

# Verify adb
if (-not (Test-Path $AdbPath)) {
    Write-Host "ERROR: adb.exe not found at: $AdbPath" -ForegroundColor Red
    exit 1
}

try {
    $adbVersion = & $AdbPath --version 2>&1 | Select-Object -First 1
    Write-Host "ADB: $adbVersion" -ForegroundColor Green
} catch {
    Write-Host "ERROR: Failed to run adb. $_" -ForegroundColor Red
    exit 1
}

if (-not $DeviceA -or -not $DeviceB) {
    Write-Host ""
    Write-Host "Connected devices:"
    & $AdbPath devices -l
    Write-Host ""
    Write-Host "Run again with -DeviceA and -DeviceB parameters" -ForegroundColor Yellow
    exit 0
}

# Verify both devices reachable
Write-Host ""
Write-Host "Verifying device connectivity..." -ForegroundColor Cyan
$testA = & $AdbPath -s $DeviceA shell echo "ok" 2>&1
if ($testA -ne "ok") { Write-Host "ERROR: Cannot reach Device A" -ForegroundColor Red; exit 1 }
$testB = & $AdbPath -s $DeviceB shell echo "ok" 2>&1
if ($testB -ne "ok") { Write-Host "ERROR: Cannot reach Device B" -ForegroundColor Red; exit 1 }
Write-Host "  Both devices OK" -ForegroundColor Green

# STEP 1: Warmup
Write-Host ""
Write-Host "=== STEP 1: WARMUP ===" -ForegroundColor Yellow
Write-Host ""
Write-Host "Before we start capturing, we need both apps in steady state." -ForegroundColor White
Write-Host ""
Write-Host "1. Open PrismTask on BOTH devices" -ForegroundColor White
Write-Host "2. Wait for any initial sync to complete (watch the sync indicator)" -ForegroundColor White
Write-Host "3. Leave both apps foregrounded, showing the task list" -ForegroundColor White
Write-Host "4. Do NOT create, edit, or interact with any tasks during warmup" -ForegroundColor White
Write-Host ""
Read-Host "Press Enter once both apps are open, visible, and quiet"

# STEP 2: Clear buffers
Write-Host ""
Write-Host "Clearing log buffers on both devices..." -ForegroundColor Cyan
& $AdbPath -s $DeviceA logcat -c
& $AdbPath -s $DeviceB logcat -c
Write-Host "  Done" -ForegroundColor Green

# STEP 3: Start capture
Write-Host ""
Write-Host "Starting log capture..." -ForegroundColor Cyan
$logA = "device_a_v2.txt"
$logB = "device_b_v2.txt"
if (Test-Path $logA) { Remove-Item $logA }
if (Test-Path $logB) { Remove-Item $logB }

$procA = Start-Process -FilePath $AdbPath -ArgumentList "-s", $DeviceA, "logcat", "-v", "time" -RedirectStandardOutput $logA -PassThru -WindowStyle Hidden
$procB = Start-Process -FilePath $AdbPath -ArgumentList "-s", $DeviceB, "logcat", "-v", "time" -RedirectStandardOutput $logB -PassThru -WindowStyle Hidden
$captureStart = Get-Date
Write-Host "  Started at $(Get-Date -Format 'HH:mm:ss')" -ForegroundColor Green

# STEP 4: Baseline period
Write-Host ""
Write-Host "=== STEP 2: BASELINE (10 seconds of quiet) ===" -ForegroundColor Yellow
Write-Host "Waiting 10 seconds without interaction..." -ForegroundColor White
Start-Sleep -Seconds 10
$baselineEnd = Get-Date
Write-Host "  Baseline complete at $(Get-Date -Format 'HH:mm:ss')" -ForegroundColor Green

# STEP 5: The test
Write-Host ""
Write-Host "=== STEP 3: TEST (create task on Device A now) ===" -ForegroundColor Yellow
Write-Host ""
Write-Host "On DEVICE A only, create a task titled: DIAG_TEST_V2" -ForegroundColor White
Write-Host "Do NOT touch Device B at all." -ForegroundColor White
Write-Host ""
$testStart = Get-Date
Write-Host "Test started at: $(Get-Date -Format 'HH:mm:ss.fff')" -ForegroundColor Cyan
Read-Host "Press Enter IMMEDIATELY after you tap 'Save' on the new task"
$taskCreatedAt = Get-Date
Write-Host "Task creation timestamp: $(Get-Date -Format 'HH:mm:ss.fff')" -ForegroundColor Cyan

# STEP 6: Observation window
Write-Host ""
Write-Host "=== STEP 4: OBSERVATION (30 seconds) ===" -ForegroundColor Yellow
Write-Host "Waiting 30 seconds for sync to propagate to Device B..." -ForegroundColor White
Write-Host "(Do NOT interact with either device)" -ForegroundColor White
Start-Sleep -Seconds 30
$observationEnd = Get-Date

# STEP 7: Result
Write-Host ""
$appeared = Read-Host "Did the task 'DIAG_TEST_V2' appear on Device B? (y/n)"
$howAppeared = ""
if ($appeared -eq "y") {
    $howAppeared = Read-Host "How did it appear? (auto / after-tap / after-refresh / after-reopen)"
}

# STEP 8: Stop capture
Write-Host ""
Write-Host "Stopping log capture..." -ForegroundColor Cyan
Stop-Process -Id $procA.Id -Force -ErrorAction SilentlyContinue
Stop-Process -Id $procB.Id -Force -ErrorAction SilentlyContinue
Start-Sleep -Seconds 2

# STEP 9: Analysis
# Convert timestamps to the format used in logcat (-v time = MM-dd HH:mm:ss.fff)
$testStartLogFmt = $taskCreatedAt.ToString("MM-dd HH:mm:ss")
$testStartHour = $taskCreatedAt.ToString("HH")
$testStartMin = $taskCreatedAt.ToString("mm")

Write-Host ""
Write-Host "Analyzing logs..." -ForegroundColor Cyan

$sizeA = if (Test-Path $logA) { (Get-Item $logA).Length } else { 0 }
$sizeB = if (Test-Path $logB) { (Get-Item $logB).Length } else { 0 }

$totalPrismA = (Select-String -Path $logA -Pattern "PrismSync" -ErrorAction SilentlyContinue | Measure-Object).Count
$totalPrismB = (Select-String -Path $logB -Pattern "PrismSync" -ErrorAction SilentlyContinue | Measure-Object).Count

# Key signals
$writeEventsA = (Select-String -Path $logA -Pattern "PrismSync.*write|PrismSync.*push|PrismSync.*upload" -ErrorAction SilentlyContinue | Measure-Object).Count
$listenerEventsB = (Select-String -Path $logB -Pattern "PrismSync.*listener\.snapshot" -ErrorAction SilentlyContinue | Measure-Object).Count
$syncStartedB = (Select-String -Path $logB -Pattern "PrismSync.*sync\.started" -ErrorAction SilentlyContinue | Measure-Object).Count
$errorLinesA = (Select-String -Path $logA -Pattern "PrismSync.*status=(error|failed)" -ErrorAction SilentlyContinue | Measure-Object).Count
$errorLinesB = (Select-String -Path $logB -Pattern "PrismSync.*status=(error|failed)" -ErrorAction SilentlyContinue | Measure-Object).Count

# All Device A PrismSync lines after test start (shows what happened when we created the task)
$prismAAfterTest = @(Select-String -Path $logA -Pattern "PrismSync" -ErrorAction SilentlyContinue | Where-Object {
    $line = $_.Line
    if ($line -match "(\d{2})-(\d{2}) (\d{2}):(\d{2}):(\d{2})") {
        $lineTime = [DateTime]::ParseExact("$($matches[3]):$($matches[4]):$($matches[5])", "HH:mm:ss", $null)
        $testTime = [DateTime]::ParseExact($taskCreatedAt.ToString("HH:mm:ss"), "HH:mm:ss", $null)
        return $lineTime -ge $testTime.AddSeconds(-2)
    }
    return $false
})

$prismBAfterTest = @(Select-String -Path $logB -Pattern "PrismSync" -ErrorAction SilentlyContinue | Where-Object {
    $line = $_.Line
    if ($line -match "(\d{2})-(\d{2}) (\d{2}):(\d{2}):(\d{2})") {
        $lineTime = [DateTime]::ParseExact("$($matches[3]):$($matches[4]):$($matches[5])", "HH:mm:ss", $null)
        $testTime = [DateTime]::ParseExact($taskCreatedAt.ToString("HH:mm:ss"), "HH:mm:ss", $null)
        return $lineTime -ge $testTime.AddSeconds(-2)
    }
    return $false
})

$deviceAAfterSample = ($prismAAfterTest | Select-Object -First 40 | ForEach-Object { $_.Line } | Out-String)
$deviceBAfterSample = ($prismBAfterTest | Select-Object -First 40 | ForEach-Object { $_.Line } | Out-String)

$report = @"
=== PrismTask Sync Diagnostic Report v2 ===
Captured: $(Get-Date -Format "yyyy-MM-dd HH:mm:ss")
Device A: $DeviceA
Device B: $DeviceB

--- Timing ---
Capture started:      $($captureStart.ToString("HH:mm:ss.fff"))
Baseline ended:       $($baselineEnd.ToString("HH:mm:ss.fff"))
Task created (~):     $($taskCreatedAt.ToString("HH:mm:ss.fff"))
Observation ended:    $($observationEnd.ToString("HH:mm:ss.fff"))

--- Result ---
Task appeared on Device B: $appeared
If yes, how:               $howAppeared

--- Log sizes ---
Device A: $sizeA bytes
Device B: $sizeB bytes

--- Total PrismSync line counts (whole capture) ---
Device A: $totalPrismA
Device B: $totalPrismB

--- Key signals (whole capture) ---
Device A write/push events:     $writeEventsA
Device B listener snapshots:    $listenerEventsB
Device B sync.started events:   $syncStartedB
Device A error lines:           $errorLinesA
Device B error lines:           $errorLinesB

--- Device A PrismSync lines around/after task creation ---
(All lines from ~2 seconds before task creation onward)
Count: $($prismAAfterTest.Count)

$deviceAAfterSample

--- Device B PrismSync lines around/after task creation ---
(All lines from ~2 seconds before task creation onward)
Count: $($prismBAfterTest.Count)

$deviceBAfterSample
"@

$report | Out-File -FilePath "sync_diagnostic_report_v2.txt" -Encoding UTF8

Write-Host ""
Write-Host "=== ANALYSIS COMPLETE ===" -ForegroundColor Cyan
Write-Host ""
Write-Host "Quick summary:" -ForegroundColor White
Write-Host "  Device A events around test: $($prismAAfterTest.Count)" -ForegroundColor $(if ($prismAAfterTest.Count -gt 0) {"Green"} else {"Red"})
Write-Host "  Device B events around test: $($prismBAfterTest.Count)" -ForegroundColor $(if ($prismBAfterTest.Count -gt 0) {"Green"} else {"Red"})
Write-Host "  Task appeared on Device B:   $appeared" -ForegroundColor $(if ($appeared -eq "y") {"Green"} else {"Red"})
Write-Host ""
Write-Host "Full report: sync_diagnostic_report_v2.txt" -ForegroundColor Yellow
Write-Host "Share the contents back in chat." -ForegroundColor Yellow
