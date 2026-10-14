# Build the patched APK in 8 steps (strings, dex, apktool, sign).

[CmdletBinding()]
param(
    [string]$JdkHome = "",
    [string]$AndroidSdk = "",
    [string]$ApktoolJar = "",
    [string]$Python = "",
    [string]$Keystore = "",
    [string]$KsPass = "",
    [string]$KeyAlias = "",
    [string]$KeyPass = "",
    [string]$OutDir = "",
    [string]$OutName = "Firefight-Mod-Manager-13.2.0-mgr0.1.3.apk",
    [switch]$NoSign
)

$requestedOutDir = $OutDir

. "$PSScriptRoot\common.ps1"

if ($requestedOutDir) { $OutDir = [System.IO.Path]::GetFullPath($requestedOutDir) }

foreach ($d in @($BuildDir, $OutDir, $LogDir, $BakDir)) {
    New-Item -ItemType Directory -Force -Path $d | Out-Null
}

Write-Step "[0/8] checking the toolchain"
$jdk = Resolve-JdkHome $JdkHome
if (-not $jdk) { Fail "jdk" "JDK not found (javac/keytool required). Install JDK 17+ or pass -JdkHome." }
$env:JAVA_HOME = $jdk
Write-Output "  JDK        : $jdk"

$sdk = Resolve-AndroidSdk $AndroidSdk
if (-not $sdk) { Fail "sdk" "Android SDK not found. Pass -AndroidSdk or set ANDROID_HOME." }
$aj = Resolve-PlatformJar $sdk
if (-not $aj) { Fail "sdk-platform" "The SDK has no platforms/android-* containing android.jar (install one with sdkmanager)." }
$bt = Resolve-BuildTools $sdk
if (-not $bt) { Fail "sdk-buildtools" "The SDK has no build-tools containing d8.bat (install one with sdkmanager)." }
Write-Output "  android.jar: $aj"
Write-Output "  build-tools: $bt"

$apktool = Resolve-Apktool $ApktoolJar
if (-not $apktool) { Fail "apktool" "apktool jar not found. Download apktool 3.x to core/tools/apktool.jar, or pass -ApktoolJar." }
$py = Resolve-Python $Python
if (-not $py) { Fail "python" "python not found. Install Python 3, or pass -Python <interpreter path>." }
Write-Output "  apktool    : $apktool"
Write-Output "  python     : $py"

if (-not (Test-Path $WorkTree)) {
Fail "worktree" "Worktree does not exist: $WorkTree. Run first: pwsh -File core/tools/prepare_work.ps1 -GameApk <Firefight APK>"
}
if (-not (Ensure-Framework $apktool $FrameDir)) { Fail "framework" "Preparing the framework file failed (see the message above)." }

Write-Step "[1/8] generating res/values*/ffm_strings.xml"
& $py "$ToolsDir\gen_l10n.py" 2>&1 | Select-Object -Last 4
if ($LASTEXITCODE -ne 0) { Fail "gen-l10n" $LASTEXITCODE }

Write-Step "[2/8] backing up and removing the old MyGame.smali"
$old = Get-ChildItem $WorkTree -Recurse -Filter "MyGame.smali" -ErrorAction SilentlyContinue |
       Where-Object { $_.DirectoryName -like "*smali*" } | Select-Object -First 1
$bak = Join-Path $BakDir "MyGame.smali.orig"
if ($old) {
    Copy-Item $old.FullName $bak -Force
    Remove-Item $old.FullName -Force
    Write-Output "  backup -> $bak ($((Get-Item $bak).Length) bytes)"
    Write-Output "  removed $($old.FullName)"
} else {
Write-Output "  old MyGame.smali is already gone (backup present: $(Test-Path $bak))"
}

Write-Step "[3/8] javac stub classes (SDLActivity)"
Remove-Item -Recurse -Force (Join-Path $BuildDir "stub") -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path (Join-Path $BuildDir "stub") | Out-Null
& "$jdk\bin\javac.exe" --release 8 -Xlint:-options -nowarn -cp "$aj" -d (Join-Path $BuildDir "stub") `
    (Join-Path $CoreDir "stub\org\libsdl\app\SDLActivity.java")
if ($LASTEXITCODE -ne 0) { Fail "javac-stub" $LASTEXITCODE }

Write-Step "[4/8] javac the manager sources"
Remove-Item -Recurse -Force (Join-Path $BuildDir "classes") -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path (Join-Path $BuildDir "classes") | Out-Null
$srcs = Get-ChildItem (Join-Path $CoreDir "src\com\windowsgames\firefight\*.java") | ForEach-Object { $_.FullName }
& "$jdk\bin\javac.exe" --release 8 -Xlint:-options -nowarn `
    -cp "$aj;$(Join-Path $BuildDir 'stub')" -d (Join-Path $BuildDir "classes") @srcs
