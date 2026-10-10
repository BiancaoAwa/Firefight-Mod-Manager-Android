# Firefight 模组管理器 —— 签名 APK 静态验证（G1–G9）
#
# 用法（在仓库根目录）:
#   pwsh -File core/tools/verify_apk.ps1
#   pwsh -File core/tools/verify_apk.ps1 -Apk D:\some\other.apk
#
# G1 产物信息 / G2 zip 层（dex、stored 的 .so）/ G3 zipalign / G4 签名
# G5 aapt2 badging / G6 注入 dex 的类表 / G7 体积 / G8 多语言资源 / G9 清单里的 Activity
# 退出码：全部通过为 0。

[CmdletBinding()]
param(
    [string]$Apk = "",
    [string]$JdkHome = "",
    [string]$AndroidSdk = "",
    [string]$Python = ""
)

. "$PSScriptRoot\common.ps1"

if (-not $Apk) { $Apk = Join-Path $OutDir "Firefight-Mod-Manager-13.2.0-mgr0.1.0.apk" }
$resolved = Resolve-Path $Apk -ErrorAction SilentlyContinue
if (-not $resolved) { Write-Output "APK 不存在；先跑 build.ps1 或用 -Apk 指定"; exit 1 }
$Apk = $resolved.Path

$jdk = Resolve-JdkHome $JdkHome
if (-not $jdk) { Write-Output "找不到 JDK"; exit 1 }
$env:JAVA_HOME = $jdk
$sdk = Resolve-AndroidSdk $AndroidSdk
if (-not $sdk) { Write-Output "找不到 Android SDK"; exit 1 }
$bt = Resolve-BuildTools $sdk
if (-not $bt) { Write-Output "找不到 build-tools"; exit 1 }
$py = Resolve-Python $Python
if (-not $py) { Write-Output "找不到 python"; exit 1 }
New-Item -ItemType Directory -Force -Path $LogDir | Out-Null

Write-Output "=== G1 产物 ==="
Get-Item $Apk | Select-Object FullName, Length, LastWriteTime | Format-List | Out-String

Write-Output "=== G2 zip 层（dex / .so / 内置模组包）==="
& $py "$ToolsDir\verify_apk.py" $Apk
$g2 = $LASTEXITCODE

Write-Output "=== G3 zipalign 校验 ==="
& "$bt\zipalign.exe" -c -v 4 $Apk 2>&1 | Select-Object -Last 5
$g3 = $LASTEXITCODE

Write-Output "=== G4 apksigner 校验 ==="
& "$bt\apksigner.bat" verify --verbose --print-certs $Apk 2>&1 | Select-Object -First 25
$g4 = $LASTEXITCODE

Write-Output "=== G5 aapt2 badging ==="
& "$bt\aapt2.exe" dump badging $Apk 2>&1 |
    Select-String -Pattern "^package|^launchable-activity|^application-label:|uses-permission|^sdkVersion|^targetSdkVersion" |
    ForEach-Object { $_.Line }
$g5 = $LASTEXITCODE

Write-Output "=== G6 注入 dex 的类表 ==="
$dex = Join-Path $BuildDir "dex\classes.dex"
if (Test-Path $dex) {
    & "$bt\dexdump.exe" -f $dex 2>&1 | Select-String -Pattern "Class descriptor" | ForEach-Object { $_.Line.Trim() }
    $g6 = $LASTEXITCODE
} else {
    Write-Output "  （还没有 core/build/dex/classes.dex，跳过；先跑 build.ps1）"
    $g6 = 0
}

Write-Output "=== G7 体积 ==="
"  签名包: {0:N1} MB" -f ((Get-Item $Apk).Length / 1MB)

Write-Output "=== G8 多语言资源 ==="
& "$bt\aapt2.exe" dump resources $Apk 2>&1 | Out-File -Encoding utf8 (Join-Path $LogDir "apk-resources.txt")
$res = Get-Content (Join-Path $LogDir "apk-resources.txt")
$g8 = 0
foreach ($k in @("ffm_btn_lang", "ffm_menu_maps", "ffm_map_import", "ffm_title_lang", "ffm_map_synced", "ffm_about_mapdir",
                 "ffm_mm_title", "ffm_mm_prompt_title", "ffm_mm_tab_mods", "ffm_mm_tab_maps", "ffm_mm_tab_about",
                 "ffm_mm_btn_enable", "ffm_mm_btn_disable", "ffm_mm_btn_delete", "ffm_mm_btn_import_mod",
                 "ffm_mm_btn_import_map", "ffm_mm_about_afdian", "ffm_mm_about_mail", "ffm_mm_meta_copy", "ffm_mm_del_title",
                 "ffm_mm_lang", "ffm_mm_about_repo", "ffm_mm_about_bili")) {
    if ($res | Select-String -Pattern ("string/" + $k + "\b") -Quiet) { "  $k : 已进包" }
    else { "  $k : 缺失！"; $g8 = 1 }
}
$ffm = ($res | Select-String -Pattern "string/ffm_[a-z0-9_]+" -AllMatches).Matches |
    ForEach-Object { $_.Value } | Sort-Object -Unique
"  ffm_ 资源条目: $($ffm.Count) 个"
if ($res | Select-String -Pattern "自定义地图" -Quiet) { "  zh-rCN 译文: 已进包（自定义地图）" }
else { "  zh-rCN 译文: 缺失！"; $g8 = 1 }
if ($res | Select-String -Pattern "カスタムマップ" -Quiet) { "  ja 译文: 已进包（カスタムマップ）" }
else { "  ja 译文: 缺失！"; $g8 = 1 }

Write-Output "=== G9 清单注册的三个 Activity ==="
$mf = & "$bt\aapt2.exe" dump xmltree --file AndroidManifest.xml $Apk 2>&1
$g9 = 0
foreach ($a in @("com.windowsgames.firefight.MyGame",
                 "com.windowsgames.firefight.ModManagerActivity",
                 "com.windowsgames.firefight.ModImportActivity")) {
    if ($mf | Select-String -Pattern ([regex]::Escape($a)) -Quiet) { "  $a : 已注册" }
    else { "  $a : 未注册！"; $g9 = 1 }
}

Write-Output ""
Write-Output "汇总: G2=$g2 G3=$g3 G4=$g4 G5=$g5 G6=$g6 G8=$g8 G9=$g9  (0=通过)"
if (($g2 + $g3 + $g4 + $g5 + $g6 + $g8 + $g9) -eq 0) {
    Write-Output "RESULT: PASS"
    exit 0
}
Write-Output "RESULT: FAIL"
exit 1
