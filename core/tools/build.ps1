# Firefight 模组管理器 —— 一键构建
#
# 用法（在仓库根目录）:
#   pwsh -File core/tools/build.ps1
#   pwsh -File core/tools/build.ps1 -JdkHome "D:\jdk-21" -AndroidSdk "D:\Android\Sdk"
#
# 前置条件:
#   1. 已经反编译出工作树: pwsh -File core/tools/prepare_work.ps1 -GameApk <交战 APK>
#   2. JDK 17+、Android SDK（platforms + build-tools）、Python 3、apktool 3.x jar
#      没给参数时按 common.ps1 的规则自动找；找不到会明确告诉你要装什么。
#
# 产物: out/Firefight-Mod-Manager-13.2.0-mgr0.1.0.apk
#
# 注意：不能用 $ErrorActionPreference = "Stop" —— javac/d8/apksigner 会把警告写到
# stderr，Stop 模式下 PowerShell 会把原生命令的 stderr 当成终止错误。每步显式查
# $LASTEXITCODE。

[CmdletBinding()]
param(
    [string]$JdkHome = "",
    [string]$AndroidSdk = "",
    [string]$ApktoolJar = "",
    [string]$Python = "",
    [string]$Keystore = "",
    [string]$OutName = "Firefight-Mod-Manager-13.2.0-mgr0.1.0.apk",
    [switch]$NoSign
)

. "$PSScriptRoot\common.ps1"

foreach ($d in @($BuildDir, $OutDir, $LogDir, $BakDir)) {
    New-Item -ItemType Directory -Force -Path $d | Out-Null
}

# ------------------------------------------------------------------ 环境检查
Write-Step "[0/8] 检查工具链"
$jdk = Resolve-JdkHome $JdkHome
if (-not $jdk) { Fail "jdk" "找不到 JDK（需要 javac/keytool）。装 JDK 17+ 或用 -JdkHome 指定。" }
$env:JAVA_HOME = $jdk
Write-Output "  JDK        : $jdk"

$sdk = Resolve-AndroidSdk $AndroidSdk
if (-not $sdk) { Fail "sdk" "找不到 Android SDK。用 -AndroidSdk 指定，或设置 ANDROID_HOME。" }
$aj = Resolve-PlatformJar $sdk
if (-not $aj) { Fail "sdk-platform" "SDK 里没有带 android.jar 的 platforms/android-*（用 sdkmanager 装一个）。" }
$bt = Resolve-BuildTools $sdk
if (-not $bt) { Fail "sdk-buildtools" "SDK 里没有带 d8.bat 的 build-tools（用 sdkmanager 装一个）。" }
Write-Output "  android.jar: $aj"
Write-Output "  build-tools: $bt"

$apktool = Resolve-Apktool $ApktoolJar
if (-not $apktool) { Fail "apktool" "找不到 apktool jar。下载 apktool 3.x 放在 core/tools/apktool.jar，或用 -ApktoolJar 指定。" }
$py = Resolve-Python $Python
if (-not $py) { Fail "python" "找不到 python。安装 Python 3，或用 -Python 指定解释器路径。" }
Write-Output "  apktool    : $apktool"
Write-Output "  python     : $py"

if (-not (Test-Path $WorkTree)) {
    Fail "worktree" "工作树不存在: $WorkTree。先跑: pwsh -File core/tools/prepare_work.ps1 -GameApk <交战 APK>"
}
if (-not (Ensure-Framework $apktool $FrameDir)) { Fail "framework" "框架文件准备失败（见上面提示）。" }

# ------------------------------------------------------------------ 1. 本地化资源
Write-Step "[1/8] 生成 res/values*/ffm_strings.xml"
& $py "$ToolsDir\gen_l10n.py" 2>&1 | Select-Object -Last 4
if ($LASTEXITCODE -ne 0) { Fail "gen-l10n" $LASTEXITCODE }

# ------------------------------------------------------------------ 2. 旧类让位
Write-Step "[2/8] 备份并移除旧 MyGame.smali"
$old = Get-ChildItem $WorkTree -Recurse -Filter "MyGame.smali" -ErrorAction SilentlyContinue |
       Where-Object { $_.DirectoryName -like "*smali*" } | Select-Object -First 1
$bak = Join-Path $BakDir "MyGame.smali.orig"
if ($old) {
    Copy-Item $old.FullName $bak -Force
    Remove-Item $old.FullName -Force
    Write-Output "  backup -> $bak ($((Get-Item $bak).Length) bytes)"
    Write-Output "  removed $($old.FullName)"
} else {
    Write-Output "  旧 MyGame.smali 已不在（备份存在: $(Test-Path $bak)）"
}

