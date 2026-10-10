# Firefight 模组管理器 —— 上机冒烟（安装 + 启动 + 截图 + 证据收集）
#
# 用法（在仓库根目录，需要设备/模拟器已连上 adb）:
#   pwsh -File core/tools/smoke_test.ps1           安装 + 启动 + 截图 + 抓日志
#   pwsh -File core/tools/smoke_test.ps1 -ShotOnly 只截图（已装好时用）
#   pwsh -File core/tools/smoke_test.ps1 -Apk <别的.apk> -Package <别的包名>
#
# 产物：截图落在 <仓库根>/shots/。

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
    Write-Output "找不到 adb；装 Android SDK platform-tools，或用 -AndroidSdk 指定 SDK 目录"
    exit 1
}
if (-not $Apk) { $Apk = Join-Path $OutDir "Firefight-Mod-Manager-13.2.0-mgr0.1.0.apk" }
$pkg  = $Package
$act  = "com.windowsgames.firefight.MyGame"
$shot = Join-Path $RepoDir "shots"
New-Item -ItemType Directory -Force -Path $shot | Out-Null
if (-not $ShotOnly -and -not (Test-Path $Apk)) { Write-Output "APK 不存在: $Apk（先跑 core/tools/build.ps1）"; exit 1 }

function Shot($name) {
    & $adb shell screencap -p /sdcard/_s.png | Out-Null
    & $adb pull /sdcard/_s.png "$shot\$name.png" 2>&1 | Out-Null
    & $adb shell rm -f /sdcard/_s.png | Out-Null
    "  screenshot -> $shot\$name.png"
}

"=== 设备 ==="
& $adb devices

if (-not $ShotOnly) {
    "=== G8-1 adb install -r ==="
    & $adb install -r $apk 2>&1 | Select-Object -Last 5

    "=== G8-2 启动游戏 ==="
    & $adb shell am start -n "$pkg/$act" 2>&1
    Start-Sleep -Seconds 20
}

"=== G8-3 当前焦点 activity ==="
& $adb shell dumpsys window 2>$null | Select-String -Pattern "mCurrentFocus|mFocusedApp" | Select-Object -First 4 | ForEach-Object { $_.Line.Trim() }

"=== G8-4 崩溃日志（有 FATAL 就是失败）==="
$crash = & $adb logcat -d -t 400 2>$null | Select-String -Pattern "FATAL|AndroidRuntime.*firefight|E SDL" 
if ($crash) { $crash | Select-Object -First 30 | ForEach-Object { $_.Line } } else { "  （无 FATAL/AndroidRuntime 崩溃）" }

"=== G8-5 应用自己的日志（logcat 里带 firefight 的行）==="
& $adb logcat -d -t 400 2>$null | Select-String -Pattern "firefight|FFM|SDLActivity" | Select-Object -Last 20 | ForEach-Object { $_.Line }

"=== G8-6 模组目录 ==="
& $adb shell ls -la "/storage/emulated/0/Android/data/$pkg/files/" 2>&1
& $adb shell ls -la "/storage/emulated/0/Android/data/$pkg/files/Mod/" 2>&1

"=== G8-7 日志落盘（需求⑥）==="
& $adb shell ls -la "/storage/emulated/0/Download/Firefight-Mod-Manager/$pkg/" 2>&1

"=== G8-8 截图 ==="
Shot "01-launch"
