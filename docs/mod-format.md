# Format of mod packages and custom maps

**English** | [中文](../zh-CN/mod-format.md)

The manager itself only "moves and registers" files; the game decides the formats. The
facts below come from the manager implementation
(`core/src/com/windowsgames/firefight/ModImporter.java`, `MapImporter.java`) and from the
string table inside the game's `libmain.so`.

## 0. Where the root is

Both the game and the manager take their root from Android's app-specific external
directory:

```
/storage/emulated/0/Android/data/com.windowsgames.firefight.mod/files/     <- <base>
├── Mod/                   data of the one active mod (raw files; the game reads only here)
├── Mod-disabled/          the mod library: inactive mods, state file, backups, index
├── Custom Maps/           custom maps
└── ...                    the game's own saves and so on
```

`<base>` is resolved by `MyGame.rootOfStatic(Context)` (`getExternalFilesDir(null)`).
The package name carries `.mod` because that is what the game manifest in the worktree
says; with a different package the package name inside the path changes too. The manager's
"About -> Manager info" shows the actual current path, use that when troubleshooting.

---

## 1. Mods

### 1.1 The shape the game accepts

The game considers **`<base>/Mod/` itself to be the mod directory**: the data files sit
directly in `Mod/` (`Mod/mod.txt`, `Mod/Data/...`), **without another `<id>/` level**.

* `mod.txt` is HTML-style tag text; the manager reads `<name>` from it as the display name;
* some community packages use `mod.json` instead, which the manager also recognises (the
  sample mod is of that kind);
* name lookup order: `mod.txt` `<name>` -> `mod.json` `name` -> empty (shown as `NA` in the UI).

`Mod/` may contain arbitrary subdirectories and files; the manager does not parse the
contents, it only moves them.

### 1.2 What the manager adds

Metadata and the index live in the library (`Mod-disabled/`), so `Mod/` stays pure data:

| Path | Contents |
| --- | --- |
| `<base>/Mod-disabled/<id>/.ffm-meta` | Install metadata (import time, source, file count) |
| `<base>/Mod-disabled/.ffm-enabled` | Id of the active mod (deliberately kept in the library so `Mod/` stays clean) |
| `<base>/Mod-disabled/.backup/<id>/<timestamp>/` | Automatic backup taken before an overwriting install |
| `<base>/Mod-disabled/installed.json` | Index snapshot for humans (a derived file; the manager does not rely on it) |

`<id>` is the directory name inside the library and is the mod's identity in the manager.

### 1.3 Enable / disable

The game only reads `Mod/`, so "enable" moves this mod's files from `Mod-disabled/<id>/` into
`Mod/`, and "disable" moves them back. **Only one mod can be active at a time**: enabling a mod
first moves the currently active data back into its own library directory. "Currently enabled"
in the UI simply means "its data is in `Mod/` right now".

### 1.4 The three import sources

| Source | Description |
| --- | --- |
| `assets/Mod/*.zip` | Mod packages bundled in the APK; the zip holds `mod.json` plus contents |
| `assets/Mod/<id>/mod.json` | Directory-form mods bundled in the APK |
| External `.zip` / `.ffmod` | Chosen in the file picker, or sent in from another app via "share / open with" (received by `ModImportActivity`) |

Two zip layouts are supported: a **flat package** (`mod.json` at the zip root) and a
**single-top-directory package** (such as `example-mod/mod.json`, where the manager strips
that directory level).

An import always lands in `Mod-disabled/<id>/` first; it only moves to `Mod/` when the user
taps "enable" in the UI, so a freshly imported mod cannot affect the game before you have
looked at it.

### 1.5 Built-in samples

The repository ships two samples (`core/assets/Mod/`) so the import flow can be tried on a
device right after installing:

* `example-mod.zip` (537 B): inside the zip are `mod.json` (268 B) + `README.txt`;
* `sample-dir/mod.json` (268 B) + `sample-dir/README.txt` (35 B): directory form.

Both samples have the same contents:

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

If you do not want the samples, delete `core/assets/Mod/` before running
`prepare_work.ps1` (the script then skips that step).

---

## 2. Custom maps

### 2.1 The shape the game accepts

* Map directory `<base>/Custom Maps/`, **one folder per map, named after its 4-character code**:

  ```
  Custom Maps/<CODE>/<CODE>-world.dat
  Custom Maps/<CODE>/<CODE>-contours.png
  Custom Maps/<CODE>/<CODE>-tileNN.jpg
  Custom Maps/<CODE>/<CODE>-scenarios.dat      (may also be -scenarios.txt)
  ```

  The code must be 4 letters or digits (`^[A-Za-z0-9]{4}$`).

* **`CustomMapCodeNames.txt` must exist in the same directory and register that 4-character
  code**, otherwise the game ignores the map. The manager appends the registration
  automatically when importing a map.

### 2.2 Enable / disable and order

The code table is the switch:

* the code is listed -> the map is enabled; deleting that line -> the map is disabled (the
  **map directory is left alone**, so it can be enabled again at any time);
* the **line order in that file is the map order in the game**. The manager lists maps in that
  order and offers "move up / move down", which swaps two lines and writes the file back
  (comment and blank lines keep their position).

### 2.3 Map packages

Map packages downloaded from the internet are usually "an archive named after the
4-character code". The manager unpacks it into `Custom Maps/<CODE>/` and then registers
`<CODE>` in `CustomMapCodeNames.txt`.

The UI name comes from the first `<name>` under `<map>` in `<CODE>-scenarios.txt` (or
`.dat`); if that cannot be read, the 4-character code is shown. The file size is the sum of
all files inside that map directory.

After a mod or map change the manager offers to restart the game, because the change only
takes effect on the next start.
