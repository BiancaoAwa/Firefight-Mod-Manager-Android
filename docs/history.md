# Development log

**English** | [中文](../zh-CN/history.md)

This is a record of "why the code looks the way it does". It reads fine backwards too: every
section notes the **problem of the moment -> how it was located -> how it was fixed**.

---

## Rounds 1-3: pure Java shell + three-tab manager

**Goal**: write a manager UI in pure Java without touching the game's own smali, and append
it to the APK as an extra dex.

* `MyGame` extends `SDLActivity`; `onCreate` calls `super` first so the game starts normally,
  then shows the prompt (`PROMPT_SECONDS = 15`): "Yes" opens `ModManagerActivity`, "No"/timeout
  continues into the game.
* Three manager tabs: Mods (fixed `MAX_SLOTS = 5` slots), Maps (`PAGE_SIZE = 9` maps per page,
  automatic paging) and About.
* A mod name comes from `<name>` in `mod.txt`, else `NA`; a map name comes from `<name>` in
  `<CODE>-scenarios.txt`, else the 4-character code.
* Localisation: the string tables live in Python (`l10n_data.py` / `l10n_extra.py` /
  `l10n_round3.py`, joined by `l10n_round4.py` in round 9) and `gen_l10n.py` generates `res/values*/ffm_strings.xml` at build time;
  on the Java side you only write `t("ffm_...")`. The benefit is centralised, checkable
  strings: `gen_l10n.py` scans the Java sources for keys and fails the build on a missing
  definition.
* The map-side facts come from the string table in the game's `libmain.so`: the map directory
  is `Custom Maps/<4-char code>/`, and the code must be registered in
  `CustomMapCodeNames.txt`, otherwise the game ignores the map.

---

## Rounds 4-6: the blank content area, finally located in "first-frame measurement"

**Symptom**: the manager screen appeared, with title, stats line and all three tabs correct,
but everything below the tabs was blank.

**The wrong turn**: at one point the diagnosis was that `LinearLayout` `weight` computed a
child height of 0 under `AT_MOST` measurement, so a custom `ScrollView` subclass was added to
switch `AT_MOST` to `EXACTLY` as a fallback -- it did not solve anything.

**How it was located**: a "diagnostic build" was made, giving the content container a
semi-transparent red background and writing the measured values into the stats line after
900 ms:

```
[r=1440 n=6 ch=0 cw=2506 lh=0 lw=2506 vis=0]
```

* `n=6` -- the children really were all created (6 of them: the empty-list text + spacer +
  spacer + import button + spacer + open-directory button);
* `cw=2506` / `lw=2506` -- the children did receive the full row width, so layout really ran;
* **`ch=0` / `lh=0`** -- the content container was measured with **0 available height** on
  that frame, so every child height was 0.

Combined with "tapping any tab makes the content appear" (tapping rebuilds the children and
re-runs measurement), the root cause was clearly **first-frame measurement timing**, unrelated
to colours, visibility or surface occlusion.

**The fix** (version 6): make the content view itself a full-screen `ScrollView`:

```java
ScrollView sv = new ScrollView(this);
sv.setFillViewport(true);
LinearLayout root = Ui.column(this);
sv.addView(root, new FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
```

The window gives the `ScrollView` EXACTLY full-screen height -> the `ScrollView` measures its
child with UNSPECIFIED -> the inner layout and its children only measure at their natural
height, so the container can no longer collapse to 0. The `FillScroll` fallback class was
deleted at the same time, leaving only `settleLayout()` (if it is still 0 high after 400 ms,
rebuild the current page and log it).

**A trap fixed along the way**: the divider had never been drawn, because `Ui.fill()`
overwrote the 1dp `LayoutParams` that `Ui.divider()` sets, turning the height into
`WRAP_CONTENT` (that is, 0); it now passes
`new LinearLayout.LayoutParams(MATCH_PARENT, Math.max(1, Ui.dp(this, 1)))` explicitly.

