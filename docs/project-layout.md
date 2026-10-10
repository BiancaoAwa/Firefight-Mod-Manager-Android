# Directory conventions

**English** | [中文](../zh-CN/project-layout.md)

## 1. Two top-level directories only

```
Firefight-Mod-Manager-Android/
├── core/        everything "code and data": sources, stub, packaged assets, scripts, build workspace
├── docs/        all documentation
├── README.md    entry point
├── LICENSE      GPL-3.0
└── .gitignore
```

There are no scattered `build/`, `out/`, `_docs/` or `_dl/` directories: everything the
build produces lives in its own place under `core/`, everything written for humans lives
under `docs/`. The only documentation entry point is `README.md`, which links into `docs/`.

## 2. What each directory under core/ is for

| Directory | Contents | Committed? |
| --- | --- | --- |
| `core/src/` | Manager Java sources (`com/windowsgames/firefight/*.java`) | ✅ |
| `core/stub/` | Compile-time stub (`org/libsdl/app/SDLActivity.java`), never packaged | ✅ |
| `core/assets/` | Assets shipped with the package (sample mods under `Mod/`) | ✅ |
| `core/tools/` | All scripts (PowerShell + Python) | ✅ |
| `core/work/` | Decoded game worktree (`work/v35/`) | ❌ generated, hundreds of MB |
| `core/build/` | javac/d8/apktool intermediates | ❌ |
| `core/framework/` | apktool framework file `1.apk` (can be extracted from the apktool jar) | ❌ |
| `core/backup/` | Backups of replaced files such as the original `MyGame.smali` | ❌ (kept for reference, but it is local state) |
| `core/keystore/testkey.jks` | The public AOSP testkey (alias/password `testkey`) used for test builds | ✅ |
| `core/keystore/debug.keystore` | Auto-generated debug certificate | ❌ never commit |
| `core/logs/` | Build/decode logs | ❌ |
| `out/` | Built APK artifacts | ❌ |

`.gitignore` already excludes everything marked ❌ above. **The repository contains only
sources, scripts and documentation, less than 1 MB.**

## 3. Why the worktree is not committed

`core/work/v35/` is decoded from the user's own Firefight APK (about 340 MB, including the
game's own `res`, `lib/*.so` and `assets`): it is large and this project must not
redistribute it. It is generated locally by `core/tools/prepare_work.ps1`:

```powershell
pwsh -File core/tools/prepare_work.ps1 -GameApk <your Firefight APK>
```

After decoding, the changes the manager needs (two activities in the manifest, the sample
mods, removal of the old smali) are applied idempotently by `patch_worktree.py`.

## 4. Where to put new things

| What you are adding | Where |
| --- | --- |
| A new manager screen / logic | `core/src/com/windowsgames/firefight/`, wire events in `ModManagerActivity` if needed |
| A new UI string | `core/tools/l10n_round4.py` (the newest table), key name `ffm_mm_*` |
| A new build/verification step | `core/tools/`, PowerShell scripts take paths and toolchain from `common.ps1` |
| A document | `docs/` (and `docs/zh-CN/`), plus a row in the README documentation table |
| Sample data shipped in the package | `core/assets/` (synced into the worktree by `patch_worktree.py`) |

## 5. Coding conventions

* Java: `--release 8`, no third-party dependencies; the whole UI is built in code (there
  are no XML layout resources), every string goes through `L10n.t(...)`.
* Scripts: PowerShell 7 (`pwsh`), paths come from `common.ps1`, no hard-coded absolute
  paths; when the toolchain is missing, say exactly what is missing.
* String data: Python dictionaries, a list of `("key", "value")` tuples plus one dict per
  language; missing keys fall back to English.