if ($LASTEXITCODE -ne 0) { Fail "javac-src" $LASTEXITCODE }
$nClass = (Get-ChildItem (Join-Path $BuildDir "classes") -Recurse -Filter *.class).Count
Write-Output "  class files: $nClass"

& "$jdk\bin\jar.exe" cf (Join-Path $BuildDir "mgr.jar") -C (Join-Path $BuildDir "classes") .
if ($LASTEXITCODE -ne 0) { Fail "jar" $LASTEXITCODE }

Write-Step "[5/8] d8 -> classes.dex"
Remove-Item -Recurse -Force (Join-Path $BuildDir "dex") -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path (Join-Path $BuildDir "dex") | Out-Null
& "$bt\d8.bat" --min-api 24 --release --lib "$aj" --output (Join-Path $BuildDir "dex") (Join-Path $BuildDir "mgr.jar")
if ($LASTEXITCODE -ne 0) { Fail "d8" $LASTEXITCODE }
$dex = Join-Path $BuildDir "dex\classes.dex"
Write-Output "  classes.dex: $((Get-Item $dex).Length) bytes"

Write-Step "[6/8] apktool rebuild of the worktree (230MB+, 3-8 minutes)"
$unsigned = Join-Path $BuildDir "unsigned.apk"
Remove-Item $unsigned -Force -ErrorAction SilentlyContinue
$apkLog = Join-Path $LogDir "build-apktool.log"
& "$jdk\bin\java.exe" -Xmx4g -jar $apktool b -p "$FrameDir" -o $unsigned $WorkTree *> $apkLog
if ($LASTEXITCODE -ne 0) { Get-Content $apkLog -Tail 30; Fail "apktool" $LASTEXITCODE }
Write-Output "  unsigned.apk: $([math]::Round((Get-Item $unsigned).Length/1MB,1)) MB"

Write-Step "[7/8] injecting classes4.dex (append-only, keeps the .so stored)"
& $py "$ToolsDir\inject_dex.py" $unsigned $dex
if ($LASTEXITCODE -ne 0) { Fail "inject-dex" $LASTEXITCODE }

Write-Step "[8/8] zipalign + apksigner"
$aligned = Join-Path $BuildDir "aligned.apk"
Remove-Item $aligned -Force -ErrorAction SilentlyContinue
& "$bt\zipalign.exe" -f -p 4 $unsigned $aligned
if ($LASTEXITCODE -ne 0) { Fail "zipalign" $LASTEXITCODE }

$out = Join-Path $OutDir $OutName
if ($NoSign) {
    Copy-Item $aligned $out -Force
Write-Output "  -NoSign: writing the unsigned package $out"
} else {
    if (-not $Keystore) {
        if (Test-Path $TestKeyFile) { $Keystore = $TestKeyFile }
        else { $Keystore = Join-Path $CoreDir "keystore\debug.keystore" }
    }
    $isTestKey = $Keystore -eq $TestKeyFile
    if (-not $KsPass) { if ($isTestKey) { $KsPass = $TestKeyPass } else { $KsPass = "android" } }
    if (-not $KeyAlias) { if ($isTestKey) { $KeyAlias = $TestKeyAlias } else { $KeyAlias = "androiddebugkey" } }
    if (-not $KeyPass) { $KeyPass = $KsPass }

    $ks = @(Ensure-Keystore $jdk $Keystore)[-1]
if (-not $ks) { Fail "keystore" "Signing certificate unavailable; pass -Keystore <file.keystore>, or use -NoSign for an unsigned package." }
    Write-Output "  signing key: $ks (alias $KeyAlias)"
    Remove-Item $out -Force -ErrorAction SilentlyContinue
    & "$bt\apksigner.bat" sign --ks $ks --ks-pass pass:$KsPass --key-pass pass:$KeyPass `
        --ks-key-alias $KeyAlias --v1-signing-enabled true --v2-signing-enabled true `
        --out $out $aligned
    if ($LASTEXITCODE -ne 0) { Fail "apksigner" $LASTEXITCODE }
    & "$bt\apksigner.bat" verify --print-certs $out | Select-String "SHA-256 digest"
}
Write-Output "  signed: $out  ($([math]::Round((Get-Item $out).Length/1MB,1)) MB)"

Write-Output "=== BUILD OK ==="
