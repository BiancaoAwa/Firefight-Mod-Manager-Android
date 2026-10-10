# Deploying the mod manager into a Firefight APK

**English** | [中文](../zh-CN/deploy.md)

This document is for anyone who has this source tree and wants to put it into their own
Firefight (交战) APK. The whole flow is two PowerShell commands: first decode the game APK
into a worktree and patch it, then build a new APK.

---

## 1. What the manager is

The manager is a set of pure-Java Android UI code (`core/src/com/windowsgames/firefight/`)
appended to the Firefight APK as an **extra dex**, providing three Activities:

| Component | Role |
| --- | --- |
| `MyGame` | The new launcher Activity. On start it first shows the prompt "use the mod manager?"; "Yes" opens the manager, "No" or the 15-second timeout goes straight into the game |
| `ModManagerActivity` | The main manager screen: the Mods / Maps / About tabs, with a title bar that can switch the UI language and return to the game |
| `ModImportActivity` | Receives `.zip` files from a file manager or another app's "open with", and imports the mod package |

It does not change game logic; it borrows the game's package name and sandbox:

* **It must use the same package name and the same signature**, because the player installs
  "game + manager" as a single app: saves, the mod directory and permissions all belong to
  that app. The right approach is therefore to patch the manager into **your own modded
  Firefight APK**, not to build a new app from scratch.
* The manager needs to read and write the mod/map directories; it reuses the
  `MANAGE_EXTERNAL_STORAGE` permission the game already declares and requests nothing extra.

---

## 2. What you need

### 2.1 Input

* **Your own Firefight APK** (the one you already modded). How to tell: it must decode into
  `smali/com/windowsgames/firefight/` and an `assets/` directory, with package name
  `com.windowsgames.firefight` and version 13.2.0 (`versionCode 13200`). This project was
  developed against `Sean's 13.2.0_Mod_v35(1).apk` (272 MB).
* Note: do **not** use the unmodded original package as input, otherwise the mod content you
  worked on will not be inside.

### 2.2 Toolchain

| Tool | Version | Purpose | How the script finds it |
| --- | --- | --- | --- |
| JDK | 17+ (25 used in development) | `javac` to compile the manager, `keytool` for signing, running apktool | `-JdkHome` or `JAVA_HOME` |
| Android SDK · platforms | containing `android.jar` (android-36.1 used in development) | `-cp`/`--lib` at compile time | `-AndroidSdk`, `ANDROID_HOME`, `ANDROID_SDK_ROOT`, default `%LOCALAPPDATA%\Android\Sdk` |
| Android SDK · build-tools | containing `d8.bat`, `zipalign.exe`, `apksigner.bat`, `aapt2.exe` (36.1.0 used in development) | dex conversion, alignment, signing, verification | same, highest version wins |
| Python | 3.8+ | generating localisation resources, injecting the dex, verifying the APK | `-Python` or `python` on PATH |
| apktool | 3.x jar | decoding / rebuilding the game | `-ApktoolJar`, `APKTOOL_JAR`, `core\tools\apktool.jar`, or an automatic search in `Downloads` |

You do not need to prepare the framework file apktool needs: the script extracts the bundled
`prebuilt/android-framework.jar` from the apktool jar, stores it as `core/framework/1.apk`
and passes it to `apktool -p`. (This framework file matches the one in the official apktool
release, SHA-256
`5DD984016ED5A5EB0EEF866E2C6E8CD352E1427828EC23ADB18005CF5648F3D7`, 4,488,013 bytes.)

You do not need to prepare a signing keystore either. Without `-Keystore` the script signs with
the public AOSP testkey shipped in the repository (`core/keystore/testkey.jks`, alias and
password both `testkey`, the same key the official Firefight mod packager uses); if that file
is absent it falls back to `keytool` creating an `androiddebugkey` (password `android`) at
`core/keystore/debug.keystore`. Both are test signatures only — use your own certificate
(`-Keystore` / `-KsPass` / `-KeyAlias` / `-KeyPass`) to publish.

### 2.3 Disk and system

* **1.5 GB or more of free disk space**: the worktree is about 340 MB, the build
  intermediates (unsigned/aligned/signed) about 780 MB, and the output package about 260 MB.
* Windows with PowerShell (`pwsh` 7 or Windows PowerShell 5.1; the examples use `pwsh`,
  replace it with `powershell` on 5.1). The scripts are PowerShell; porting them to
  Linux/macOS is up to you.

---

## 3. Three steps

Open PowerShell in the repository root:

```powershell
# 0) allow local script execution (for this session only)
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass -Force

# 1) decode the game + patch (2-4 minutes)
pwsh -File core/tools/prepare_work.ps1 -GameApk "D:\APK\Sean's 13.2.0_Mod_v35(1).apk"

# 2) compile the manager and produce the package (5-10 minutes; rebuilding 230 MB+ with apktool is slow)
pwsh -File core/tools/build.ps1

# 3) static verification (optional but strongly recommended)
pwsh -File core/tools/verify_apk.ps1
```

Artifact: `out/Firefight-Mod-Manager-13.2.0-mgr0.1.0.apk` (about 260 MB).

Install it on a device:

```powershell
adb install -r out\Firefight-Mod-Manager-13.2.0-mgr0.1.0.apk
```