# ------------------------------------------------------------------ 3. javac 桩类
Write-Step "[3/8] javac 桩类（SDLActivity）"
Remove-Item -Recurse -Force (Join-Path $BuildDir "stub") -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path (Join-Path $BuildDir "stub") | Out-Null
& "$jdk\bin\javac.exe" --release 8 -Xlint:-options -nowarn -cp "$aj" -d (Join-Path $BuildDir "stub") `
    (Join-Path $CoreDir "stub\org\libsdl\app\SDLActivity.java")
if ($LASTEXITCODE -ne 0) { Fail "javac-stub" $LASTEXITCODE }

# ------------------------------------------------------------------ 4. javac 源码
Write-Step "[4/8] javac 管理器源码"
Remove-Item -Recurse -Force (Join-Path $BuildDir "classes") -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path (Join-Path $BuildDir "classes") | Out-Null
$srcs = Get-ChildItem (Join-Path $CoreDir "src\com\windowsgames\firefight\*.java") | ForEach-Object { $_.FullName }
& "$jdk\bin\javac.exe" --release 8 -Xlint:-options -nowarn `
    -cp "$aj;$(Join-Path $BuildDir 'stub')" -d (Join-Path $BuildDir "classes") @srcs
if ($LASTEXITCODE -ne 0) { Fail "javac-src" $LASTEXITCODE }
$nClass = (Get-ChildItem (Join-Path $BuildDir "classes") -Recurse -Filter *.class).Count
Write-Output "  class 文件: $nClass"

& "$jdk\bin\jar.exe" cf (Join-Path $BuildDir "mgr.jar") -C (Join-Path $BuildDir "classes") .
if ($LASTEXITCODE -ne 0) { Fail "jar" $LASTEXITCODE }

# ------------------------------------------------------------------ 5. d8
Write-Step "[5/8] d8 -> classes.dex"
Remove-Item -Recurse -Force (Join-Path $BuildDir "dex") -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path (Join-Path $BuildDir "dex") | Out-Null
& "$bt\d8.bat" --min-api 24 --release --lib "$aj" --output (Join-Path $BuildDir "dex") (Join-Path $BuildDir "mgr.jar")
if ($LASTEXITCODE -ne 0) { Fail "d8" $LASTEXITCODE }
$dex = Join-Path $BuildDir "dex\classes.dex"
Write-Output "  classes.dex: $((Get-Item $dex).Length) bytes"

# ------------------------------------------------------------------ 6. apktool
Write-Step "[6/8] apktool 回编译工作树（230MB+，3-8 分钟）"
$unsigned = Join-Path $BuildDir "unsigned.apk"
Remove-Item $unsigned -Force -ErrorAction SilentlyContinue
$apkLog = Join-Path $LogDir "build-apktool.log"
& "$jdk\bin\java.exe" -Xmx4g -jar $apktool b -p "$FrameDir" -o $unsigned $WorkTree *> $apkLog
if ($LASTEXITCODE -ne 0) { Get-Content $apkLog -Tail 30; Fail "apktool" $LASTEXITCODE }
Write-Output "  unsigned.apk: $([math]::Round((Get-Item $unsigned).Length/1MB,1)) MB"

# ------------------------------------------------------------------ 7. 注入 dex
Write-Step "[7/8] 注入 classes4.dex（append-only，保住 .so 的 stored）"
& $py "$ToolsDir\inject_dex.py" $unsigned $dex
if ($LASTEXITCODE -ne 0) { Fail "inject-dex" $LASTEXITCODE }

# ------------------------------------------------------------------ 8. 对齐 + 签名
Write-Step "[8/8] zipalign + apksigner"
$aligned = Join-Path $BuildDir "aligned.apk"
Remove-Item $aligned -Force -ErrorAction SilentlyContinue
& "$bt\zipalign.exe" -f -p 4 $unsigned $aligned
if ($LASTEXITCODE -ne 0) { Fail "zipalign" $LASTEXITCODE }

$out = Join-Path $OutDir $OutName
if ($NoSign) {
    Copy-Item $aligned $out -Force
    Write-Output "  -NoSign: 输出未签名包 $out"
} else {
    if (-not $Keystore) { $Keystore = Join-Path $CoreDir "keystore\debug.keystore" }
    # 只取最后一项：万一将来函数里混进了别的输出，也不要让 $ks 变成数组把命令行串歪。
    $ks = @(Ensure-Keystore $jdk $Keystore)[-1]
    if (-not $ks) { Fail "keystore" "签名证书不可用；用 -Keystore 指定 .keystore，或用 -NoSign 出未签名包。" }
    Remove-Item $out -Force -ErrorAction SilentlyContinue
    & "$bt\apksigner.bat" sign --ks $ks --ks-pass pass:android --key-pass pass:android `
        --ks-key-alias androiddebugkey --v1-signing-enabled true --v2-signing-enabled true `
        --out $out $aligned
    if ($LASTEXITCODE -ne 0) { Fail "apksigner" $LASTEXITCODE }
    & "$bt\apksigner.bat" verify --print-certs $out | Select-String "SHA-256 digest"
}
Write-Output "  signed: $out  ($([math]::Round((Get-Item $out).Length/1MB,1)) MB)"

Write-Output "=== BUILD OK ==="
