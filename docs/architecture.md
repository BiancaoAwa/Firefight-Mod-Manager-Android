# Code structure

**English** | [中文](../zh-CN/architecture.md)

The manager is pure Java (no third-party dependencies, compiled with `--release 8`) built
into `classes4.dex` and appended to the game APK. All classes live in
`core/src/com/windowsgames/firefight/`:

| Class | Size | Responsibility |
| --- | --- | --- |
| `MyGame` | ~170 lines | **The new launcher Activity**, `extends SDLActivity`. `onCreate` calls `super` first (the game initialises as usual) and then shows the prompt: "Yes" opens `ModManagerActivity`, "No" or the end of the `PROMPT_SECONDS = 15` countdown continues into the game. It also provides `rootOfStatic(Context)` -- the single "sandbox root" entry point of the whole project |
| `ModManagerActivity` | ~860 lines | The main manager screen (three tabs + title bar + stats line) and the click dispatch centre for every button |
| `ModImportActivity` | ~120 lines | A transparent/dialog-style Activity that receives `VIEW`/`SEND` of `application/zip`, hands the mod package to `ModImporter` and finishes |
| `ModImporter` | ~1000 lines | All file logic for mods: `Mod/` holds the raw files of the one active mod (no `<id>/` level), inactive mods live in `Mod-disabled/<id>/` and the active id is recorded in `Mod-disabled/.ffm-enabled`; enabling a mod moves the previous one back into the library (one at a time). Also import (built-in assets / external zip / single-top-directory stripping), backups and metadata |
| `MapImporter` | ~740 lines | Custom maps: unpack into `Custom Maps/<CODE>/`, register `<CODE>` in `CustomMapCodeNames.txt` (listed = enabled, deleting the line = disabled), list maps in the order of that file with `moveCode()` for up/down, read `<name>` from `-scenarios`, page the list |
| `Ui` | ~205 lines | Widget factory and sizing helpers: `column/row/title/subtitle/action/headerAction/tabButton/divider/weight/fill/pad/dp/...`, all colours have fallbacks (a missing theme colour cannot crash it) |
| `L10n` | ~230 lines | Language resolution: maps the system locale to one of 28 languages, `t()` reads from the generated `ffm_strings.xml` and falls back to English/the key name |
| `LogStore` | ~230 lines | Diagnostics: appends events to `<Download>/Firefight-Mod-Manager/<package>/`, keeping the newest `MAX_FILES = 10`; "About -> Copy diagnostics" copies its contents |
| `Perms` | ~55 lines | Permission helpers: check/request `MANAGE_EXTERNAL_STORAGE` |

`core/stub/org/libsdl/app/SDLActivity.java` is a **compile-time stub only**: on a real
device the class comes from the game's own dex; the stub exists so `MyGame` compiles, and it
is never packaged into the manager dex.

---

## 1. How the UI is built

The content view returned by `ModManagerActivity.buildRoot()` **is itself a full-screen
`ScrollView`** (`setFillViewport(true)`) wrapping a vertical `LinearLayout`:

```
ScrollView (the window gives it EXACTLY full-screen height -> it measures the child with UNSPECIFIED)
└── LinearLayout (padding 12,10,12,16)
    ├── title Row: title (weight=1) + [Language] (headerAction) + [Back] (headerAction)
    ├── stats subtitle: installed mod count / total size
    ├── 10dp spacer
    ├── tab Row: [Mods][Maps][About] (each weight=1, ids 1/2/3)
    ├── divider
    └── content (vertical LinearLayout, cleared and rebuilt when a tab is tapped)
```

**Why the content view is a ScrollView and not a plain LinearLayout**: in earlier versions
`content` was measured as 0 high on the first frame (children got the full row width but
zero height) and the whole area stayed blank; once "the window gives the ScrollView EXACTLY
full-screen height" the inner layout is measured with an UNSPECIFIED height and can only
measure at its natural height, so the container can no longer collapse to 0. The full record
of this trap is in [history.md](history.md).

`showTab(tab)` rebuilds `content`: clear -> call `showMods()` / `showMaps()` / `showAbout()`
for the tab -> reset the scroll position. Every list item is a plain `LinearLayout` row as
well; no `RecyclerView` is used (zero dependencies).

`settleLayout()` is the safety net: 400 ms later, if `content.getHeight() <= 0`, it calls
`showTab(tab)` again and logs the anomaly.

## 2. A few fixed specifications in the UI

| Constant | Value | Meaning |
| --- | --- | --- |
| `MAX_SLOTS` | 5 | The Mods page shows at most 5 slots, the rest are hidden |
| `PAGE_SIZE` | 9 | The Maps page holds 9 maps per page and pages automatically |
| `PROMPT_SECONDS` | 15 | Countdown of the launch prompt; on timeout the game starts |
| `TAB_MODS / TAB_MAPS / TAB_ABOUT` | 0 / 1 / 2 | The three tabs |
| `REQ_PICK_MOD / REQ_PICK_MAP` | 0x61 / 0x62 | File picker request codes |

Each row on the Mods page: name (the `<name>` of `mod.txt`, `NA` if unreadable) + size; slot 0
shows "currently enabled"; the buttons are enable/disable + delete (deleting asks for
confirmation). The last item of the list is "Import mod", preceded by a line saying that only
one mod can be active at a time.

Each row on the Maps page: code + name + state (enabled / disabled / folder missing) + size,
with enable/disable + up + down buttons; the last item is "Import map", with previous/next page
buttons at the bottom and a line saying that this order is the order in the game.

After a mod or map change the manager offers a "restart required" dialog whose "Restart now"
button brings `MyGame` back up.

## 3. Localisation

* String keys all use the `ffm_` prefix; manager UI keys use `ffm_mm_*`.
* The four Python data tables `core/tools/l10n_data.py` / `l10n_extra.py` /
  `l10n_round3.py` / `l10n_round4.py` are the **only** source of strings; `core/tools/gen_l10n.py`
  merges them at build time and writes `core/work/v35/res/values*/ffm_strings.xml` (28 languages plus
  the default English).
* `gen_l10n.py` scans every `t("key")` in `core/src/**/*.java` and checks that each key is
  defined and that no language has extra keys; a missing key fails the build.
* The UI language is decided by `L10n` and decoupled from the system language: switching
  inside the manager affects the manager only, not the game (the prompt's "switch language"
  hint says the same).

## 4. Logging and diagnostics

`LogStore.event(context, "...")` appends one line under
`<Download>/Firefight-Mod-Manager/<package>/` (keeping the newest 10 files). The manager can
show and copy them directly:

* "Open mod directory" at the bottom of the Mods page and "Open map directory" at the bottom
  of the Maps page help you find the files;
* "About -> Manager info" shows the actual paths, language and version, and offers "Copy
  diagnostics" and "Permissions" buttons.

When something goes wrong, look here first, then at `adb logcat` (log lines written by the
manager go to logcat as well).
