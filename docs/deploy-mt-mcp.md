# Injecting the mod manager with MT Manager (MCP)

**English** | [中文](../zh-CN/deploy-mt-mcp.md)

The PC route (see the [deployment guide](deploy.md)) needs a JDK, the Android SDK and
apktool installed on a computer. This document is the **on-device route**: use the MCP
interface of MT Manager to append the manager `classes.dex` built on the PC, then patch the
manifest, rebuild and sign — no PC toolchain involved. The flow below has been verified
end to end.

---

## 1. Input and output

* Input: the stock `Firefight 13.2.0.APK` (package `com.windowsgames.firefight`,
  `versionCode 13200`, 3 dex files) plus an already compiled `classes.dex` (≈68 KB, the
  artifact of step [5/8] of `build.ps1` on the PC side).
* Output: an installable APK with package name `com.windowsgames.firefight.mod`, signed
  with testkey.

---

## 2. What MT MCP can and cannot do

1. No shell / JDK / SDK / apktool / PowerShell → it **cannot compile Java**, so
   `classes.dex` has to be produced on the PC first.
2. `mt_apk_copy_entry` / `mt_apk_patch_bytes` / `mt_apk_delete_target` **refuse to operate
   on `classes*.dex`**.
3. `mt_zip_update` treats the APK as an ordinary ZIP, so it can append `classes4.dex`
   (**without signing**).
4. When the same descriptor exists in several dex files: reading returns the **first dex**,
   deleting/writing fails with `AMBIGUOUS_CLASS_IN_DEX`, and there is **no dex-qualified
   locator**.
5. `mt_apk_workspace_open` requires a valid APK containing `AndroidManifest.xml`, otherwise
   it fails with `NOT_APK`.
6. `mt_zip_create` / `mt_zip_extract` are general ZIP tools and can handle
   `AndroidManifest.xml` and `classes.dex`.

---

## 3. Flow

```text
[prepare] PC: build.ps1 -> classes.dex (manager)
  |
[0] copy the base APK to a working copy (leave the original alone)
  |
[1] mt_zip_update: classes.dex -> append as classes4.dex
  |
[2] mt_apk_workspace_open(working copy) + mt_apk_session_create
  |
[3] remove the duplicate MyGame
    3.1 mt_zip_extract the base APK's AndroidManifest.xml
        mt_zip_create -> mgr_only.apk {AndroidManifest.xml, classes.dex}
        open -> read dex_class:.../MyGame   (= new smali, 515 lines)
    3.2 delete dex_class:.../MyGame -> build(sign=false) -> mgr_only_fixed.apk
    3.3 mt_zip_extract -> mgr_fixed.dex
    3.4 mt_zip_update: mgr_fixed.dex -> classes4.dex (overwrite)
  |
[4] reopen the working copy + a new session; edit_text(write_target) writes the new MyGame back
  |
[5] edit_text(axml) changes 5 places atomically:
    package -> .mod; permission/uses-permission names -> *.mod.*;
    provider authorities -> *.mod.androidx-startup;
    insert ModManagerActivity / ModImportActivity(+intent-filter) after MyGame
  |
[6] optional: resource_add for ffm_*; copy_entry for assets/Mod
  |
[7] mt_apk_session_check(runBuildChecks=true) -> passed
  |
[8] mt_apk_build(sign=true) -> testkey-signed APK
  |
[9] reopen the built APK for acceptance checks
```

---

## 4. Cautions

1. **The duplicate class must be removed**: both the manager dex and the game dex contain a
   `MyGame` entry point; if both stay, `classes.dex` wins and the old version shadows the new
   one → the manager never appears. Step [3] drops `MyGame` from the manager dex and lets the
   game dex carry the new version.
2. **`.so` entries must stay STORED** (`extractNativeLibs=false`): neither `mt_zip_update`
   nor `mt_apk_build` recompresses existing entries; check that every `lib/**` entry has the
   same byte count before and after.
3. **Missing `ffm_*` resources fall back to key names** (`L10n.t()` resolves them by name
   through `getIdentifier`): usable, but ugly.
4. **Changing the package name means changing more than that**: the custom permission name
   and the provider authority as well, otherwise installing next to the original fails with
   `INSTALL_FAILED_CONFLICTING_PROVIDER`.
5. **A session is a snapshot**: if you modify the APK file after opening it, you must reopen
   the workspace and create a new session.
6. A new Activity's `android:name` must be the **fully qualified name**
   (`com.windowsgames.firefight.ModManagerActivity`).
7. `session_check` only inspects content, it does not mean the APK is installable; use
   `mt_apk_build` for a real package.
8. Clean up the `temporary=true` workspaces and intermediate files when you are done.

---

## 5. Compared with the PC scripts

| PC | MT MCP |
| --- | --- |
| `patch_worktree.py` edits the manifest | [5] `edit_text(axml)` |
| `patch_worktree.py` deletes the old `MyGame.smali` | [3] |
| `javac` + `d8` | must be done offline |
| `apktool b` | `mt_apk_build` |
| `inject_dex.py` appends `classes4.dex` | `mt_zip_update` |
| `zipalign` + `apksigner`(testkey) | `mt_apk_build(sign=true)` |

---

## 6. Acceptance

* Package name `com.windowsgames.firefight.mod`, `versionCode 13200`;
* Certificate SHA-256 `a40da80a59d170caa950cf15c18c454d47a39b26989d8b640ecd745ba71bf5dc`;
* 4 dex files; 33 manager classes; `MyGame` present exactly once and being the new version;
* The manifest contains `MyGame` / `ModManagerActivity` / `ModImportActivity`;
* `lib/**/*.so` entries are STORED.

Measured: `Firefight-Mod-Manager-13.2.0.apk` (578,621,576 B) passes all of the above; only
the `ffm_*` strings and `assets/Mod` were not added.
