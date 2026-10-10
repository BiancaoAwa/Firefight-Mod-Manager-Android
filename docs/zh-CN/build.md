# 构建脚本

[English](../build.md) | **中文**

全部脚本在 `core/tools/`。日常只需要两条命令，细节在这里。

| 脚本 | 作用 |
| --- | --- |
| `common.ps1` | 公共库：路径变量、工具链自动查找、框架/证书准备。其它脚本 `. "$PSScriptRoot\common.ps1"` 引它 |
| `prepare_work.ps1` | 反编译游戏 APK → `core/work/v35`，再打管理器补丁 |
| `build.ps1` | 8 步构建：生成本地化 → 编译 → d8 → apktool 回编译 → 注入 dex → 对齐签名 |
| `verify_apk.ps1` | 静态验证 G1–G9，全通过打印 `RESULT: PASS` 并以 0 退出 |
| `smoke_test.ps1` | 上机冒烟：`adb install -r` → 启动 → 截图 → 抓 logcat/崩溃/目录清单 |
| `gen_l10n.py` | 合并三份文案表，生成 `res/values*/ffm_strings.xml`，校验键 |
| `patch_worktree.py` | 对工作树做幂等补丁（清单 + 示例模组 + 删旧 MyGame.smali） |
| `inject_dex.py` | 把 `classes.dex` 以 `classes4.dex` 追加进 APK（不重写既有条目） |
| `verify_apk.py` | G2 的 zip 层检查（dex 数量、`.so` 是否仍 stored） |
| `apk_grep.py` / `apk_list.py` | 排查用的 APK 内容检索/列表小工具 |

## 1. 参数

```powershell
# 反编译 + 打补丁
pwsh -File core/tools/prepare_work.ps1 -GameApk <你的交战 APK> [-Force] [-SkipPatch]
                                   [-JdkHome <dir>] [-ApktoolJar <jar>] [-Python <exe>]

# 构建
pwsh -File core/tools/build.ps1 [-JdkHome <dir>] [-AndroidSdk <dir>] [-ApktoolJar <jar>]
                                [-Python <exe>] [-Keystore <file>] [-KsPass <pw>]
                                [-KeyAlias <name>] [-KeyPass <pw>] [-OutDir <dir>] [-OutName <name>] [-NoSign]

# 验证
pwsh -File core/tools/verify_apk.ps1 [-Apk <file>] [-JdkHome <dir>] [-AndroidSdk <dir>] [-Python <exe>]
```

签名后的 APK 默认写进仓库里的 `out/`（已被 git 忽略）；想放到别处就传 `-OutDir <dir>`。

## 2. 工具链怎么找的

| 需要 | 查找顺序 |
| --- | --- |
| JDK | `-JdkHome` → `JAVA_HOME` → PATH 上的 `java`（必须带 `bin\javac.exe`） |
| Android SDK | `-AndroidSdk` → `ANDROID_HOME` → `ANDROID_SDK_ROOT` → `%LOCALAPPDATA%\Android\Sdk` |
| `android.jar` | SDK 下 `platforms\android-*` 里版本号最大的一个 |
| build-tools | SDK 下 `build-tools\*` 里版本号最大且含 `d8.bat` 的一个 |
| apktool | `-ApktoolJar` → `APKTOOL_JAR` → `core\tools\apktool.jar` → `core\tools\apktool*.jar` → `Downloads\apktool*.jar` |
| Python | `-Python` → PATH 上的 `python` / `python3` / `py` |
| 签名证书 | `-Keystore` → `core\keystore\testkey.jks`（AOSP 公开测试密钥，别名与口令都是 `testkey`）→ `core\keystore\debug.keystore`（不存在就用 `keytool` 现生成） |
| 口令 / 别名 | `-KsPass` / `-KeyAlias` / `-KeyPass`；不给就按选中的证书取默认值（`testkey`，或 `android` + `androiddebugkey`） |
| apktool 框架 | 每次构建前用 `Ensure-Framework` 从 apktool jar 里取 `prebuilt/android-framework.jar` → `core/framework/1.apk` |

找不到任何一个都会明确报出「装什么 / 用哪个参数指定」并退出，不会半路失败。

