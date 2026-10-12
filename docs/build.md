# Build scripts

**English** | [中文](../zh-CN/build.md)

All scripts live in `core/tools/`. Day to day you only need two commands; the details are
here.

| Script | Purpose |
| --- | --- |
| `common.ps1` | Shared library: path variables, toolchain auto-discovery, framework/keystore preparation. Other scripts pull it in with `. "$PSScriptRoot\common.ps1"` |
| `prepare_work.ps1` | Decodes the game APK into `core/work/v35` and applies the manager patches |
| `build.ps1` | The 8-step build: localisation -> compile -> d8 -> apktool rebuild -> dex injection -> align and sign |
| `verify_apk.ps1` | Static verification G1-G9; prints `RESULT: PASS` and exits 0 when everything passes |
| `smoke_test.ps1` | On-device smoke test: `adb install -r` -> launch -> screenshot -> collect logcat/crash/directory listing |
| `gen_l10n.py` | Merges the five string tables, writes `res/values*/ffm_strings.xml`, validates the keys |
| `patch_worktree.py` | Idempotent worktree patches (manifest + sample mods + removing the old MyGame.smali) |
| `inject_dex.py` | Appends `classes.dex` to the APK as `classes4.dex` (never rewrites existing entries) |
| `verify_apk.py` | The zip-level check behind G2 (dex count, whether `.so` is still `stored`) |
| `apk_grep.py` / `apk_list.py` | Small APK content search/listing helpers for troubleshooting |

## 1. Arguments

```powershell
# decode + patch
pwsh -File core/tools/prepare_work.ps1 -GameApk <your Firefight APK> [-Force] [-SkipPatch]
                                   [-JdkHome <dir>] [-ApktoolJar <jar>] [-Python <exe>]

# build
pwsh -File core/tools/build.ps1 [-JdkHome <dir>] [-AndroidSdk <dir>] [-ApktoolJar <jar>]
                                [-Python <exe>] [-Keystore <file>] [-KsPass <pw>]
                                [-KeyAlias <name>] [-KeyPass <pw>] [-OutDir <dir>] [-OutName <name>] [-NoSign]

# verify
pwsh -File core/tools/verify_apk.ps1 [-Apk <file>] [-JdkHome <dir>] [-AndroidSdk <dir>] [-Python <exe>]
```

The signed APK is written to `out/` inside the repository by default (git-ignored);
pass `-OutDir <dir>` to put it somewhere else.

## 2. How the toolchain is located

| Needed | Lookup order |
| --- | --- |
| JDK | `-JdkHome` -> `JAVA_HOME` -> `java` on PATH (must provide `bin\javac.exe`) |
| Android SDK | `-AndroidSdk` -> `ANDROID_HOME` -> `ANDROID_SDK_ROOT` -> `%LOCALAPPDATA%\Android\Sdk` |
| `android.jar` | The highest-versioned `platforms\android-*` under the SDK |
| build-tools | The highest-versioned `build-tools\*` under the SDK that contains `d8.bat` |
| apktool | `-ApktoolJar` -> `APKTOOL_JAR` -> `core\tools\apktool.jar` -> `core\tools\apktool*.jar` -> `Downloads\apktool*.jar` |
| Python | `-Python` -> `python` / `python3` / `py` on PATH |
| Signing keystore | `-Keystore` -> `core\keystore\testkey.jks` (the public AOSP testkey, alias/password `testkey`) -> `core\keystore\debug.keystore` (created on the spot with `keytool` if missing) |
| Key password / alias | `-KsPass` / `-KeyAlias` / `-KeyPass`; without them the defaults follow the chosen keystore (`testkey` or `android` + `androiddebugkey`) |
| apktool framework | `Ensure-Framework` extracts `prebuilt/android-framework.jar` from the apktool jar into `core/framework/1.apk` before every build |

If any of them is missing the script says exactly "install this / pass that argument" and
exits; it never fails halfway through.