**Same round**: the language switch button moved to the left of "Back" (adding
`Ui.headerAction()`, with `minWidth/minimumWidth/minimumHeight` zeroed so it cannot squeeze
the title away), all debug red backgrounds and `probe()`/`debugLine()` were deleted, and the
UI wording was made more objective (unified placeholders and texts such as "imported as
disabled; it takes effect after enabling").

---

## Round 7: landing the About-page links

Six rows (website, repository, author GitHub, Bilibili, Afdian, e-mail) were filled into
"About", each opening through `Intent.ACTION_VIEW` / `mailto:`; `ID_BILI = 31`. The
`mm_about_bili` key was added to the string tables.

---

## Round 8: reorganising into core/ + docs/ and turning it into a repository

**Goal**: tidy a project that had been piled into one big directory into "only `core/` and
`docs/`" with portable scripts, clean up dead code, and then create a local git repository
(without pushing yet).

### Dead code cleanup

A script first counted members that appear only once (a declaration with no caller), each was
checked by hand, and 183 lines were removed:

| File | Removed members |
| --- | --- |
| `ModImporter.java` | `listBuiltin` / `uninstall` / `rollback` / `installBuiltin` / `newestBackup` / `copyAssetDir` (-146 lines) |
| `MapImporter.java` | `syncCodes` (-21 lines) |
| `LogStore.java` | `dump` (-3 lines) |
| `Ui.java` | `margins` / `wrap` / `accentText` (-13 lines) |

Plus one unused import. `MyGame.getContext()` is **kept**: it is the outward hook that
SDL/billing code calls statically by signature. After the cleanup the rebuild went from 34 to
33 class files (the missing one was the anonymous `Comparator` inner class inside
`newestBackup`), `classes.dex` dropped from 68732 to 65912 bytes, and every gate passed.

### Making the scripts portable

The original `build.ps1` hard-coded absolute paths for JDK, SDK, apktool, keystore and python,
so it broke on any other machine. It is now split into `common.ps1` (toolchain discovery +
framework/keystore preparation) + `prepare_work.ps1` (decode and patch) + `build.ps1` (the
8-step build) + `verify_apk.ps1` (G1-G9).

**The apktool framework file does not need to be committed**: the apktool jar already carries
`prebuilt/android-framework.jar` (4,488,013 bytes, SHA-256 `5DD984...F3D7`), byte-identical to
the project's `framework/1.apk`, and `Ensure-Framework` extracts it during the build. That also
removes the "apktool wants to write `%LOCALAPPDATA%\apktool\framework` but has no permission"
error.

### End-to-end reproduction check

After the reorganisation, everything was run **from scratch with the new scripts** (which is
the whole point of having them):

```
prepare_work.ps1 -GameApk "Sean's 13.2.0_Mod_v35(1).apk"   -> decode + patch + self-check passed
build.ps1                                                   -> class 33 / classes.dex 65908 B
                                                              unsigned 259.1 MB / signed 260 MB
                                                              classes4.dex stored, stored-so-violations: 0
verify_apk.ps1                                              -> RESULT: PASS (G2..G9 all 0)
artifact: out/Firefight-Mod-Manager-13.2.0-mgr0.1.0.apk  272,628,824 B
```

The reproduced package has the **same size** as the hand-built one (the SHA-256 differs because
of apktool rebuild differences such as zip timestamps, which is normal).

Three problems fixed:

* `patch_worktree.py` looked for the old `MyGame.smali` by testing whether the *directory name*
  starts with `smali`, while the real path is
  `smali/com/windowsgames/firefight/MyGame.smali` (the directory is `firefight`), so it always
  reported "not present, skipping". It now checks whether the **first segment of the path
  relative to the worktree** starts with `smali`, and prints the file it removes.
* The self-check in `prepare_work.ps1` used the same wrong path test; it now searches
  recursively.
* `Ensure-Keystore` / `Ensure-Framework` in `common.ps1` wrote their messages with
  `Write-Output`, so the function "returned" an array of message + path; when the caller built
  the apksigner command line, the message string was taken as the input APK and apksigner
  reported `Unexpected parameter(s) after input APK (--ks-pass)`. The functions now use
  `Write-Host`, and the caller takes the last element as a safety net.

---

## Round 9: flat Mod/ data, map order and enable/disable, comment trimming

**Requests**: (1) `Mod/` must not contain a sub-directory, it must hold raw data; (2) the map
list must follow the line order of `CustomMapCodeNames.txt` and that order must be editable in
the manager; (3) maps need enable/disable too; (4) after a mod or map change, prompt for a
restart; (5) code comments should keep only "what this does" and "what is not implemented".

**How it was done**:

* `ModImporter`: `Mod/` now holds only the data files of the one active mod (flat, no `<id>/`);
  inactive mods stay in the library directory `Mod-disabled/<id>/`, and the active id is
  recorded in `Mod-disabled/.ffm-enabled` -- deliberately inside the library so `Mod/` stays
  pure data. Enabling a mod first moves the currently active data set back into its own library
  directory, so **only one mod is active at a time**. Moving uses `moveData()`, which renames
  file by file and falls back to "copy + delete source".
* `MapImporter`: `listMaps()` no longer sorts by directory name but follows the **line order of
  the code table** (codes in the table without a folder are listed too, marked "folder
  missing"); a new `moveCode()` swaps two adjacent code lines and writes the file back.
  Enable/disable is `registerCode()` / `unregisterCode()` -- the **map directory is left
  alone**, only the line is added or removed.
* `ModManagerActivity`: each map row gained enable/disable, up and down buttons (located by
  page-local index); a new `promptRestart()` dialog can bring `MyGame` back up directly and is
  triggered by mod enable/disable/delete and map enable/disable/delete/move/import; both pages
  gained one line of explanatory text.
* Strings: new `core/tools/l10n_round4.py` (7 keys in 11 languages), merged by `gen_l10n.py`.

**Comment trimming**: `strip_comments.py` removes the comments (a hand-written scanner for Java
that skips string and character literals, `tokenize` for Python so only `COMMENT` tokens go, a
quote-aware line pass for PowerShell), then every file got a one-to-three-line English header
saying what it does and what is missing. After touching the translation data files
(`l10n_*.py`), check with `git diff --unified=0` that "non-comment added lines = 0", otherwise
the translations themselves have been damaged.

**Result**: 34 class files, `classes.dex` 69144 bytes, all 81 keys referenced by Java have
translations, `values-zh-rTW 192/193` (the one missing key is the retired `about_github`, no
longer referenced by Java), `stored-so-violations: 0`; build and `verify_apk.ps1` both pass.

---

## Round 10: the AOSP testkey as the default signature

**Problem**: builds were signed with a locally generated debug certificate, while the game
and the official packager use the public AOSP testkey, so test packages could not be matched
against anything reproducible.

**Fix**: `core/keystore/testkey.jks` (alias and password both `testkey`, the same key the
official Firefight mod packager ships in `keys/testkey.jks`) is now committed and used
automatically when `-Keystore` is not passed; the generated debug keystore stays as the
fallback. `build.ps1` gained `-KsPass`, `-KeyAlias` and `-KeyPass` and prints the chosen key,
and `.gitignore` ignores `core/keystore/*` except the testkey.

**Result**: `build.ps1 -OutName ...-testkey.apk` -> `BUILD OK`; `apksigner verify --print-certs`
reports certificate SHA-256 `a40da80a59d170caa950cf15c18c454d47a39b26989d8b640ecd745ba71bf5dc`;
`verify_apk.ps1` -> `RESULT: PASS`.

**Convention set in this round**: everything published on GitHub (repository description,
commit messages, PR titles and bodies, issues) is written in English; the documents keep a
Chinese twin under `docs/zh-CN/`.

---

## Round 11: uncapped lists, search, numbering, thumbnails, version line

**Requests**: (1) reading `mod.txt` during a mod import must be more tolerant of directory
layouts; (2) the map rows need the `map.jpg` thumbnail; (3) mods and maps must no longer be
limited in number -- one huge scrolling list instead; (4) a search filter; (5) show the order
number of every mod and map in the list, editable; (6) show the in-game load number of a map
(at most 27, without affecting the sorting); (7) up/down buttons for both numbers; (8) a "jump
to line xx" function; (9) manager version 0.1.1.

**How it was done**:

* `ModImporter`: the display order moved into `Mod-disabled/.ffm-order` (one id per line) and
  became the single source of the list order -- the active mod takes part in it by its id
  instead of being pinned to the top. `installZip()` now scans the unpacked tree (depth 4) for
  directories that hold a `mod.txt` directly and imports **each one as a mod**; an archive
  without any `mod.txt` is still one mod, and `__MACOSX` / `.DS_Store` / `Thumbs.db` /
  `desktop.ini` are dropped while unpacking. Duplicate ids inside one archive get a `_2` /
  `_3` suffix.
* `MapImporter`: the display order moved into `Custom Maps/.ffm-map-order`; the code table is
  rewritten from that order with non-code lines kept in their relative positions. The load
  order stays the line order of the listed codes, so a map now has two numbers (display order
  and load number, the latter empty while the map is not listed). `MAX_LOAD = 27` is enforced
  on registration: enabling one more map is refused instead of growing the file. `mapImage()`
  finds the row thumbnail (`<CODE>-map.jpg` with fallbacks).
* `ModManagerActivity`: paging is gone (`MAX_SLOTS` / `PAGE_SIZE` / the page field / the
  id-range button dispatch), every row builds its own `OnClickListener` closure, and a search
  row (permanently visible `EditText` + "Jump" button) sits on top of both lists. New helper
  `askNumber()` backs the "set the number" dialogs; `jumpTo()` clears the filter, highlights
  the target row for two seconds and scrolls to it.
* `Ui`: new factories `searchBox()`, `mini()`, `thumb()` (down-scaled decode) and
  `cardBgMarked()` for the jump highlight.
* Layout: the title row gained a version line under it, and the root `ScrollView` applies the
  window insets as padding so the title stays clear of the status bar and the display cutout.
* Localisation: `l10n_round5.py` adds no English key and fills in the languages that previously
  covered only part of the keys, so all 28 languages now cover all 201 keys. `mm_map_up` /
  `mm_map_down` are gone (the map rows use the shared up/down labels).
* Version: the manager reports 0.1.1; every other version number (package `versionCode 13200`,
  game version 13.2.0) is unchanged.

**Result**: 61 class files, `classes4.dex` 91540 bytes, `gen_l10n.py` reports 83 keys referenced
by Java, all covered, and `201/201` for each of the 28 languages; `build.ps1` -> `BUILD OK`,
`verify_apk.ps1` -> `RESULT: PASS` (G2..G9 all 0). 32 offline JVM checks over the pure helpers
(`mergeOrder`, `listedSlots`, `moveIn`, `swapIn`, `firstCode`, `applyOrder`, `moveList`,
`findModRoots`) pass without a device.

**Traps hit in this round**:

* Running a PowerShell script through `[scriptblock]::Create((Get-Content -Raw ...))` leaves
  `$PSScriptRoot` empty, so `common.ps1` is never loaded; the script keeps going with null paths
  and still prints a success banner. Run scripts as files, never as script blocks.
* On a machine with only Windows PowerShell 5.1 (no `pwsh`), `& <script>.ps1` can fail with
  `AuthorizationManager check failed`; `powershell -NoProfile -ExecutionPolicy Bypass -File
  <script>` is the reliable form.

---

## Traps hit (quick reference)

| Trap | Lesson |
| --- | --- |
| `weight` collapsing to 0 under `AT_MOST` | The hypothesis was wrong; disprove with measurements (red background + size probes) before changing code |
| The divider never drawn | `Ui.fill()` overwrites a widget's own `LayoutParams` with `WRAP_CONTENT`; such "sizing helpers" should either take explicit arguments or not be used |
| `javac: incompatible types: int cannot be converted to String` | When a return type changes, go back and search the call sites |
| `aapt2: unescaped apostrophe in string` | Escape `'`, `"`, newlines and leading `@`/`?` when generating XML strings (`android_escape()` in `gen_l10n.py`) |
| apktool reports `Could not create framework directory` | Always pass `-p core/framework`; never let it write into the user directory |
| A PowerShell script fails immediately with `PSSecurityException` | `Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass -Force` |
| Build script using `$ErrorActionPreference = "Stop"` | Warnings that native commands (javac/d8) write to stderr are treated as terminating errors; use `Continue` + check `$LASTEXITCODE` |
| apktool rebuilding a several-hundred-MB package | Being slow is normal (3-8 minutes); log to a file, do not rely on terminal output |
| Only wanting to edit a file in the worktree but being told "file no longer exists, re-read" | The filesystem layer has a version guard for delete-then-write; when recreating a same-named file, work around it (write under a new name, then rename) |
| Removing or translating comments with a naive text replacement | It also rewrites Python docstrings and Chinese text inside string literals (five tool files broke outright); use a parser (`tokenize`) or an explicit "line number -> full new line" map, then run `py_compile` |
| Running a build script as a script block (`[scriptblock]::Create((Get-Content -Raw ...))`) | `$PSScriptRoot` is empty inside a script block, so the shared library is never loaded, the variables stay null and the script still prints a success banner; run scripts as files |
| `& <script>.ps1` failing with `AuthorizationManager check failed` under PowerShell 5.1 | Use `powershell -NoProfile -ExecutionPolicy Bypass -File <script>` |
