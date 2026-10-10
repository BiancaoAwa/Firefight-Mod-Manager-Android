# 模组包与自定义地图的格式

管理器本身只是「搬运 + 登记」，真正决定格式的是游戏。下面这些事实来自管理器的实现
（`core/src/com/windowsgames/firefight/ModImporter.java`、`MapImporter.java`）以及游戏
`libmain.so` 里的字符串表。

## 0. 根目录在哪

游戏与管理器都从 Android 的「应用专属外部目录」取根：

```
/storage/emulated/0/Android/data/com.windowsgames.firefight.mod/files/     ← <base>
├── Mod/                   模组（游戏只读这里）
├── Mod-disabled/          被禁用 / 刚导入还没启用的模组
├── Custom Maps/           自定义地图
└── ...                    游戏自己的存档等
```

`<base>` 由 `MyGame.rootOfStatic(Context)` 解析（`getExternalFilesDir(null)`）。
包名带 `.mod` 是因为工作树里的游戏清单就是这么写的；换了别的包，路径里的包名跟着变。
管理器的「关于管理器 → 管理器信息」里会显示当前实际路径，排查时以那里为准。

---

## 1. 模组

### 1.1 游戏认的形态

游戏认定 **`<base>/Mod/<id>/` 是一个模组目录**，识别标志是这个目录里的 `mod.txt`：

* `mod.txt` 是 HTML 风格的标签文本，管理器从中读 `<name>` 作为显示名；
* 社区里也有用 `mod.json` 的包，管理器同样识别（示例模组就是这种）；
* 名字取值顺序：`mod.txt` 的 `<name>` → `mod.json` 的 `name` → 空（界面上显示 `NA`）。

`<id>` 就是目录名，也是管理器里模组的标识。目录里可以放任意子目录和文件，管理器
不解析内容，只做搬运。

### 1.2 管理器加的东西

在 `<base>/Mod/<id>/` 内，管理器会写入：

| 路径 | 内容 |
| --- | --- |
| `.ffm-meta` | 安装元信息（导入时间、来源、文件数） |
| `<base>/Mod/.backup/<id>/<时间戳>/` | 覆盖安装前的自动备份 |
| `<base>/Mod/installed.json` | 供人或游戏查看的索引快照（派生文件，管理器不依赖它解析） |

### 1.3 启用 / 禁用

游戏只读 `Mod/`，所以「禁用」不是改标志位，而是把整个目录搬到同级的 `Mod-disabled/`；
「启用」再搬回来。管理器界面上「当前启用」指的就是「现在在 `Mod/` 里」。

### 1.4 导入的三种来源

| 来源 | 说明 |
| --- | --- |
| `assets/Mod/*.zip` | 随 APK 内置的模组包，zip 里含 `mod.json` 与内容文件 |
| `assets/Mod/<id>/mod.json` | 随 APK 内置的目录形态模组 |
| 外部 `.zip` / `.ffmod` | 文件选择器选的，或从别的应用「分享 / 打开方式」送进来的（由 `ModImportActivity` 接收） |

zip 支持两种布局：**扁平包**（`mod.json` 在 zip 根）和**单顶层目录包**（如
`example-mod/mod.json`，管理器会把那层目录剥掉）。

导入一律先落到 `Mod-disabled/<id>/`，用户在界面点「启用」才进 `Mod/` —— 这样新导入的
模组不会在你没看过之前就影响游戏。

### 1.5 内置示例

仓库里带了两份示例（`core/assets/Mod/`），装完就能在设备上试导入流程：

* `example-mod.zip`（537 B）：zip 内是 `mod.json`（268 B）+ `README.txt`；
* `sample-dir/mod.json`（268 B）+ `sample-dir/README.txt`（35 B）：目录形态。

两份示例的内容一致：

```json
{
  "id": "example-mod",
  "name": "Example Mod",
  "name_zh": "示例模组",
  "version": "1.0.0",
  "gameVersion": "13.2.0",
  "type": "data",
  "description": "Built-in sample mod shipped in assets/Mod. ..."
}
```

不需要示例就在跑 `prepare_work.ps1` 之前删掉 `core/assets/Mod/`（脚本会跳过这一步）。

---

## 2. 自定义地图

### 2.1 游戏认的形态

* 地图目录 `<base>/Custom Maps/`，**每张地图一个以 4 字符代码命名的文件夹**：

  ```
  Custom Maps/<CODE>/<CODE>-world.dat
  Custom Maps/<CODE>/<CODE>-contours.png
  Custom Maps/<CODE>/<CODE>-tileNN.jpg
  Custom Maps/<CODE>/<CODE>-scenarios.dat      （也可能是 -scenarios.txt）
  ```

  代码必须是 4 个字符的字母或数字（`^[A-Za-z0-9]{4}$`）。

* **同目录下必须有 `CustomMapCodeNames.txt`，把这 4 字符代码登记进去**，否则游戏不认这张地图。
  管理器导入地图时会自动追加登记。

### 2.2 地图包

网上下载的地图包通常就是「以 4 字符代码命名的压缩包」。管理器的做法：解压到
`Custom Maps/<CODE>/`，再把 `<CODE>` 登记进 `CustomMapCodeNames.txt`。

地图在界面上的名字读 `<CODE>-scenarios.txt`（或 `.dat`）里 `<map>` 下的第一个 `<name>`；
读不到就显示那 4 位代码。文件大小 = 该地图目录内所有文件之和。
