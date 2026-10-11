# 用 MT 管理器（MCP）给《交战》注入模组管理器

[English](../deploy-mt-mcp.md) | **中文**

电脑端路线（见[部署指南](deploy.md)）要在 PC 上装齐 JDK、Android SDK 与 apktool。
本文是**手机端路线**：用 MT 管理器的 MCP 接口，把 PC 端编译好的管理器 `classes.dex`
追加进 APK，再改清单、回编译、签名，全程不依赖 PC 工具链。下面的流程是实测跑通的。

---

## 1. 输入 / 输出

* 输入：原版 `交战13.2.0.APK`（包名 `com.windowsgames.firefight`，`versionCode 13200`，
  3 个 dex）＋ 已编译好的 `classes.dex`（≈68 KB，即 PC 端 `build.ps1` 第 [5/8] 步的产物）。
* 输出：包名 `com.windowsgames.firefight.mod`、testkey 签名、可直接安装的 APK。

---

## 2. MT MCP 的能力边界

1. 没有 shell / JDK / SDK / apktool / PowerShell → **不能编译 Java**，`classes.dex` 必须先由
   PC 端产出。
2. `mt_apk_copy_entry` / `mt_apk_patch_bytes` / `mt_apk_delete_target` **拒绝对 `classes*.dex`
   操作**。
3. `mt_zip_update` 把 APK 当普通 ZIP，可以追加 `classes4.dex`（**不签名**）。
4. 同一描述符存在于多个 dex 时：读取取**第一个 dex**，删/写报 `AMBIGUOUS_CLASS_IN_DEX`，
   且**没有按 dex 限定的 locator**。
5. `mt_apk_workspace_open` 要求是含 `AndroidManifest.xml` 的合法 APK，否则报 `NOT_APK`。
6. `mt_zip_create` / `mt_zip_extract` 是通用 ZIP 工具，可以处理 `AndroidManifest.xml`、
   `classes.dex`。

---

## 3. 流程

```text
[准备] PC: build.ps1 -> classes.dex（管理器）
  |
[0] 复制底包为工作副本（不动原包）
  |
[1] mt_zip_update: classes.dex -> classes4.dex 追加
  |
[2] mt_apk_workspace_open(工作副本) + mt_apk_session_create
  |
[3] 消除 MyGame 重名
    3.1 mt_zip_extract 取底包 AndroidManifest.xml
        mt_zip_create -> mgr_only.apk {AndroidManifest.xml, classes.dex}
        open -> read dex_class:.../MyGame   (= 新版 smali, 515 行)
    3.2 delete dex_class:.../MyGame -> build(sign=false) -> mgr_only_fixed.apk
    3.3 mt_zip_extract -> mgr_fixed.dex
    3.4 mt_zip_update: mgr_fixed.dex -> classes4.dex（覆盖）
  |
[4] 重开工作副本 + 新会话; edit_text(write_target) 写回新版 MyGame
  |
[5] edit_text(axml) 一次原子改 5 处:
    package -> .mod; permission/uses-permission 名 -> *.mod.*;
    provider authorities -> *.mod.androidx-startup;
    MyGame 后插入 ModManagerActivity / ModImportActivity(+intent-filter)
  |
[6] 可选: resource_add 补 ffm_*; copy_entry 放 assets/Mod
  |
[7] mt_apk_session_check(runBuildChecks=true) -> passed
  |
[8] mt_apk_build(sign=true) -> testkey 签名成品
  |
[9] 重开成品验收
```

---

## 4. 注意事项

1. **重名类必须消除**：管理器 dex 与游戏 dex 都有入口 `MyGame`；都留则 `classes.dex` 优先、
   旧版遮蔽新版 → 管理器不弹。按 [3] 把管理器 dex 的 `MyGame` 去掉，改由游戏 dex 承载新版。
2. **`.so` 必须保持 STORED**（`extractNativeLibs=false`）：`mt_zip_update` / `mt_apk_build`
   都不重压既有条目；验收看 `lib/**` 压缩前后字节数相等。
3. **`ffm_*` 资源缺失会回退成键名**（`L10n.t()` 按名字 `getIdentifier`），能用但难看。
4. **改包名要连带改**：自定义权限名、provider authority，否则与原版共存会报
   `INSTALL_FAILED_CONFLICTING_PROVIDER`。
5. **会话是快照**：打开之后又改了 APK 文件，必须重开工作区再建会话。
6. 新 Activity 的 `android:name` 用**全限定名**（`com.windowsgames.firefight.ModManagerActivity`）。
7. `session_check` 只做内容检查，不代表可安装；正式出包走 `mt_apk_build`。
8. 收尾清理 `temporary=true` 的工作区与中间文件。

---

## 5. 与 PC 脚本对照

| PC | MT MCP |
| --- | --- |
| `patch_worktree.py` 改清单 | [5] `edit_text(axml)` |
| `patch_worktree.py` 删旧 `MyGame.smali` | [3] |
| `javac` + `d8` | 需离线完成 |
| `apktool b` | `mt_apk_build` |
| `inject_dex.py` 追加 `classes4.dex` | `mt_zip_update` |
| `zipalign` + `apksigner`(testkey) | `mt_apk_build(sign=true)` |

---

## 6. 验收

* 包名 `com.windowsgames.firefight.mod`，`versionCode 13200`；
* 证书 SHA-256 `a40da80a59d170caa950cf15c18c454d47a39b26989d8b640ecd745ba71bf5dc`；
* 4 个 dex；管理器 33 个类；`MyGame` 唯一且为新版；
* Manifest 含 `MyGame` / `ModManagerActivity` / `ModImportActivity`；
* `lib/**/*.so` 为 STORED。

实测：`Firefight-Mod-Manager-13.2.0.apk`（578,621,576 B）以上全部通过；仅 `ffm_*` 文案与
`assets/Mod` 未补。
