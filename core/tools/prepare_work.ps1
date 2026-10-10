# Firefight 模组管理器 —— 准备反编译工作树
#
# 把「已经打好模组的交战 APK」反编译到 core/work/v35，并自动打上管理器需要的
# 工作树改动（AndroidManifest 里加两个 Activity、示例模组、让位旧的 MyGame.smali）。
#
# 用法（在仓库根目录）:
#   pwsh -File core/tools/prepare_work.ps1 -GameApk "D:\path\Sean's 13.2.0_Mod_v35(1).apk"
#   pwsh -File core/tools/prepare_work.ps1 -GameApk <apk> -Force     # 覆盖已有工作树
#
# 之后跑 core/tools/build.ps1 出包。
#
# 为什么要用「打好模组的」APK：管理器和游戏本体是同包名同签名的整体，管理器挂在
# 你自己的模组包上，玩家的存档与模组目录才在同一个 App 沙盒里。详见 docs/deploy.md。

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

Write-Step "[0/4] 检查工具链"
if (-not (Test-Path $GameApk)) { Fail "game-apk" "找不到交战 APK: $GameApk" }
$jdk = Resolve-JdkHome $JdkHome
if (-not $jdk) { Fail "jdk" "找不到 JDK。装 JDK 17+ 或用 -JdkHome 指定。" }
$env:JAVA_HOME = $jdk
$apktool = Resolve-Apktool $ApktoolJar
if (-not $apktool) { Fail "apktool" "找不到 apktool jar。放在 core/tools/apktool.jar 或用 -ApktoolJar 指定。" }
$py = Resolve-Python $Python
if (-not $py) { Fail "python" "找不到 python。安装 Python 3，或用 -Python 指定。" }
Write-Output "  JDK     : $jdk"
Write-Output "  apktool : $apktool"
Write-Output "  python  : $py"
Write-Output "  输入 APK: $GameApk ($([math]::Round((Get-Item $GameApk).Length/1MB,1)) MB)"

if (-not (Ensure-Framework $apktool $FrameDir)) { Fail "framework" "框架文件准备失败（见上面提示）。" }

Write-Step "[1/4] 反编译到 $WorkTree"
if (Test-Path $WorkTree) {
    if (-not $Force) {
        Fail "exists" "工作树已存在: $WorkTree ；确认要重新生成就加 -Force（会先删除）。"
    }
    Remove-Item -Recurse -Force $WorkTree
}
$decodeLog = Join-Path $LogDir "decode.log"
& "$jdk\bin\java.exe" -Xmx4g -jar $apktool d -f -p "$FrameDir" -o $WorkTree $GameApk *> $decodeLog
if ($LASTEXITCODE -ne 0) { Get-Content $decodeLog -Tail 30; Fail "apktool-d" $LASTEXITCODE }
Write-Output "  反编译完成，日志: $decodeLog"

Write-Step "[2/4] 应用管理器补丁"
if ($SkipPatch) {
    Write-Output "  -SkipPatch: 跳过（自己改工作树时用）"
} else {
    & $py "$ToolsDir\patch_worktree.py" -w $WorkTree -c $CoreDir
    if ($LASTEXITCODE -ne 0) { Fail "patch" $LASTEXITCODE }
}

Write-Step "[3/4] 自检"
$manifest = Join-Path $WorkTree "AndroidManifest.xml"
$okManifest = (Select-String -Path $manifest -Pattern "ModManagerActivity" -Quiet)
$okImport = (Select-String -Path $manifest -Pattern "ModImportActivity" -Quiet)
$leftover = Get-ChildItem $WorkTree -Recurse -Filter "MyGame.smali" -ErrorAction SilentlyContinue |
            Where-Object { $_.FullName -like "*\smali*" }
$okSmali = -not $leftover
Write-Output "  ModManagerActivity 已注册 : $okManifest"
Write-Output "  ModImportActivity 已注册  : $okImport"
Write-Output "  旧 MyGame.smali 已移除    : $okSmali"
if (-not ($okManifest -and $okImport)) { Fail "selfcheck" "AndroidManifest.xml 补丁没生效" }
if (-not $okSmali) { Fail "selfcheck" "还有旧 MyGame.smali 留在工作树里: $($leftover.FullName -join ', ')" }

Write-Step "[4/4] 下一步"
Write-Output "  pwsh -File core/tools/build.ps1"
Write-Output "=== PREPARE OK ==="
