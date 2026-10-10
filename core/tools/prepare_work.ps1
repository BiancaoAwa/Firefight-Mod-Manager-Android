# Firefight Mod Manager -- prepare the decoded worktree
#
# Decode "a Firefight APK that already carries your mods" into core/work/v35 and apply the worktree
# changes the manager needs (two activities in AndroidManifest, the sample mod, retiring the old MyGame.smali).
#
# Usage (from the repository root):
#   pwsh -File core/tools/prepare_work.ps1 -GameApk "D:\path\Sean's 13.2.0_Mod_v35(1).apk"
#   pwsh -File core/tools/prepare_work.ps1 -GameApk <apk> -Force     # overwrite an existing worktree
#
# Then run core/tools/build.ps1 to produce the APK.
#
# Why a modded APK: the manager and the game are one package with one signature, so the manager rides
# on your own mod package and the player's saves and mod directory stay inside the same app sandbox. See docs/deploy.md.

[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$GameApk,
    [string]$JdkHome = "",
    [string]$ApktoolJar = "",
    [string]$Python = "",
    [switch]$Force,
    [switch]$SkipPatch
)

. "$PSScriptRoot\common.ps1"

foreach ($d in @($LogDir, $BakDir, (Join-Path $CoreDir "work"))) {
    New-Item -ItemType Directory -Force -Path $d | Out-Null
}

Write-Step "[0/4] checking the toolchain"
if (-not (Test-Path $GameApk)) { Fail "game-apk" "Firefight APK not found: $GameApk" }
$jdk = Resolve-JdkHome $JdkHome
if (-not $jdk) { Fail "jdk" "JDK not found. Install JDK 17+ or pass -JdkHome." }
$env:JAVA_HOME = $jdk
$apktool = Resolve-Apktool $ApktoolJar
if (-not $apktool) { Fail "apktool" "apktool jar not found. Put it in core/tools/apktool.jar or pass -ApktoolJar." }
$py = Resolve-Python $Python
if (-not $py) { Fail "python" "python not found. Install Python 3 or pass -Python <interpreter path>." }
Write-Output "  JDK     : $jdk"
Write-Output "  apktool : $apktool"
Write-Output "  python  : $py"
Write-Output "  input APK: $GameApk ($([math]::Round((Get-Item $GameApk).Length/1MB,1)) MB)"

if (-not (Ensure-Framework $apktool $FrameDir)) { Fail "framework" "Preparing the framework file failed (see the message above)." }

Write-Step "[1/4] decoding into $WorkTree"
if (Test-Path $WorkTree) {
    if (-not $Force) {
Fail "exists" "Worktree already exists: $WorkTree ; add -Force to regenerate it (it is deleted first)."
    }
    Remove-Item -Recurse -Force $WorkTree
}
$decodeLog = Join-Path $LogDir "decode.log"
& "$jdk\bin\java.exe" -Xmx4g -jar $apktool d -f -p "$FrameDir" -o $WorkTree $GameApk *> $decodeLog
if ($LASTEXITCODE -ne 0) { Get-Content $decodeLog -Tail 30; Fail "apktool-d" $LASTEXITCODE }
Write-Output "  decode finished, log: $decodeLog"

Write-Step "[2/4] applying the manager patches"
if ($SkipPatch) {
Write-Output "  -SkipPatch: skipped (use this when editing the worktree yourself)"
} else {
    & $py "$ToolsDir\patch_worktree.py" -w $WorkTree -c $CoreDir
    if ($LASTEXITCODE -ne 0) { Fail "patch" $LASTEXITCODE }
}

Write-Step "[3/4] self check"
$manifest = Join-Path $WorkTree "AndroidManifest.xml"
$okManifest = (Select-String -Path $manifest -Pattern "ModManagerActivity" -Quiet)
$okImport = (Select-String -Path $manifest -Pattern "ModImportActivity" -Quiet)
$leftover = Get-ChildItem $WorkTree -Recurse -Filter "MyGame.smali" -ErrorAction SilentlyContinue |
            Where-Object { $_.FullName -like "*\smali*" }
$okSmali = -not $leftover
Write-Output "  ModManagerActivity registered : $okManifest"
Write-Output "  ModImportActivity registered  : $okImport"
Write-Output "  old MyGame.smali removed     : $okSmali"
if (-not ($okManifest -and $okImport)) { Fail "selfcheck" "the AndroidManifest.xml patch did not take effect" }
if (-not $okSmali) { Fail "selfcheck" "an old MyGame.smali is still in the worktree: $($leftover.FullName -join ', ')" }

Write-Step "[4/4] next step"
Write-Output "  pwsh -File core/tools/build.ps1"
Write-Output "=== PREPARE OK ==="
