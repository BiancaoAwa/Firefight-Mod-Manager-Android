# Static gate over the built APK (checks G2 to G9).

[CmdletBinding()]
param(
    [string]$Apk = "",
    [string]$JdkHome = "",
    [string]$AndroidSdk = "",
    [string]$Python = ""
)

. "$PSScriptRoot\common.ps1"

if (-not $Apk) { $Apk = Join-Path $OutDir "Firefight-Mod-Manager-13.2.0-mgr0.1.2.apk" }
$resolved = Resolve-Path $Apk -ErrorAction SilentlyContinue
if (-not $resolved) { Write-Output "APK does not exist; run build.ps1 first or pass -Apk"; exit 1 }
$Apk = $resolved.Path

$jdk = Resolve-JdkHome $JdkHome
if (-not $jdk) { Write-Output "JDK not found"; exit 1 }
$env:JAVA_HOME = $jdk
$sdk = Resolve-AndroidSdk $AndroidSdk
if (-not $sdk) { Write-Output "Android SDK not found"; exit 1 }
$bt = Resolve-BuildTools $sdk
if (-not $bt) { Write-Output "build-tools not found"; exit 1 }
$py = Resolve-Python $Python
if (-not $py) { Write-Output "python not found"; exit 1 }
New-Item -ItemType Directory -Force -Path $LogDir | Out-Null

Write-Output "=== G1 artifact ==="
Get-Item $Apk | Select-Object FullName, Length, LastWriteTime | Format-List | Out-String

Write-Output "=== G2 zip layer (dex / .so / bundled mod packages) ==="
& $py "$ToolsDir\verify_apk.py" $Apk
$g2 = $LASTEXITCODE

Write-Output "=== G3 zipalign check ==="
& "$bt\zipalign.exe" -c -v 4 $Apk 2>&1 | Select-Object -Last 5
$g3 = $LASTEXITCODE

Write-Output "=== G4 apksigner check ==="
& "$bt\apksigner.bat" verify --verbose --print-certs $Apk 2>&1 | Select-Object -First 25
$g4 = $LASTEXITCODE

Write-Output "=== G5 aapt2 badging ==="
& "$bt\aapt2.exe" dump badging $Apk 2>&1 |
    Select-String -Pattern "^package|^launchable-activity|^application-label:|uses-permission|^sdkVersion|^targetSdkVersion" |
    ForEach-Object { $_.Line }
$g5 = $LASTEXITCODE

Write-Output "=== G6 class table of the injected dex ==="
$dex = Join-Path $BuildDir "dex\classes.dex"
if (Test-Path $dex) {
    & "$bt\dexdump.exe" -f $dex 2>&1 | Select-String -Pattern "Class descriptor" | ForEach-Object { $_.Line.Trim() }
    $g6 = $LASTEXITCODE
} else {
Write-Output "  (core/build/dex/classes.dex does not exist yet, skipped; run build.ps1 first)"
    $g6 = 0
}

Write-Output "=== G7 size ==="
"  signed package: {0:N1} MB" -f ((Get-Item $Apk).Length / 1MB)

Write-Output "=== G8 localized resources ==="
& "$bt\aapt2.exe" dump resources $Apk 2>&1 | Out-File -Encoding utf8 (Join-Path $LogDir "apk-resources.txt")
$res = Get-Content (Join-Path $LogDir "apk-resources.txt")
$g8 = 0
foreach ($k in @("ffm_btn_lang", "ffm_menu_maps", "ffm_map_import", "ffm_title_lang", "ffm_map_synced", "ffm_about_mapdir",
                 "ffm_mm_title", "ffm_mm_prompt_title", "ffm_mm_tab_mods", "ffm_mm_tab_maps", "ffm_mm_tab_about",
                 "ffm_mm_btn_enable", "ffm_mm_btn_disable", "ffm_mm_btn_delete", "ffm_mm_btn_import_mod",
                 "ffm_mm_btn_import_map", "ffm_mm_about_afdian", "ffm_mm_about_mail", "ffm_mm_meta_copy", "ffm_mm_del_title",
                 "ffm_mm_lang", "ffm_mm_about_repo", "ffm_mm_about_bili")) {
if ($res | Select-String -Pattern ("string/" + $k + "\b") -Quiet) { "  $k : present" }
else { "  $k : MISSING!"; $g8 = 1 }
}
$ffm = ($res | Select-String -Pattern "string/ffm_[a-z0-9_]+" -AllMatches).Matches |
    ForEach-Object { $_.Value } | Sort-Object -Unique
"  ffm_ resource entries: $($ffm.Count)"
if ($res | Select-String -Pattern "自定义地图" -Quiet) { "  zh-rCN translation: present" }
else { "  zh-rCN translation: MISSING!"; $g8 = 1 }
if ($res | Select-String -Pattern "カスタムマップ" -Quiet) { "  ja translation: present" }
else { "  ja translation: MISSING!"; $g8 = 1 }

Write-Output "=== G9 the three activities registered in the manifest ==="
$mf = & "$bt\aapt2.exe" dump xmltree --file AndroidManifest.xml $Apk 2>&1
$g9 = 0
foreach ($a in @("com.windowsgames.firefight.MyGame",
                 "com.windowsgames.firefight.ModManagerActivity",
                 "com.windowsgames.firefight.ModImportActivity")) {
if ($mf | Select-String -Pattern ([regex]::Escape($a)) -Quiet) { "  $a : registered" }
else { "  $a : NOT registered!"; $g9 = 1 }
}

Write-Output ""
Write-Output "summary: G2=$g2 G3=$g3 G4=$g4 G5=$g5 G6=$g6 G8=$g8 G9=$g9  (0=pass)"
if (($g2 + $g3 + $g4 + $g5 + $g6 + $g8 + $g9) -eq 0) {
    Write-Output "RESULT: PASS"
    exit 0
}
Write-Output "RESULT: FAIL"
exit 1
