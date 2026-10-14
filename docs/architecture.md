# Code structure

**English** | [中文](../zh-CN/architecture.md)

The manager is pure Java (no third-party dependencies, compiled with `--release 8`) built
into `classes4.dex` and appended to the game APK. All classes live in
`core/src/com/windowsgames/firefight/`:

| Class | Size | Responsibility |
| --- | --- | --- |
| `MyGame` | ~150 lines | **The new launcher Activity**, `extends SDLActivity`. `onCreate` calls `super` first (the game initialises as usual) and then shows the prompt: "Yes" opens `ModManagerActivity`, "No" or the end of the `PROMPT_SECONDS = 15` countdown continues into the game. It also provides `rootOfStatic(Context)` -- the single "sandbox root" entry point of the whole project |
| `ModManagerActivity` | ~1400 lines | The main manager screen (three tabs + title bar + version line + stats line) and the click dispatch centre for every button. Each list item carries its own `OnClickListener` closure instead of a page-local id range, so a list of any length works. The About tab opens six sub-pages (help, manager info, game site, repository, author, licence); leaving the manager runs the "restart needed" dialog when a change is still pending |
| `ModImportActivity` | ~120 lines | A transparent/dialog-style Activity that receives `VIEW`/`SEND` of `application/zip`, hands the mod package to `ModImporter` and finishes |
| `ModImporter` | ~1200 lines | All file logic for mods: `Mod/` holds the raw files of the one active mod (no `<id>/` level), inactive mods live in `Mod-disabled/<id>/` and the active id is recorded in `Mod-disabled/.ffm-enabled`; enabling a mod moves the previous one back into the library (one at a time). Also import (built-in assets / external zip / every directory that holds a `mod.txt` becomes one mod), backups, metadata and the display order file `Mod-disabled/.ffm-order` |
| `MapImporter` | ~1000 lines | Custom maps: unpack into `Custom Maps/<CODE>/`, register `<CODE>` in `CustomMapCodeNames.txt` (listed = enabled, deleting the line = disabled, at most `MAX_LOAD = 27` listed), read `<name>` from `-scenarios`, and keep two orders -- the display order in `Custom Maps/.ffm-map-order` and the in-game load order as the sequence of listed codes. Also `mapImage()`, which finds the `<CODE>-map.jpg` thumbnail of a row |
| `Ui` | ~270 lines | Widget factory and sizing helpers: `column/row/title/subtitle/action/headerAction/tabButton/divider/weight/fill/pad/dp/searchBox/mini/thumb/cardBgMarked/palette/...`, all colours have fallbacks (a missing theme colour cannot crash it). `palette()` holds the single light/dark colour decision of the manager |
| `L10n` | ~210 lines | Language resolution: maps the system locale to one of 28 languages, `t()` reads from the generated `ffm_strings.xml` and falls back to English/the key name |
| `LogStore` | ~220 lines | Diagnostics: appends events to `<Download>/Firefight-Mod-Manager/<package>/`, keeping the newest `MAX_FILES = 10`; "About -> Copy diagnostics" copies its contents |
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
└── LinearLayout (padding 12,10,12,16 plus the window insets)
    ├── title Row: title (weight=1) + [Language] (headerAction) + [Back] (headerAction)
    ├── version line: "0.1.3 (ffm-0.1.3)" in the small/faded style
    ├── stats subtitle: installed mod count / total size
    ├── 10dp spacer
    ├── tab Row: [Mods][Maps][About] (each weight=1, ids 1/2/3)
    ├── divider
    └── content (vertical LinearLayout, cleared and rebuilt when a tab is tapped)
```

The `ScrollView` carries an `OnApplyWindowInsetsListener` that copies the system window insets
into its own padding, so the title line stays clear of the status bar, the display cutout and
the navigation bar.

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
| `MAX_LOAD` (`MapImporter`) | 27 | At most 27 maps may be listed in `CustomMapCodeNames.txt`; enabling one more is refused |
| `PROMPT_SECONDS` | 15 | Countdown of the launch prompt; on timeout the game starts |
| `TAB_MODS / TAB_MAPS / TAB_ABOUT` | 0 / 1 / 2 | The three tabs |
| `REQ_PICK_MOD / REQ_PICK_MAP` | 0x61 / 0x62 | File picker request codes |
| `ID_MOD_JUMP / ID_MAP_JUMP` | 15 / 16 | The two "jump to line" buttons of the search row |

Both lists are **not paged and not capped**: every installed mod and every map directory is one
row in one scrolling list.

Each row on the Mods page: `#<order number>` + name (the `<name>` of `mod.txt`, `NA` if
unreadable) + state + size, and the buttons enable/disable, delete, set the number, move up and
move down. The number is the position in the mod display order (`Mod-disabled/.ffm-order`) and
can be typed in directly; moving a mod only changes the manager-side order.

Each row on the Maps page: a 64dp thumbnail (`<CODE>-map.jpg` and its fallbacks, an empty frame
when no image is found) on the left, then `#<order number>` + name + code + size + state, and
three button rows: enable/disable + delete, display number + up + down, load number + up + down
(the load row is disabled while the map is not listed). The display number is the position in
the manager order, the load number is the position among the listed maps and is what the game
follows.

The search row sits at the top of both lists: a permanently visible `EditText` that filters the
rows (name / code / number, case-insensitive) and a "Jump" button that asks for a line number,
clears the filter, scrolls to that row and highlights it for two seconds.

No change asks for a restart while it is made: the manager only marks the session as dirty
(`markDirty()`), and only the changes the game re-reads on its next launch mark it (enable,
disable, delete, import, and the map load order). The `[Exit]` button in the title bar then
behaves in two ways: with no pending change it closes the manager and the game keeps running;
with a pending change it offers "Restart the game now to apply the change" / "Back to the
manager", and exits the game only when that is chosen. Leaving the game ends the manager process
without restarting anything: the game re-reads its files on the next manual launch, and an
automatic relaunch is what left the sound engine dead (see [history.md](history.md)).

The manager paints itself. `Ui.palette(context)` picks a light or a dark set of colours from
`Configuration.UI_MODE_NIGHT_MASK`, and every widget asks it for its colours, so no colour is
taken from the game's theme and a missing theme colour cannot crash the manager.

The About tab is a list of six entries -- help, manager info, game site, repository, author,
licence -- and each one except the game site opens a sub-page inside the manager; the game site
and the repository/author/licence links open in the browser or a viewer. The licence text is
compiled in, so the page works offline.

## 3. Localisation

* String keys all use the `ffm_` prefix; manager UI keys use `ffm_mm_*`.
* The six Python data tables `core/tools/l10n_data.py` / `l10n_extra.py` /
  `l10n_round3.py` / `l10n_round4.py` / `l10n_round5.py` / `l10n_round6.py` are the **only**
  source of strings;
  `core/tools/gen_l10n.py` merges them at build time and writes
  `core/work/v35/res/values*/ffm_strings.xml` (28 languages plus the default English).
  Rounds 3 to 6 add keys on top of the first two tables, so a large data file does not have to be
  edited. `l10n_round5.py` adds no English key: it fills in the languages that previously covered
  only part of the keys. `l10n_round6.py` carries the keys of 0.1.2 (about pages, licence, exit
  dialog) and covers all 28 languages; the translations of the later rounds were produced
  automatically and have not been proof-read by native speakers.
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
