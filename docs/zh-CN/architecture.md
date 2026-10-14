# 代码结构

[English](../architecture.md) | **中文**

管理器是纯 Java（无第三方依赖，`--release 8` 编译），编译成 `classes4.dex` 追加进游戏 APK。
所有类都在 `core/src/com/windowsgames/firefight/`：

| 类 | 行数级 | 职责 |
| --- | --- | --- |
| `MyGame` | ~150 | **新的启动 Activity**，`extends SDLActivity`。`onCreate` 里先调 `super`（游戏照常初始化），再弹提示框：选「是」拉起 `ModManagerActivity`，选「否」或 `PROMPT_SECONDS = 15` 秒倒计时结束就继续游戏。同时提供 `rootOfStatic(Context)` —— 全项目唯一的「沙盒根目录」入口 |
| `ModManagerActivity` | ~1400 | 管理器主界面（三个页签 + 标题栏 + 版本行 + 统计行），也是所有按钮的点击分派中心。每个列表项自带 `OnClickListener` 闭包，不再用按页分配的 id 区间，因此列表长度不受限制。关于页打开六个子页（使用帮助、管理器信息、游戏官网、仓库、作者、许可证）；有未完成更改时从标题栏 `[退出]` 会走重启提示框 |
| `ModImportActivity` | ~120 | 透明/对话框式 Activity，接收 `VIEW`/`SEND` 的 `application/zip`，把模组包交给 `ModImporter` 导入后退出 |
| `ModImporter` | ~1200 | 模组的全部文件逻辑：`Mod/` 只放当前启用模组的裸文件（不套 `<id>/`），未启用的放在 `Mod-disabled/<id>/`，活动 id 记在 `Mod-disabled/.ffm-enabled`；启用新模组会自动把旧的搬回库（同一时间只有一个）；另有导入（内置 assets / 外部 zip / 每个含 `mod.txt` 的目录各算一个模组）、备份、元信息与显示顺序文件 `Mod-disabled/.ffm-order` |
| `MapImporter` | ~1000 | 自定义地图：解压到 `Custom Maps/<CODE>/`、把 `<CODE>` 登记进 `CustomMapCodeNames.txt`（有行=启用，删行=禁用，最多 `MAX_LOAD = 27` 行）、读 `-scenarios` 里的 `<name>`，并维护两个顺序——`Custom Maps/.ffm-map-order` 里的显示顺序，以及以码表行序表示的**游戏内加载顺序**；另有 `mapImage()`，找一行条目的 `<CODE>-map.jpg` 缩略图 |
| `Ui` | ~270 | 控件工厂与尺寸工具：`column/row/title/subtitle/action/headerAction/tabButton/divider/weight/fill/pad/dp/searchBox/mini/thumb/cardBgMarked/palette/...`，配色统一带 fallback（取不到主题色也不会崩）。`palette()` 是管理器浅色/深色配色的唯一决策点 |
| `L10n` | ~210 | 语言解析：把系统 locale 映射到 28 个语种之一，`t()` 从生成的 `ffm_strings.xml` 取值，取不到回落英文/键名 |
| `LogStore` | ~220 | 诊断日志：写事件到 `<Download>/Firefight-Mod-Manager/<包名>/`，保留最近 `MAX_FILES = 10` 份；「关于管理器 → 复制诊断信息」就是复制它的内容 |
| `Perms` | ~55 | 权限辅助：判断/引导 `MANAGE_EXTERNAL_STORAGE` |

`core/stub/org/libsdl/app/SDLActivity.java` 只是**编译期桩**：真实设备上这个类由游戏自带的
dex 提供，桩是为了让 `MyGame` 能编译过，它不会被编进管理器 dex。

---

## 1. 界面是怎么搭的

`ModManagerActivity.buildRoot()` 返回的内容视图**本身就是一个整屏 `ScrollView`**
（`setFillViewport(true)`），里面套一层竖直 `LinearLayout`：

```
ScrollView（窗口给 EXACTLY 整屏高 → 以 UNSPECIFIED 量内层）
└── LinearLayout（12,10,12,16 内边距，另加窗口 insets）
    ├── 标题栏 Row：标题（weight=1）+ [语言]（headerAction）+ [返回]（headerAction）
    ├── 版本行：「0.1.3 (ffm-0.1.3)」，small / faded 样式
    ├── 统计行 subtitle：已安装模组数 / 合计大小
    ├── 间距 10dp
    ├── 页签 Row：[模组管理][地图管理][关于管理器]（各 weight=1，id 1/2/3）
    ├── 分隔线
    └── content（竖直 LinearLayout，点页签时清空重建）
```

