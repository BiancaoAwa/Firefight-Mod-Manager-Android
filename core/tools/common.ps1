# Firefight Mod Manager -- shared environment detection for the build scripts
# Dot-sourced by build.ps1 and prepare_work.ps1; not meant to run on its own.
#
# All paths are relative to this repository, with no hard-coded user name:
#   <repo>/                      repository root
#   <repo>/core/                 sources and tools (core/src, core/stub, core/tools, core/assets)
#   <repo>/core/work/v35         the decoded Firefight project (not committed, created by prepare_work.ps1)
#   <repo>/core/framework/1.apk  apktool framework file (not committed, extracted from the apktool jar)
#   <repo>/core/build/           intermediate artifacts (not committed)
#   <repo>/out/                  final APKs (not committed)

$ErrorActionPreference = "Continue"   # not Stop: a native command's stderr would be treated as a terminating error

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

# ------------------------------------------------------------------ tool detection

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

# ------------------------------------------------------------------ dependency preparation

# apktool 3.x bundles prebuilt/android-framework.jar; extracting it yields the 1.apk in the framework directory.
# That way the repository does not carry a 4 MB framework file and a fresh clone generates it on the first run.
function Ensure-Framework([string]$apktoolJar, [string]$dir) {
    $dst = Join-Path $dir "1.apk"
    if (Test-Path $dst) { return $dst }
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = [System.IO.Compression.ZipFile]::OpenRead($apktoolJar)
    try {
        $e = $zip.GetEntry("prebuilt/android-framework.jar")
        if (-not $e) {
# Note: these messages must go through Write-Host, never Write-Output -- this function returns a path,
# and a stray string would make the caller receive an array, skewing the command line built later.
Write-Host "  no prebuilt/android-framework.jar inside the apktool jar (apktool too old?)"
Write-Host "  Please run manually: java -jar $apktoolJar if -p `"$dir`" <framework-res.apk>"
            return ""
        }
        [System.IO.Compression.ZipFileExtensions]::ExtractToFile($e, $dst, $true)
    } finally {
        $zip.Dispose()
    }
Write-Host "  extracted the framework file from apktool: $dst ($((Get-Item $dst).Length) bytes)"
    return $dst
}

# Generate a debug signing certificate when there is none (keytool ships with the JDK), avoiding a dependency on another machine.
function Ensure-Keystore([string]$jdk, [string]$path) {
    if (Test-Path $path) { return $path }
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $path) | Out-Null
    & "$jdk\bin\keytool.exe" -genkeypair -keystore $path -alias androiddebugkey `
        -storepass android -keypass android -dname "CN=Android Debug,O=Android,C=US" `
        -keyalg RSA -keysize 2048 -validity 10000 2>&1 | Out-Null
    if (Test-Path $path) {
Write-Host "  generated a debug signing certificate: $path"
        return $path
    }
    return ""
}
