# 目录约定

[English](../project-layout.md) | **中文**

## 1. 顶层只放两类目录

```
Firefight-Mod-Manager-Android/
├── core/        全部「代码与资源」：源码、桩、随包资源、脚本、构建工作区
├── docs/        全部文档
├── README.md    入口
├── LICENSE      GPL-3.0
└── .gitignore
```

没有 `build/`、`out/`、`_docs/`、`_dl/` 这些散落目录：凡是构建出来的东西都在 `core/` 里
各自的位置，凡是写给人看的都在 `docs/` 里。文档入口只有 `README.md`，它链到 `docs/`。

## 2. core/ 里各目录的职责

| 目录 | 内容 | 进仓库？ |
| --- | --- | --- |
| `core/src/` | 管理器 Java 源码（`com/windowsgames/firefight/*.java`） | ✅ |
| `core/stub/` | 编译期桩（`org/libsdl/app/SDLActivity.java`），不进包 | ✅ |
| `core/assets/` | 随包发布的资源（示例模组 `Mod/`） | ✅ |
| `core/tools/` | 全部脚本（PowerShell + Python） | ✅ |
| `core/work/` | 反编译出来的游戏工作树（`work/v35/`） | ❌ 生成物，几百 MB |
| `core/build/` | javac/d8/apktool 中间产物 | ❌ |
| `core/framework/` | apktool 框架文件 `1.apk`（可从 apktool jar 现取） | ❌ |
| `core/backup/` | 被替换掉的旧 `MyGame.smali` 等备份 | ❌（可留作参考，但属于本地状态） |
| `core/keystore/` | 自动生成的调试证书 | ❌ 绝不进仓库 |
| `core/logs/` | 构建/反编译日志 | ❌ |
| `out/` | 构建产物 APK | ❌ |

`.gitignore` 已经把上面 ❌ 的全部排除。**仓库里只有源码、脚本和文档，小于 1 MB。**

## 3. 为什么不把工作树放进仓库

`core/work/v35/` 是从用户自备的交战 APK 反编译出来的（约 340 MB，含游戏自己的
`res`、`lib/*.so`、`assets`），既大又不该由本项目分发。它由
`core/tools/prepare_work.ps1` 在本地现场生成：

```powershell
pwsh -File core/tools/prepare_work.ps1 -GameApk <你的交战 APK>
```

生成之后，管理器需要的改动（清单里两个 Activity、示例模组、删旧 smali）由
`patch_worktree.py` 幂等补齐。

## 4. 新东西该放哪

| 你要加的东西 | 放哪 |
| --- | --- |
| 管理器的一个新界面 / 逻辑 | `core/src/com/windowsgames/firefight/`，需要的话在 `ModManagerActivity` 里挂事件 |
| 一句新文案 | `core/tools/l10n_round3.py`（或 `l10n_extra.py`），键名 `ffm_mm_*` |
| 一个新的构建/验证步骤 | `core/tools/`，PowerShell 脚本引 `common.ps1` 取路径与工具链 |
| 一份说明 | `docs/`，并在 `README.md` 的文档表里加一行 |
| 随包的示例数据 | `core/assets/`（会被 `patch_worktree.py` 同步进工作树） |

## 5. 编码约定

* Java：`--release 8`，不引入任何第三方依赖；界面全部用代码构建（没有 XML 布局资源），
  文案一律走 `L10n.t(...)`。
* 脚本：PowerShell 7（`pwsh`），路径从 `common.ps1` 取，不写死绝对路径；
  工具链找不到要说清楚缺什么。
* 文案数据：Python 字典，`("key", "value")` 元组列表 + 各语种字典，缺键回落英文。
