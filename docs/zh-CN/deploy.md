# 把模组管理器部署进交战 APK

[English](../deploy.md) | **中文**

本文面向「拿到这套源码，想把它装进自己那份交战（Firefight）APK」的人。全部流程是
两条 PowerShell 命令：先把游戏 APK 反编译成工作树并打上补丁，再编译出一个新的 APK。

---

## 1. 管理器是什么

管理器是一套纯 Java 的 Android 界面代码（`core/src/com/windowsgames/firefight/`），
以**额外的 dex** 追加进交战 APK，并提供三个 Activity：

| 组件 | 作用 |
| --- | --- |
| `MyGame` | 新的启动 Activity。游戏启动时先弹一个提示框「是否使用模组管理器」，选「是」进管理器，选「否」或 15 秒超时直接进游戏 |
| `ModManagerActivity` | 管理器主界面：模组管理 / 地图管理 / 关于管理器 三个页签，标题栏可切换界面语言、可返回游戏 |
| `ModImportActivity` | 从文件管理器 / 其他应用「打开方式」里接收 `.zip`，把模组包导入管理器 |

它不改游戏逻辑，只借用游戏的包名和沙盒：

* **必须是同一个包名、同一份签名**，玩家装的是「游戏 + 管理器」一个 App，存档、模组目录、
  权限都属于这个 App。所以正确做法是把管理器打到你**自己那份已经打过模组的交战 APK** 上，
  而不是从零做一个新 App。
* 管理器要读写模组/地图目录，沿用游戏已声明的 `MANAGE_EXTERNAL_STORAGE` 权限，不额外申请。

---

## 2. 需要准备什么

### 2.1 输入

* **你自己的交战 APK**（已经打过模组的那个）。判定方法：能解出 `smali/com/windowsgames/firefight/`
  与 `assets/` 目录，包名 `com.windowsgames.firefight`，版本 13.2.0（`versionCode 13200`）。
  本项目开发时用的是 `Sean's 13.2.0_Mod_v35(1).apk`（272 MB）。
* 注意：**不要**用未打过模组的原始安装包当输入，那样你辛苦做的模组内容不在里面。

### 2.2 工具链

| 工具 | 版本要求 | 用途 | 脚本怎么找 |
| --- | --- | --- | --- |
| JDK | 17+（开发用 25） | `javac` 编译管理器、`keytool` 生成签名、跑 apktool | `-JdkHome` 或 `JAVA_HOME` |
| Android SDK · platforms | 含 `android.jar`（开发用 android-36.1） | 编译时的 `-cp`/`--lib` | `-AndroidSdk`、`ANDROID_HOME`、`ANDROID_SDK_ROOT`、默认 `%LOCALAPPDATA%\Android\Sdk` |
| Android SDK · build-tools | 含 `d8.bat`、`zipalign.exe`、`apksigner.bat`、`aapt2.exe`（开发用 36.1.0） | 转 dex、对齐、签名、验包 | 同上，取版本号最大的一个 |
| Python | 3.8+ | 生成本地化资源、注入 dex、验包 | `-Python` 或 PATH 上的 `python` |
| apktool | 3.x jar | 反编译 / 回编译游戏 | `-ApktoolJar`、`APKTOOL_JAR`、`core\tools\apktool.jar`，或自动去 `Downloads` 找 |

apktool 需要的框架文件不用自己准备：脚本会从 apktool jar 里取出内置的
`prebuilt/android-framework.jar`，放进 `core/framework/1.apk` 后传给 `apktool -p`。
（这份框架文件与官方 apktool 发布包里的一致，SHA-256
`5DD984016ED5A5EB0EEF866E2C6E8CD352E1427828EC23ADB18005CF5648F3D7`，4,488,013 字节。）

签名证书也不用准备：没给 `-Keystore` 时脚本用仓库里自带的 AOSP 公开测试密钥签名
（`core/keystore/testkey.jks`，别名与口令都是 `testkey`，与官方交战模组打包器用的是同一把）；
该文件不存在时，才退回用 `keytool` 生成 `androiddebugkey`（口令 `android`）放在
`core/keystore/debug.keystore`。两者都只是测试签名；要发布请换成你自己的证书
（`-Keystore` / `-KsPass` / `-KeyAlias` / `-KeyPass`）。

### 2.3 磁盘与系统

* 空余磁盘 **1.5 GB 以上**：工作树约 340 MB，构建中间产物（unsigned/aligned/签名包）约 780 MB，
  输出包约 260 MB。
* Windows + PowerShell（`pwsh` 7 或 Windows PowerShell 5.1；示例里写 `pwsh`，5.1 上换成
  `powershell`）。脚本用的是 PowerShell 语法，Linux/macOS 上需自行改写。

---

## 3. 三步走

在仓库根目录打开 PowerShell：

```powershell
# 0) 允许本地脚本执行（当前会话有效）
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass -Force

# 1) 反编译游戏 + 打补丁（2~4 分钟）
pwsh -File core/tools/prepare_work.ps1 -GameApk "D:\APK\Sean's 13.2.0_Mod_v35(1).apk"

# 2) 编译管理器并出包（5~10 分钟，apktool 回编译 230 MB+ 比较慢）
pwsh -File core/tools/build.ps1

# 3) 静态验证（可选但强烈建议）
pwsh -File core/tools/verify_apk.ps1
```

产物：`out/Firefight-Mod-Manager-13.2.0-mgr0.1.0.apk`（约 260 MB）。

安装到设备：

```powershell
adb install -r out\Firefight-Mod-Manager-13.2.0-mgr0.1.0.apk
```

