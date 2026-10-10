# 开发过程记录

这是一份「为什么代码长成现在这样」的记录。按轮次倒着看也成立：每一节都写了当时
**遇到的问题 → 怎么定位 → 怎么改**。

---

## 第 1–3 轮：纯 Java 外壳 + 三页签管理器

**目标**：不碰游戏本体的 smali，用纯 Java 写一套管理器界面，以额外 dex 追加进 APK。

* `MyGame` 继承 `SDLActivity`，`onCreate` 先 `super` 让游戏正常启动，再弹提示框
  （`PROMPT_SECONDS = 15`）：选「是」进 `ModManagerActivity`，选「否」/超时继续游戏。
* 管理器三页签：模组管理（固定 `MAX_SLOTS = 5` 个槽位）、地图管理（每页 `PAGE_SIZE = 9`
  张自动分页）、关于管理器。
* 模组名读 `mod.txt` 的 `<name>`，取不到显示 `NA`；地图名读 `<CODE>-scenarios.txt` 的
  `<name>`，取不到显示 4 位代码。
* 多语言：文案数据表放 Python（`l10n_data.py` / `l10n_extra.py` / `l10n_round3.py`），
  构建时由 `gen_l10n.py` 生成 `res/values*/ffm_strings.xml`，Java 侧只写 `t("ffm_...")`。
  这样做的好处是文案集中可校验：`gen_l10n.py` 会扫 Java 源码里的键，缺定义直接让构建失败。
* 地图侧的事实来自游戏 `libmain.so` 的字符串表：地图目录 `Custom Maps/<4 字符代码>/`，
  且必须把代码登记进 `CustomMapCodeNames.txt`，否则游戏不认。

---

## 第 4–6 轮：内容区一片空白，最后定位到「首帧测量」

**现象**：管理器界面能出来，标题、统计行、三个页签都正常，但页签下面整片空白。

**走过的弯路**：一度判断是 `LinearLayout` 的 `weight` 在 `AT_MOST` 测量下把子项高度算成了 0，
于是加了自定义 `ScrollView` 子类在 `AT_MOST` 时改成 `EXACTLY` 兜底 —— 没解决。

**怎么定位的**：做了一版「诊断版」，把内容容器改成半透明红底，900 ms 后把实测值写进统计行：

```
[r=1440 n=6 ch=0 cw=2506 lh=0 lw=2506 vis=0]
```

* `n=6` —— 子项确实建全了（6 个：无模组文案 + 间距 + 间距 + 导入按钮 + 间距 + 打开目录按钮）；
* `cw=2506` / `lw=2506` —— 子项拿到过整行宽度，布局确实跑过；
* **`ch=0` / `lh=0`** —— 内容容器在那一帧被测量成 **0 可用高度**，所以所有子项高度都是 0。

再结合「随便点一下页签内容就出来了」（点页签会重建子视图并重跑测量），根因明确是
**首帧测量时机**，与配色、可见性、surface 遮挡都无关。

**改法**（第 6 版）：让内容视图本身就是一个整屏 `ScrollView`：

```java
ScrollView sv = new ScrollView(this);
sv.setFillViewport(true);
LinearLayout root = Ui.column(this);
sv.addView(root, new FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
```

窗口给 `ScrollView` EXACTLY 整屏高 → `ScrollView` 以 UNSPECIFIED 量内层 → 内层与子项
只按自然高度测量，容器不可能再塌成 0。同时删掉那个 `FillScroll` 兜底类，只留一个
`settleLayout()`（400 ms 后仍为 0 高就重建当前页并记日志）。

**顺带修掉的坑**：分隔线一直没画出来，因为 `Ui.fill()` 覆盖了 `Ui.divider()` 自带的 1dp
`LayoutParams`，把高度变成了 `WRAP_CONTENT`（即 0）；改成显式传
`new LinearLayout.LayoutParams(MATCH_PARENT, Math.max(1, Ui.dp(this, 1)))`。

**同一轮还做了**：语言切换按钮移到「返回」左边（新增 `Ui.headerAction()`，
`minWidth/minimumWidth/minimumHeight` 归零，避免把标题挤没）、删掉所有调试用的红底与
`probe()`/`debugLine()`、把界面文案改得更客观（「（未提供）」→「（未提供）」类占位统一、
「已导入为禁用状态；启用后生效。」等）。

---

## 第 7 轮：关于页链接落地

把官网、仓库、作者 GitHub、B 站、爱发电、邮箱六行填进「关于管理器」，点击分别用
`Intent.ACTION_VIEW` / `mailto:` 打开；`ID_BILI = 31`。多语言表里补 `mm_about_bili`。

---

## 第 8 轮：整理成 core/ + docs/，并仓库化

**目标**：把当初堆在一个大目录里的工程整理成「只留 `core/` 与 `docs/`」，脚本可移植，
清理死代码，然后在本地建成 git 仓库（先不推送）。

