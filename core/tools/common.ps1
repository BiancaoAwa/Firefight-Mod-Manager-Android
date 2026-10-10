# Firefight 模组管理器 —— 构建脚本共用的环境探测
# 由 build.ps1 / prepare_work.ps1 以 . (dot-source) 方式加载；不单独运行。
#
# 路径全部相对本仓库，不写死用户名：
#   <repo>/                     仓库根
#   <repo>/core/                源码与工具（core/src, core/stub, core/tools, core/assets）
#   <repo>/core/work/v35        反编译出来的交战工程（不入库，由 prepare_work.ps1 生成）
#   <repo>/core/framework/1.apk apktool 框架文件（不入库，自动从 apktool jar 取出）
#   <repo>/core/build/          中间产物（不入库）
#   <repo>/out/                 成品 APK（不入库）

$ErrorActionPreference = "Continue"   # 不能用 Stop：原生命令的 stderr 会被当成终止错误

$ToolsDir = $PSScriptRoot
$CoreDir  = Split-Path -Parent $ToolsDir
$RepoDir  = Split-Path -Parent $CoreDir
$WorkTree = Join-Path $CoreDir "work\v35"
$FrameDir = Join-Path $CoreDir "framework"
$BuildDir = Join-Path $CoreDir "build"
$OutDir   = Join-Path $RepoDir "out"
$LogDir   = Join-Path $CoreDir "logs"
$BakDir   = Join-Path $CoreDir "backup"

function Write-Step($s) { Write-Output "=== $s ===" }

function Fail($step, $code) {
    Write-Output "FAILED[$step] exit=$code"
    exit 1
}

function Get-VerKey([string]$n) {
    $m = [regex]::Match([string]$n, '\d+(\.\d+)*')
    if ($m.Success) { try { return [version]$m.Value } catch { return [version]'0.0' } }
    return [version]'0.0'
}

# ------------------------------------------------------------------ 工具探测

function Resolve-JdkHome([string]$explicit) {
    $cand = @()
    if ($explicit) { $cand += $explicit }
    if ($env:JAVA_HOME) { $cand += $env:JAVA_HOME }
    foreach ($root in @("C:\Program Files\Eclipse Adoptium", "C:\Program Files\Java",
                        "C:\Program Files\Microsoft", "$env:LOCALAPPDATA\Programs\Eclipse Adoptium")) {
        $cand += (Get-ChildItem $root -Directory -ErrorAction SilentlyContinue |
                  Sort-Object { Get-VerKey $_.Name } -Descending |
                  ForEach-Object { $_.FullName })
    }
    foreach ($p in $cand) {
        if ($p -and (Test-Path (Join-Path $p "bin\javac.exe"))) { return $p }
    }
    $jc = Get-Command javac -ErrorAction SilentlyContinue
    if ($jc) { return (Split-Path -Parent (Split-Path -Parent $jc.Source)) }
    return ""
}

function Resolve-AndroidSdk([string]$explicit) {
    $cand = @()
    if ($explicit) { $cand += $explicit }
    if ($env:ANDROID_HOME) { $cand += $env:ANDROID_HOME }
    if ($env:ANDROID_SDK_ROOT) { $cand += $env:ANDROID_SDK_ROOT }
    $cand += (Join-Path $env:LOCALAPPDATA "Android\Sdk")
    foreach ($p in $cand) {
        if ($p -and (Test-Path (Join-Path $p "platforms"))) { return $p }
    }
    return ""
}

function Resolve-PlatformJar([string]$sdk) {
    $plats = Get-ChildItem (Join-Path $sdk "platforms") -Directory -ErrorAction SilentlyContinue |
             Where-Object { Test-Path (Join-Path $_.FullName "android.jar") } |
             Sort-Object { Get-VerKey $_.Name } -Descending
    if ($plats) { return (Join-Path $plats[0].FullName "android.jar") }
    return ""
}

function Resolve-BuildTools([string]$sdk) {
    $bt = Get-ChildItem (Join-Path $sdk "build-tools") -Directory -ErrorAction SilentlyContinue |
          Where-Object { Test-Path (Join-Path $_.FullName "d8.bat") } |
          Sort-Object { Get-VerKey $_.Name } -Descending
    if ($bt) { return $bt[0].FullName }
    return ""
}

function Resolve-Apktool([string]$explicit) {
    $cand = @()
    if ($explicit) { $cand += $explicit }
    if ($env:APKTOOL_JAR) { $cand += $env:APKTOOL_JAR }
    $cand += (Join-Path $ToolsDir "apktool.jar")
    $cand += (Get-ChildItem $ToolsDir -Filter "apktool*.jar" -ErrorAction SilentlyContinue |
              Sort-Object Name -Descending | ForEach-Object { $_.FullName })
    $dl = Join-Path $env:USERPROFILE "Downloads"
    $cand += (Get-ChildItem $dl -Filter "apktool*.jar" -ErrorAction SilentlyContinue |
              Sort-Object Name -Descending | ForEach-Object { $_.FullName })
    foreach ($p in $cand) { if ($p -and (Test-Path $p)) { return $p } }
    return ""
}

function Resolve-Python([string]$explicit) {
    if ($explicit -and (Test-Path $explicit)) { return $explicit }
    foreach ($n in @("python", "python3")) {
        $c = Get-Command $n -ErrorAction SilentlyContinue
        if ($c) { return $c.Source }
    }
    return ""
}

# ------------------------------------------------------------------ 依赖准备

# apktool 3.x 自带 prebuilt/android-framework.jar，取出来就是框架目录里的 1.apk。
# 这样仓库里不用塞 4 MB 的框架文件，别人克隆下来第一次跑就能自己生成。
function Ensure-Framework([string]$apktoolJar, [string]$dir) {
    $dst = Join-Path $dir "1.apk"
    if (Test-Path $dst) { return $dst }
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = [System.IO.Compression.ZipFile]::OpenRead($apktoolJar)
    try {
        $e = $zip.GetEntry("prebuilt/android-framework.jar")
        if (-not $e) {
            # 注意：这些提示必须走 Write-Host，不能走 Write-Output——本函数要「返回路径」，
            # 混进来的字符串会让调用方拿到数组，后面拼命令行时就串参数了。
            Write-Host "  apktool jar 中没有 prebuilt/android-framework.jar（apktool 版本过旧？）"
            Write-Host "  请手动执行: java -jar $apktoolJar if -p `"$dir`" <framework-res.apk>"
            return ""
        }
        [System.IO.Compression.ZipFileExtensions]::ExtractToFile($e, $dst, $true)
    } finally {
        $zip.Dispose()
    }
    Write-Host "  已从 apktool 内提取框架文件: $dst ($((Get-Item $dst).Length) bytes)"
    return $dst
}

# 没有调试签名证书就现生成一个（keytool 随 JDK 提供），避免依赖别的机器。
function Ensure-Keystore([string]$jdk, [string]$path) {
    if (Test-Path $path) { return $path }
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $path) | Out-Null
    & "$jdk\bin\keytool.exe" -genkeypair -keystore $path -alias androiddebugkey `
        -storepass android -keypass android -dname "CN=Android Debug,O=Android,C=US" `
        -keyalg RSA -keysize 2048 -validity 10000 2>&1 | Out-Null
    if (Test-Path $path) {
        Write-Host "  已生成调试签名证书: $path"
        return $path
    }
    return ""
}
