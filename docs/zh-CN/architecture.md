# 代码结构

[English](../architecture.md) | **中文**

管理器是纯 Java（无第三方依赖，`--release 8` 编译），编译成 `classes4.dex` 追加进游戏 APK。
所有类都在 `core/src/com/windowsgames/firefight/`：

| 类 | 行数级 | 职责 |
| --- | --- | --- |
| `MyGame` | ~170 | **新的启动 Activity**，`extends SDLActivity`。`onCreate` 里先调 `super`（游戏照常初始化），再弹提示框：选「是」拉起 `ModManagerActivity`，选「否」或 `PROMPT_SECONDS = 15` 秒倒计时结束就继续游戏。同时提供 `rootOfStatic(Context)` —— 全项目唯一的「沙盒根目录」入口 |
| `ModManagerActivity` | ~860 | 管理器主界面（三个页签 + 标题栏 + 统计行），也是所有按钮的点击分派中心 |
| `ModImportActivity` | ~120 | 透明/对话框式 Activity，接收 `VIEW`/`SEND` 的 `application/zip`，把模组包交给 `ModImporter` 导入后退出 |
| `ModImporter` | ~885 | 模组的全部文件逻辑：枚举槽位、读名字、启用/禁用（`Mod/` ↔ `Mod-disabled/` 搬运）、导入（内置 assets / 外部 zip / 单顶层目录剥离）、备份与元信息 |
| `MapImporter` | ~690 | 自定义地图的导入与登记：解压到 `Custom Maps/<CODE>/`、把 `<CODE>` 追加进 `CustomMapCodeNames.txt`、读 `-scenarios` 里的 `<name>`、按页切分 |
| `Ui` | ~205 | 控件工厂与尺寸工具：`column/row/title/subtitle/action/headerAction/tabButton/divider/weight/fill/pad/dp/...`，配色统一带 fallback（取不到主题色也不会崩） |
| `L10n` | ~230 | 语言解析：把系统 locale 映射到 28 个语种之一，`t()` 从生成的 `ffm_strings.xml` 取值，取不到回落英文/键名 |
| `LogStore` | ~230 | 诊断日志：写事件到 `<Download>/Firefight-Mod-Manager/<包名>/`，保留最近 `MAX_FILES = 10` 份；「关于管理器 → 复制诊断信息」就是复制它的内容 |
| `Perms` | ~55 | 权限辅助：判断/引导 `MANAGE_EXTERNAL_STORAGE` |

`core/stub/org/libsdl/app/SDLActivity.java` 只是**编译期桩**：真实设备上这个类由游戏自带的
dex 提供，桩是为了让 `MyGame` 能编译过，它不会被编进管理器 dex。

---

## 1. 界面是怎么搭的

`ModManagerActivity.buildRoot()` 返回的内容视图**本身就是一个整屏 `ScrollView`**
（`setFillViewport(true)`），里面套一层竖直 `LinearLayout`：

```
ScrollView（窗口给 EXACTLY 整屏高 → 以 UNSPECIFIED 量内层）
└── LinearLayout（12,10,12,16 内边距）
    ├── 标题栏 Row：标题（weight=1）+ [语言]（headerAction）+ [返回]（headerAction）
    ├── 统计行 subtitle：已安装模组数 / 合计大小
    ├── 间距 10dp
    ├── 页签 Row：[模组管理][地图管理][关于管理器]（各 weight=1，id 1/2/3）
    ├── 分隔线
    └── content（竖直 LinearLayout，点页签时清空重建）
```

**为什么内容视图是 ScrollView 而不是普通 LinearLayout**：早先版本里 `content` 会被
第一帧测量成 0 高（子项拿到整行宽但高度为 0），整片区域空白；改成「窗口直接给 ScrollView
EXACTLY 整屏高」之后，内层布局拿到的是 UNSPECIFIED 高度，只能按自然高度测量，容器不可能
再塌成 0。这个坑的完整记录见 [history.md](history.md)。

`showTab(tab)` 负责重建 `content`：清空 → 按页签调用 `showMods()` / `showMaps()` / `showAbout()`
→ 重置滚动位置。所有列表项也都是普通 `LinearLayout` 行，没有用 `RecyclerView`（依赖为零）。

`settleLayout()` 是兜底：400 ms 后如果 `content.getHeight() <= 0`，就重新 `showTab(tab)`
并把这次异常写进日志。

## 2. 界面上的几个固定规格

| 常量 | 值 | 含义 |
| --- | --- | --- |
| `MAX_SLOTS` | 5 | 模组页最多显示 5 个槽位，超出的隐藏 |
| `PAGE_SIZE` | 9 | 地图页每页 9 张，自动分页 |
| `PROMPT_SECONDS` | 15 | 启动提示框倒计时，超时直接进游戏 |
| `TAB_MODS / TAB_MAPS / TAB_ABOUT` | 0 / 1 / 2 | 三个页签 |
| `REQ_PICK_MOD / REQ_PICK_MAP` | 0x61 / 0x62 | 文件选择器的请求码 |

模组页每行：名字（`mod.txt` 的 `<name>`，取不到显示 `NA`）+ 大小，第 0 槽显示「当前启用」；
按钮是 启用/禁用 + 删除（删除要二次确认）。列表最后一项是「导入模组」。
地图页最后一项是「地图导入」，底部有 上一页/下一页。

## 3. 多语言

* 文案键统一前缀 `ffm_`，管理器界面用 `ffm_mm_*`。
* `core/tools/l10n_data.py` / `l10n_extra.py` / `l10n_round3.py` 三份 Python 数据表是**唯一**
  文案源；`core/tools/gen_l10n.py` 在构建时把它们合并，写出
  `core/work/v35/res/values*/ffm_strings.xml`（28 个语种 + 默认英文）。
* `gen_l10n.py` 会扫 `core/src/**/*.java` 里所有 `t("key")`，校验每个键都有定义、
  每个语种没有多余键；缺键直接让构建失败。
* 界面语言由 `L10n` 决定，和系统语言解耦：管理器里切语言只影响管理器，游戏本体不变
  （提示框里的「切换语言」说明文案也是这么写的）。

## 4. 日志与诊断

`LogStore.event(context, "...")` 追加一行到
`<Download>/Firefight-Mod-Manager/<包名>/`（保留最近 10 份）。管理器里能直接看/复制：

* 模组页底部「打开模组目录」、地图页底部「打开地图目录」便于找文件；
* 「关于管理器 → 管理器信息」显示实际路径、语言、版本，并提供「复制诊断信息」「权限」按钮。

出问题时先看这里，再看 `adb logcat`（管理器写的日志也会进 logcat）。