## 3. Exit codes and logs

* Scripts print `[FAIL] <step> exit code N` through `Fail` and exit 1; success ends with
  `=== BUILD OK ===` / `=== PREPARE OK ===` / `RESULT: PASS`.
* Logs: decoding `core/logs/decode.log`, apktool rebuild `core/logs/build-apktool.log`,
  `aapt2 dump resources` output `core/logs/apk-resources.txt`.
* The build scripts **deliberately do not** use `$ErrorActionPreference = "Stop"`: `javac`,
  `d8` and `apksigner` write warnings to stderr, and in Stop mode PowerShell treats a native
  command's stderr as a terminating error. Every step checks `$LASTEXITCODE` explicitly.

## 4. What a successful build looks like

```
[0/8] checking toolchain ...    JDK / android.jar / build-tools / apktool / python
[1/8] generating res/values*/ffm_strings.xml    languages: 28 (+ default English)
                                                keys actually referenced by Java: 83, all covered
[2/8] backing up and removing the old MyGame.smali   backup -> core/backup/MyGame.smali.orig (48557 bytes)
[3/8] javac stub classes (SDLActivity)
[4/8] javac manager sources                     class files: 61
[5/8] d8 -> classes.dex                         classes.dex: ~90000 bytes
[6/8] apktool rebuild of the worktree           unsigned.apk: ~259 MB
[7/8] injecting classes4.dex (append-only)      classes4.dex stored / stored-so-violations: 0
[8/8] zipalign + apksigner                      signed: out\...apk (~260 MB)
=== BUILD OK ===
```

The `verify_apk.ps1` gates:

| Gate | Check |
| --- | --- |
| G1 | Artifact exists, size, timestamp |
| G2 | Zip level: `classes4.dex` was added next to `classes.dex`; `.so` entries are still `stored` (not recompressed) |
| G3 | `zipalign -c -v 4` |
| G4 | `apksigner verify --print-certs` |
| G5 | `aapt2 dump badging` (package `com.windowsgames.firefight.mod`, `versionCode 13200`) |
| G6 | `dexdump` listing the manager dex's classes |
| G7 | Size |
| G8 | `ffm_*` resources present, 23 keys spot-checked, Chinese/Japanese translations readable |
| G9 | `MyGame` / `ModManagerActivity` / `ModImportActivity` present in the manifest |

## 5. Changing strings / adding a language

1. Add the key and its translations in `core/tools/l10n_round5.py` (the newest round; older keys
   live in `l10n_round4.py` / `l10n_round3.py` / `l10n_extra.py` / `l10n_data.py`);
2. Reference it from Java with `t("ffm_mm_your_key")`;
3. Run `build.ps1` -- `gen_l10n.py` validates both "used by Java but undefined" and
   "defined but misspelled" keys, and aborts if anything is wrong.

Adding a language: add that language's dict to `l10n_round5.py` (or to the round that introduces
the keys); missing keys fall back to English automatically (the build log prints coverage hints
such as `values-<locale> 43/201 keys`).

> Current state: all 28 languages cover all 201 keys (`gen_l10n.py` prints `201/201` for each of
> them). A few of the older translations were produced by an early automated pass and have not
> been proof-read by native speakers.

## 6. Common problems

**`AuthorizationManager check failed` / `PSSecurityException`.**
The PowerShell execution policy is blocking the script. Relax it for the current session:
`Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass -Force`.

**`apktool: command not found` / jar not found.**
Put an apktool 3.x jar at `core/tools/apktool.jar`, or point at it with `-ApktoolJar`.

**The rebuild is slow or runs out of memory.**
`apktool b` runs through `java -Xmx4g`; the worktree is 230 MB+, so 3-8 minutes for the
rebuild is normal. Leave 1.5 GB of free disk space as well.

**The build succeeds but the APK will not install.**
See section 6 of [deploy.md](deploy.md) (signature conflict / uninstall first).
