# 开发过程记录

[English](../history.md) | **中文**

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
* 多语言：文案数据表放 Python（`l10n_data.py` / `l10n_extra.py` / `l10n_round3.py`，第 9 轮又加了
  `l10n_round4.py`），
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

## 第 9 轮：Mod 裸数据化、地图顺序与启用禁用、注释精简

**需求**：①`Mod/` 里不能套子目录，必须是裸数据；②地图目录按
`CustomMapCodeNames.txt` 的行序列出并能在管理器里改顺序；③地图也要有启用/禁用；
④模组或地图改完提示「重启生效」；⑤代码注释只留「这是干什么的 / 什么没有实现」，其余删掉。

**怎么改的**：

* `ModImporter`：`Mod/` 现在只放当前启用模组的数据文件（平铺、无 `<id>/`）；未启用的模组留在
  库目录 `Mod-disabled/<id>/`，活动模组的 id 记在 `Mod-disabled/.ffm-enabled`——这个状态文件
  刻意放在库里，就是为了让 `Mod/` 保持纯数据。启用一个模组时会先把当前启用那套数据整套搬回
  它自己的库目录，所以**同一时间只有一个模组启用**。搬运用 `moveData()` 逐个 `renameTo`，
  失败再退回「复制 + 删源」。
* `MapImporter`：`listMaps()` 不再按目录名排序，而是**按代码表的行序**列出（表里多出来的代码也
  列出来、标记为「目录缺失」）；新增 `moveCode()` 把相邻两条代码行对调后写回；启用/禁用就是
  `registerCode()` / `unregisterCode()`——**地图目录不动**，只是代码表里有没有这一行。
* `ModManagerActivity`：地图每行加「启用/禁用、上移、下移」三个按钮（用页内下标定位）；
  新增 `promptRestart()`（弹框，可直接「立即重启」重新拉起 `MyGame`），挂在模组启用/禁用/删除、
  地图启用/禁用/删除/移动/导入之后；模组页与地图页各加一句说明文案。
* 文案：新增 `core/tools/l10n_round4.py`（7 个键 × 11 个语种），`gen_l10n.py` 合并。

**注释精简**：先写 `strip_comments.py` 删除注释（Java 手写扫描器跳过字符串/字符字面量，
Python 走 `tokenize` 只删 `COMMENT`，PowerShell 逐行做引号感知），再给每个文件补 1–3 行英文头，
只写「做什么」和「没实现什么」。译文数据文件（`l10n_*.py`）动完要用 `git diff --unified=0`
确认「非注释新增行 = 0」，否则会把译文一起改坏。

**结果**：class 文件 34 个、`classes.dex` 69144 字节、Java 实际引用的 81 个键全部有译文、
`values-zh-rTW 192/193`（唯一缺的是已废弃、Java 已不再引用的 `about_github`）、
`stored-so-violations: 0`，构建与 `verify_apk.ps1` 均通过。

---

## 第 10 轮：改用 AOSP 公开 testkey 作为默认签名

**问题**：之前的包用的是本机生成的调试证书，而游戏和官方打包器用的是 AOSP 公开 testkey，
测试包无法与任何可复现的签名对上。

**改法**：把 `core/keystore/testkey.jks`（别名与口令都是 `testkey`，与官方交战模组打包器
`keys/testkey.jks` 是同一把）收进仓库，没给 `-Keystore` 时自动用它；自己生成的调试证书只作
兜底。`build.ps1` 增加 `-KsPass` / `-KeyAlias` / `-KeyPass` 并打印实际使用的证书，
`.gitignore` 忽略 `core/keystore/*` 但保留 testkey。

**结果**：`build.ps1 -OutName ...-testkey.apk` → `BUILD OK`；
`apksigner verify --print-certs` 显示证书 SHA-256
`a40da80a59d170caa950cf15c18c454d47a39b26989d8b640ecd745ba71bf5dc`；
`verify_apk.ps1` → `RESULT: PASS`。

**本轮定下的约定**：发到 GitHub 上的一切（仓库简介、提交信息、PR 标题与正文、Issue）都用英文；
文档保留中文版放在 `docs/zh-CN/`。

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
| 用朴素文本替换「删注释 / 翻注释」 | 会把 Python docstring、字符串里的中文一起改掉（5 个工具文件直接语法崩）；必须用解析器（`tokenize`）或「显式行号 → 整行新文本」的映射，改完跑 `py_compile` |
