# Install the APK on a device or emulator and capture screenshots.

[CmdletBinding()]
param(
    [switch]$ShotOnly,
    [string]$Apk = "",
    [string]$AndroidSdk = "",
    [string]$Package = "com.windowsgames.firefight.mod"
)

. "$PSScriptRoot\common.ps1"

$sdk = Resolve-AndroidSdk $AndroidSdk
$adb = if ($sdk) { Join-Path $sdk "platform-tools\adb.exe" } else { "" }
if (-not $adb -or -not (Test-Path $adb)) {
    Write-Output "adb not found; install Android SDK platform-tools, or pass -AndroidSdk <sdk dir>"
    exit 1
}
if (-not $Apk) { $Apk = Join-Path $OutDir "Firefight-Mod-Manager-13.2.0-mgr0.1.3.apk" }
$pkg  = $Package
$act  = "com.windowsgames.firefight.MyGame"
$shot = Join-Path $RepoDir "shots"
New-Item -ItemType Directory -Force -Path $shot | Out-Null
if (-not $ShotOnly -and -not (Test-Path $Apk)) { Write-Output "APK does not exist: $Apk (run core/tools/build.ps1 first)"; exit 1 }

function Shot($name) {
    & $adb shell screencap -p /sdcard/_s.png | Out-Null
    & $adb pull /sdcard/_s.png "$shot\$name.png" 2>&1 | Out-Null
    & $adb shell rm -f /sdcard/_s.png | Out-Null
    "  screenshot -> $shot\$name.png"
}

"=== device ==="
& $adb devices

if (-not $ShotOnly) {
    "=== G8-1 adb install -r ==="
    & $adb install -r $apk 2>&1 | Select-Object -Last 5

    "=== G8-2 launching the game ==="
    & $adb shell am start -n "$pkg/$act" 2>&1
    Start-Sleep -Seconds 20
}

"=== G8-3 current focused activity ==="
& $adb shell dumpsys window 2>$null | Select-String -Pattern "mCurrentFocus|mFocusedApp" | Select-Object -First 4 | ForEach-Object { $_.Line.Trim() }

"=== G8-4 crash log (a FATAL means failure) ==="
$crash = & $adb logcat -d -t 400 2>$null | Select-String -Pattern "FATAL|AndroidRuntime.*firefight|E SDL"
if ($crash) { $crash | Select-Object -First 30 | ForEach-Object { $_.Line } } else { "  (no FATAL/AndroidRuntime crash)" }

"=== G8-5 the app's own log (logcat lines mentioning firefight) ==="
& $adb logcat -d -t 400 2>$null | Select-String -Pattern "firefight|FFM|SDLActivity" | Select-Object -Last 20 | ForEach-Object { $_.Line }

"=== G8-6 mod directory ==="
& $adb shell ls -la "/storage/emulated/0/Android/data/$pkg/files/" 2>&1
& $adb shell ls -la "/storage/emulated/0/Android/data/$pkg/files/Mod/" 2>&1

"=== G8-7 log persistence ==="
& $adb shell ls -la "/storage/emulated/0/Download/Firefight-Mod-Manager/$pkg/" 2>&1

"=== G8-8 screenshots ==="
Shot "01-launch"