### 死代码清理

先用脚本统计「只出现一次」的成员（即只有声明没有调用者），再逐条核对，删掉 183 行：

| 文件 | 删除的成员 |
| --- | --- |
| `ModImporter.java` | `listBuiltin` / `uninstall` / `rollback` / `installBuiltin` / `newestBackup` / `copyAssetDir`（−146 行） |
| `MapImporter.java` | `syncCodes`（−21 行） |
| `LogStore.java` | `dump`（−3 行） |
| `Ui.java` | `margins` / `wrap` / `accentText`（−13 行） |

外加一个无用 import。**保留** `MyGame.getContext()`：它是 SDL/计费代码按签名静态调用的对外
钩子。清理后重编：class 文件从 34 变成 33（少的是 `newestBackup` 里匿名 `Comparator` 产生的
内部类），`classes.dex` 从 68732 降到 65912 字节，门禁全过。

### 脚本可移植化

原来的 `build.ps1` 把 JDK、SDK、apktool、keystore、python 的绝对路径写死在文件里，换台机器
就废。现在拆成 `common.ps1`（工具链查找 + 框架/证书准备）+ `prepare_work.ps1`（反编译打补丁）
+ `build.ps1`（8 步构建）+ `verify_apk.ps1`（G1–G9）。

**apktool 框架文件不用进仓库**：apktool jar 里本来就带 `prebuilt/android-framework.jar`
（4,488,013 字节，SHA-256 `5DD984...F3D7`），与工程里的 `framework/1.apk` 逐字节相同，
`Ensure-Framework` 构建时现取。这也解决了「apktool 默认想写
`%LOCALAPPDATA%\apktool\framework` 但没权限」的报错。

### 端到端复现验证

整理完成后，用**新脚本从零跑了一遍**（这是这套脚本存在的意义）：

```
prepare_work.ps1 -GameApk "Sean's 13.2.0_Mod_v35(1).apk"   → 反编译 + 打补丁 + 自检通过
build.ps1                                                   → class 33 / classes.dex 65908 B
                                                              unsigned 259.1 MB / signed 260 MB
                                                              classes4.dex stored, stored-so-violations: 0
verify_apk.ps1                                              → RESULT: PASS（G2..G9 全 0）
产物: out/Firefight-Mod-Manager-13.2.0-mgr0.1.0.apk  272,628,824 B
```

复现出的包与手工构建的那版**体积一致**（SHA-256 会因 apktool 回编译的 zip 时间戳等差异而不同，
属正常）。

修掉的两处问题：

* `patch_worktree.py` 找旧 `MyGame.smali` 时判断的是「目录名以 smali 开头」，而真实路径是
  `smali/com/windowsgames/firefight/MyGame.smali`（目录名是 `firefight`），所以一直报「不在，
  跳过」。改成看**相对工作树路径的第一段**是否以 `smali` 开头，并打印被删的文件。
* `prepare_work.ps1` 的自检用同样的错路径判断，改成递归查找。
* `common.ps1` 的 `Ensure-Keystore` / `Ensure-Framework` 把提示信息用 `Write-Output` 输出，
  结果函数「返回」的是「提示 + 路径」的数组；调用方拼 apksigner 命令行时，提示字符串被当成
  了输入 APK，报 `Unexpected parameter(s) after input APK (--ks-pass)`。改成 `Write-Host`，
  调用方再取最后一项兜底。

---

## 踩过的坑（速查）

| 坑 | 教训 |
| --- | --- |
| `weight` 在 `AT_MOST` 下塌成 0 | 假说错了；要先用实测读数（红底 + 尺寸打点）证伪，再改代码 |
| 分隔线画不出来 | `Ui.fill()` 会把控件自带的 `LayoutParams` 覆盖成 `WRAP_CONTENT`，这类「尺寸工具」要么显式传参，要么别用 |
| `javac: 不兼容的类型: int 无法转换为 String` | 改方法返回值时回头搜调用点 |
| `aapt2: unescaped apostrophe in string` | 生成 XML 字符串时要转义 `'`、`"`、换行、行首 `@`/`?`（`gen_l10n.py` 的 `android_escape()`） |
| apktool 报 `Could not create framework directory` | 一律传 `-p core/framework`，别让它写用户目录 |
| PowerShell 脚本一跑就 `PSSecurityException` | `Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass -Force` |
| 构建脚本用 `$ErrorActionPreference = "Stop"` | 原生命令（javac/d8）往 stderr 写警告会被当终止错误；改 `Continue` + 查 `$LASTEXITCODE` |
| apktool 回编译几百 MB 的包 | 慢是正常的（3–8 分钟），日志写文件，别指望终端输出 |
| 只是想改工作树里的文件，却被报「文件不存在，重新读」 | 文件系统层对「先删后写」有版本守卫；重新创建同名文件时换个思路（先写到新名字再改名） |