`ScrollView` 上挂了 `OnApplyWindowInsetsListener`，把系统窗口 insets 加进自身内边距，
因此标题行不会被状态栏、刘海与导航栏遮住。

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
| `MAX_LOAD`（`MapImporter`） | 27 | `CustomMapCodeNames.txt` 最多列出 27 张地图，再启用一张会被拒绝 |
| `PROMPT_SECONDS` | 15 | 启动提示框倒计时，超时直接进游戏 |
| `TAB_MODS / TAB_MAPS / TAB_ABOUT` | 0 / 1 / 2 | 三个页签 |
| `REQ_PICK_MOD / REQ_PICK_MAP` | 0x61 / 0x62 | 文件选择器的请求码 |
| `ID_MOD_JUMP / ID_MAP_JUMP` | 15 / 16 | 搜索行上两个「跳转」按钮 |

两个列表**都不分页、不设上限**：每个已安装模组、每个地图目录都是一行，同属一条滚动列表。

模组页每行：`#<排序编号>` + 名字（`mod.txt` 的 `<name>`，取不到显示 `NA`）+ 状态 + 大小，
按钮是 启用/禁用、删除、设置编号、上移、下移。编号是模组显示顺序（`Mod-disabled/.ffm-order`）
中的位置，可直接输入；移动模组只改变管理器侧的顺序。

地图页每行：左侧 64dp 缩略图（`<CODE>-map.jpg` 及其回退，找不到图片时是空框），右侧是
`#<排序编号>` + 名字 + 代码 + 大小 + 状态，下面三行按钮：启用/禁用 + 删除、排序编号 + 上移 +
下移、加载编号 + 上移 + 下移（未列入码表时加载编号那一行禁用）。排序编号是管理器顺序中的位置，
加载编号是已列入码表的地图之间的位置，游戏按后者加载。

搜索行在两个列表的顶部：常驻的 `EditText` 按名称 / 代码 / 编号过滤（忽略大小写），旁边的
「跳转」按钮询问行号，清空过滤、滚动到该行并高亮两秒。

改动时不再弹重启提示：管理器只把本次会话标记为「有未完成更改」（`markDirty()`），且只有游戏
下次启动会重新读取的改动才标记（启用、禁用、删除、导入，以及地图加载顺序）。从标题栏 `[退出]`
离开时分两种：没有未完成更改就直接关闭管理器，游戏继续运行；有未完成更改则给「立即重启游戏完成
更改」/「返回管理器」两项，选择退出游戏才结束游戏进程。退出游戏只结束管理器、不重启任何东西：
游戏在下次手动启动时会重新读取文件，而自动重启正是让音效引擎失效的做法（见 [history.md](history.md)）。

管理器自绘界面：`Ui.palette(context)` 按 `Configuration.UI_MODE_NIGHT_MASK` 给出一套浅色或深色
配色，所有控件都向它取色，因此不依赖游戏主题色，取不到主题色也不会崩。

关于页是六项列表（使用帮助、管理器信息、游戏官网、仓库、作者、许可证），除游戏官网外都在管理器
内打开子页；游戏官网与仓库/作者/许可证链接交给浏览器或查看器打开。许可证正文编译进 dex，
离线也能看。

## 3. 多语言

* 文案键统一前缀 `ffm_`，管理器界面用 `ffm_mm_*`。
* `core/tools/l10n_data.py` / `l10n_extra.py` / `l10n_round3.py` / `l10n_round4.py` /
  `l10n_round5.py` / `l10n_round6.py` 六份 Python 数据表是**唯一**文案源；`core/tools/gen_l10n.py`
  在构建时把它们合并，写出 `core/work/v35/res/values*/ffm_strings.xml`（28 个语种 + 默认英文）。
  第 3 到第 6 轮只在前两份表之上追加键，避免改动大文件；`l10n_round5.py` 不含英文键，只补齐此前
  只有部分译文的语种；`l10n_round6.py` 是 0.1.2 的文案（关于页、许可证、退出对话框），28 个语种
  全有译文，但后面几轮的译文是自动生成的，没有经过母语者校对。
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
