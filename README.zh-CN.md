# Firefight Mod Manager (Android)

[English](README.md) | **中文**

把一套**纯 Java 的模组管理器界面**追加进你自己的 Firefight（交战）Android APK：
不改游戏逻辑，和游戏同包名同签名，共用存档、模组目录与权限。

* 启动时弹提示框「是否使用模组管理器」：选「是」进管理器，选「否」或 15 秒超时直接进游戏。
* 管理器三个页签：**模组管理**（启用 / 禁用 / 删除 / 导入，固定 5 个槽位）、
  **地图管理**（自定义地图分页浏览、导入）、**关于管理器**（版本、官网、仓库、作者信息）。
* 界面语言 28 种，可随时在标题栏切换，游戏本体不受影响。
* 管理器以额外的 `classes4.dex` 追加进 APK，原有的 dex 与体积巨大的 `.so` 条目不重写。

## 快速开始

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass -Force
pwsh -File core/tools/prepare_work.ps1 -GameApk "D:\APK\你的交战.apk"   # 反编译 + 打补丁
pwsh -File core/tools/build.ps1                                        # 编译 + 注入 + 签名
pwsh -File core/tools/verify_apk.ps1                                   # 静态验证
adb install -r out\Firefight-Mod-Manager-13.2.0-mgr0.1.0.apk
```

（示例里的 `pwsh` 指 PowerShell 7；只有 Windows PowerShell 5.1 时把 `pwsh` 换成 `powershell`，两者都能跑。）

需要什么工具、每一步在干什么、装不上怎么办：**[docs/deploy.md](docs/zh-CN/deploy.md)**。

## 目录结构

```
core/                 # 全部「core」：源码、资源、构建脚本
  src/                #   管理器 Java 源码（com.windowsgames.firefight）
  stub/              #   只为编译通过的 SDLActivity 桩，不进包
  assets/Mod/         #   随包发布的示例模组
  tools/              #   构建与验证脚本（PowerShell + Python）
  work/               #   [生成] 反编译出来的游戏工作树
  build/ out/ logs/   #   [生成] 中间产物 / 产物 / 日志
docs/                 # 文档
```

工作树、构建产物、日志、框架文件都不进版本库，见 `.gitignore`；
仓库里只有源码、脚本和文档（< 1 MB）。

## 文档

| 文档 | 内容 |
| --- | --- |
| [docs/deploy.md](docs/zh-CN/deploy.md) | **部署指南**：需要的工具、两条命令做了什么、往包里加了什么、常见问题、回滚 |
| [docs/build.md](docs/zh-CN/build.md) | 构建脚本参数、工具链解析顺序、脚本自身怎么验证 |
| [docs/architecture.md](docs/zh-CN/architecture.md) | 管理器代码结构：每个类负责什么、界面怎么搭、数据存在哪 |
| [docs/mod-format.md](docs/zh-CN/mod-format.md) | 模组包与自定义地图的文件格式（做模组/地图时看） |
| [docs/project-layout.md](docs/zh-CN/project-layout.md) | 目录约定与「哪些东西不该进仓库」 |
| [docs/history.md](docs/zh-CN/history.md) | 开发过程记录：每一轮改了什么、踩了什么坑 |

## 仓库约定

发到 GitHub 上的一切都用**英文**写：仓库简介、提交信息（commit message）、Pull Request 的
标题与正文、Issue 正文。源码注释和脚本输出也是英文。只有文档有中文版，放在
`docs/zh-CN/`，以及 `README.zh-CN.md`。

## 链接

| 项目 | 地址 |
| --- | --- |
| 官网（游戏） | <http://www.windowsgames.co.uk/ff.html> |
| 仓库 | <https://github.com/BiancaoAwa/Firefight-Mod-Manager-Android> |
| 作者 | <https://github.com/BiancaoAwa> |
| 哔哩哔哩 | <https://space.bilibili.com/1499437543> |
| 爱发电 | <https://afdian.com/a/bc_awa> |
| 邮箱 | BC_awa@163.com |

## 许可

GPL-3.0，见 [LICENSE](LICENSE)。

管理器代码由本项目作者编写；交战（Firefight）游戏本体、官方打包器与官方框架的版权归
Windows Games Ltd. 所有。发布打过管理器的整包前请自行确认你有权分发其中的游戏内容。