## 3. 退出码与日志

* 脚本用 `Fail` 打印 `[FAIL] <步骤> 退出码 N` 并以 1 退出；成功以 `=== BUILD OK ===` /
  `=== PREPARE OK ===` / `RESULT: PASS` 结束。
* 日志：反编译 `core/logs/decode.log`，apktool 回编译 `core/logs/build-apktool.log`，
  `aapt2 dump resources` 的结果 `core/logs/apk-resources.txt`。
* 构建脚本**故意不用** `$ErrorActionPreference = "Stop"`：`javac`/`d8`/`apksigner` 把警告写
  stderr，Stop 模式下 PowerShell 会把原生命令的 stderr 当成终止错误。每一步显式查
  `$LASTEXITCODE`。

## 4. 一次成功的构建长什么样

```
[0/8] 检查工具链 ...            JDK / android.jar / build-tools / apktool / python
[1/8] 生成 res/values*/ffm_strings.xml     语种数: 28 (+ 默认英文)
                                            Java 实际引用的键: 74 个，全部已覆盖
[2/8] 备份并移除旧 MyGame.smali            backup -> core/backup/MyGame.smali.orig (48557 bytes)
[3/8] javac 桩类（SDLActivity）
[4/8] javac 管理器源码                     class 文件: 33
[5/8] d8 -> classes.dex                    classes.dex: ≈65000 bytes
[6/8] apktool 回编译工作树                 unsigned.apk: ≈259 MB
[7/8] 注入 classes4.dex（append-only）     classes4.dex stored / stored-so-violations: 0
[8/8] zipalign + apksigner                 signed: out\...apk (≈260 MB)
=== BUILD OK ===
```

`verify_apk.ps1` 的门禁：

| 门禁 | 检查 |
| --- | --- |
| G1 | 产物存在、大小、时间 |
| G2 | zip 层：`classes.dex` 之外多了 `classes4.dex`；`.so` 仍是 `stored`（没有被重压缩） |
| G3 | `zipalign -c -v 4` |
| G4 | `apksigner verify --print-certs` |
| G5 | `aapt2 dump badging`（包名 `com.windowsgames.firefight.mod`、`versionCode 13200`） |
| G6 | `dexdump` 打印管理器 dex 的类表 |
| G7 | 体积 |
| G8 | `ffm_*` 资源进包、抽查 23 个键、中/日译文可查 |
| G9 | 清单里有 `MyGame` / `ModManagerActivity` / `ModImportActivity` |

## 5. 改文案 / 加语言的流程

1. 在 `core/tools/l10n_round4.py`（最新一轮；更早的键在 `l10n_round3.py` / `l10n_extra.py` /
   `l10n_data.py`）里加键和译文；
2. 在 Java 里用 `t("ffm_mm_你的键")` 引用；
3. 跑 `build.ps1` —— `gen_l10n.py` 会校验「Java 用到但没定义」和「定义了却拼错」的键，
   不通过就中止。

加语言：在 `gen_l10n.py` 的语种表里加 locale，并在三份数据表里补该语种的字典；
缺的键会自动回落英文（构建日志会打印 `values-<locale> 43/186 键` 这样的覆盖率提示）。

> 现状：28 个语种里，10 个是「全量」（186 键，只差一个早已不再使用的遗留键
> `about_github`），另外 18 个是早期版本只翻了首轮 43 个键，其余一律回落英文。
> 想补全某个语种，就往对应的数据表里加键即可。

## 6. 常见问题

**`AuthorizationManager check failed` / `PSSecurityException`。**
PowerShell 执行策略拦住了脚本。当前会话放开：
`Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass -Force`。

**`apktool: command not found` / 找不到 jar。**
把 apktool 3.x 的 jar 放到 `core/tools/apktool.jar`，或用 `-ApktoolJar` 指定。

**回编译很慢或内存不足。**
`apktool b` 走 `java -Xmx4g`；工作树 230 MB+，回编译 3–8 分钟属正常。磁盘也要留 1.5 GB。

**构建成功但装不上。**
见 [deploy.md](deploy.md) 第 6 节（签名冲突 / 先卸载）。