如果设备上已经装了别的签名的同包名 App，`adb install -r` 会报
`INSTALL_FAILED_UPDATE_INCOMPATIBLE`，此时先卸载旧包（会丢存档，先备份）：

```powershell
adb uninstall com.windowsgames.firefight
```

---

## 4. 这两条命令各自做了什么

### 4.1 `prepare_work.ps1`

1. 解析工具链，准备 `core/framework/1.apk`。
2. `apktool d -f -p core/framework -o core/work/v35 <你的 APK>`，反编译日志写
   `core/logs/decode.log`。
3. 跑 `core/tools/patch_worktree.py`，它对工作树做三件幂等的事：
   * **注册两个 Activity**：在 `AndroidManifest.xml` 里游戏自己的 `MyGame` 之后插入
     `ModManagerActivity`（`exported=false`）与 `ModImportActivity`（`exported=true`，
     带 3 个 intent-filter：`VIEW application/zip`、`VIEW application/x-zip-compressed`、
     `SEND application/zip`）。重复执行不会插第二遍。
   * **放进示例模组**：`core/assets/Mod/` → `work/v35/assets/Mod/`。
   * **让位旧的游戏入口**：把 `smali*/com/windowsgames/firefight/MyGame.smali`
     备份成 `core/backup/MyGame.smali.orig` 后删除 —— 新的 `MyGame` 由 Java 源码提供，
     同一个类名，dex 里只有一份。
4. 自检：打印两个 Activity 是否已注册、旧 smali 是否已移除，不通过就报错退出。

`-Force` 会删掉已有工作树重来；`-SkipPatch` 只反编译不补丁（自己手动改工作树时用）。

### 4.2 `build.ps1`（8 步）

| 步骤 | 做什么 | 关键点 |
| --- | --- | --- |
| [1/8] | `gen_l10n.py` 生成 `res/values*/ffm_strings.xml` | 28 个语种 + 默认英文；同时校验 Java 里用到的每个 `t("key")` 都有定义 |
| [2/8] | 备份并删除旧的 `MyGame.smali` | 幂等：已经删过就跳过 |
| [3/8] | `javac --release 8` 编译 `core/stub` 里的 `SDLActivity` 桩 | 只是为了让管理器代码能编译过，不进包 |
| [4/8] | `javac --release 8` 编译 `core/src` 全部源码 → `jar` | 会打印 class 文件数 |
| [5/8] | `d8 --min-api 24 --release` 转 `classes.dex` | |
| [6/8] | `apktool b -p core/framework -o core/build/unsigned.apk core/work/v35` | 日志 `core/logs/build-apktool.log` |
| [7/8] | `inject_dex.py` 把 `classes.dex` 以 **`classes4.dex`** 追加进 APK | append-only：原来的 dex 与 `.so` 条目一律不重写，保证体积巨大的 `.so` 仍以 `stored`（不压缩）方式存在 |
| [8/8] | `zipalign -f -p 4` + `apksigner sign`（v1+v2） | `-NoSign` 可跳过去拿未签名包 |

构建脚本刻意**没有**用 `$ErrorActionPreference = "Stop"`：`javac`/`d8`/`apksigner`
会把警告写到 stderr，Stop 模式下 PowerShell 会把原生命令的 stderr 当成终止错误。
每一步都显式检查 `$LASTEXITCODE`。

---

## 5. 管理器往 APK 里加了什么（自己核对）

用 `pwsh -File core/tools/verify_apk.ps1` 会自动查这些（G1–G9 全 0 才算通过）：

* `classes4.dex` 存在，且原有 dex / `.so` 条目没被重写（`.so` 仍是 `stored`）；
* `AndroidManifest.xml` 里有 `MyGame`、`ModManagerActivity`、`ModImportActivity`；
* `res` 里有 `ffm_mm_*` 等管理器文案，且中/日译文能查出来（`自定义地图`、`カスタムマップ`）；
* `zipalign`、`apksigner` 校验通过，包名 `com.windowsgames.firefight.mod`、`versionCode 13200`。

---

## 6. 常见问题

**装不上 / 提示签名冲突。** 你手上的游戏与要装的包签名不同。先在设备上卸载旧包再装，
或用同一把证书给你的包签名（`-Keystore`）。

**启动后只有提示框，或者 15 秒后直接进游戏。** 这是预期行为：提示框选「否」或超时都会
直接进游戏；选「是」才进管理器。想改默认行为见 `MyGame.java`。

**管理器里看不到模组/地图。** 管理器读的是游戏沙盒下的模组目录，只显示「已安装」的自定义
模组与地图；没有任何自定义内容时列表为空并显示占位文案。先用游戏自带的导入功能或管理器的
「导入模组」放一份进去。

**设备是 Android 11+ 且管理器读写不了外部目录。** 游戏清单里已声明
`MANAGE_EXTERNAL_STORAGE`，首次进入时系统会引导去授权；管理器里「关于管理器 → 权限」
也提供了跳转。

**apktool 报 `Could not create framework directory: C:\Users\<你>\AppData\Local\apktool\framework`。**
说明它想写默认框架目录但没有权限。本项目的脚本一律传 `-p core/framework`，不碰那个目录。

**磁盘不够 / 想只出未签名包。** `pwsh -File core/tools/build.ps1 -NoSign`。

---

## 7. 回滚

管理器不修改游戏原包的 dex 与资源，全部改动都在工作树里。想回到原状：

```powershell
# 直接用自己的原始 APK 重装
adb install -r "D:\APK\Sean's 13.2.0_Mod_v35(1).apk"
# 或彻底清掉
Remove-Item -Recurse -Force core\work, core\build, out
```

`core/backup/MyGame.smali.orig` 保留着被替换掉的旧入口 smali，需要手工还原工作树时用它。