If the device already has an app with the same package name but a different signature,
`adb install -r` reports `INSTALL_FAILED_UPDATE_INCOMPATIBLE`; uninstall the old package
first (this loses saves, back them up):

```powershell
adb uninstall com.windowsgames.firefight
```

---

## 4. What each command does

### 4.1 `prepare_work.ps1`

1. Resolves the toolchain and prepares `core/framework/1.apk`.
2. `apktool d -f -p core/framework -o core/work/v35 <your APK>`; the decode log goes to
   `core/logs/decode.log`.
3. Runs `core/tools/patch_worktree.py`, which performs three idempotent patches:
   * **Register the two Activities**: after the game's own `MyGame` in `AndroidManifest.xml`,
     insert `ModManagerActivity` (`exported=false`) and `ModImportActivity` (`exported=true`
     with 3 intent-filters: `VIEW application/zip`, `VIEW application/x-zip-compressed`,
     `SEND application/zip`). Running it again does not insert a second copy.
   * **Add the sample mods**: `core/assets/Mod/` -> `work/v35/assets/Mod/`.
   * **Make room for the old game entry point**: back up
     `smali*/com/windowsgames/firefight/MyGame.smali` to `core/backup/MyGame.smali.orig`
     and delete it -- the new `MyGame` comes from Java source, and only one copy of that
     class name may exist in the dex files.
4. Self-check: prints whether both Activities are registered and whether the old smali is
   gone, and exits with an error if not.

`-Force` deletes an existing worktree and starts over; `-SkipPatch` only decodes without
patching (for when you edit the worktree by hand).

### 4.2 `build.ps1` (8 steps)

| Step | What it does | Key point |
| --- | --- | --- |
| [1/8] | `gen_l10n.py` generates `res/values*/ffm_strings.xml` | 28 languages + default English; also validates that every `t("key")` used in Java is defined |
| [2/8] | Back up and delete the old `MyGame.smali` | Idempotent: skipped if already deleted |
| [3/8] | `javac --release 8` compiles the `SDLActivity` stub in `core/stub` | Only so the manager sources compile; never packaged |
| [4/8] | `javac --release 8` compiles all of `core/src` -> `jar` | Prints the class count |
| [5/8] | `d8 --min-api 24 --release` produces `classes.dex` | |
| [6/8] | `apktool b -p core/framework -o core/build/unsigned.apk core/work/v35` | Log at `core/logs/build-apktool.log` |
| [7/8] | `inject_dex.py` appends `classes.dex` to the APK as **`classes4.dex`** | Append-only: existing dex and `.so` entries are never rewritten, which keeps the huge `.so` entries `stored` (uncompressed) |
| [8/8] | `zipalign -f -p 4` + `apksigner sign` (v1+v2) | `-NoSign` stops before signing to leave an unsigned package |

The build script deliberately does **not** set `$ErrorActionPreference = "Stop"`:
`javac`/`d8`/`apksigner` write warnings to stderr, and in Stop mode PowerShell treats a
native command's stderr as a terminating error. Every step checks `$LASTEXITCODE`
explicitly.

---

## 5. What the manager adds to the APK (verify it yourself)

`pwsh -File core/tools/verify_apk.ps1` checks all of this automatically (G1-G9 must all be 0
to pass):

* `classes4.dex` exists and the original dex / `.so` entries were not rewritten (`.so` is
  still `stored`);
* `AndroidManifest.xml` contains `MyGame`, `ModManagerActivity`, `ModImportActivity`;
* `res` contains the manager strings such as `ffm_mm_*`, and the Chinese/Japanese
  translations can be found (`自定义地图`, `カスタムマップ`);
* `zipalign` and `apksigner` validation passes, package `com.windowsgames.firefight.mod`,
  `versionCode 13200`.

---

## 6. Common problems

**It will not install / signature conflict.** The game you have and the package you are
installing were signed with different keys. Uninstall the old package on the device first, or
sign your package with the same certificate (`-Keystore`).

**After launching I only see the prompt, or the game starts after 15 seconds.** That is the
intended behaviour: "No" or the timeout goes straight into the game, only "Yes" opens the
manager. To change the default, see `MyGame.java`.

**No mods/maps in the manager.** The manager reads the mod directory inside the game sandbox
and only lists *installed* custom mods and maps; with no custom content the list is empty and
shows a placeholder. Put something there first, using the game's own import or the manager's
"Import mod".

**Android 11+ and the manager cannot read/write external directories.** The game manifest
already declares `MANAGE_EXTERNAL_STORAGE`; the system offers the grant on first entry, and
"About -> Permissions" in the manager jumps to it too.

**apktool reports `Could not create framework directory: C:\Users\<you>\AppData\Local\apktool\framework`.**
It wants to write the default framework directory but has no permission. This project's
scripts always pass `-p core/framework` and never touch that directory.

**Not enough disk / only want an unsigned package.** `pwsh -File core/tools/build.ps1 -NoSign`.

---

## 7. Rolling back

The manager does not modify the original APK's dex or resources; all changes live in the
worktree. To go back:

```powershell
# reinstall your own original APK
adb install -r "D:\APK\Sean's 13.2.0_Mod_v35(1).apk"
# or wipe everything
Remove-Item -Recurse -Force core\work, core\build, out
```

`core/backup/MyGame.smali.orig` holds the replaced old entry-point smali, for when you need
to restore the worktree by hand.
