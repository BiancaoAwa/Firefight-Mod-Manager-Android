#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Apply the worktree changes the manager needs to the decoded work/v35.

Designed to be idempotent: running it twice does not insert duplicates, and existing files are skipped.

It does three things:

1. Insert two Activities into `AndroidManifest.xml`:
   - `ModManagerActivity`: the manager UI (opened by the launch prompt / share entry point)
   - `ModImportActivity`: receives VIEW / SEND `application/zip`, used to share a mod zip in from a file
     manager or another app
   The position is right after the game's own `MyGame` Activity (no existing game node is touched).
   No permission needs to be added: the game itself already declares `MANAGE_EXTERNAL_STORAGE` and
   `WRITE_EXTERNAL_STORAGE`.

2. Sample `assets/Mod/` data: `core/assets/Mod` is copied into the worktree so the import flow can be
   tried directly on a device. **Delete `core/assets/Mod` before running if you do not want them.**

3. Delete the old smali `smali*/com/windowsgames/firefight/MyGame.smali`: the manager takes over the
   game launch flow with a new class of the same package and name, so the old one must give way (backed up to `core/backup/` first).

Usage::

    python core/tools/patch_worktree.py            # work/v35 by default
    python core/tools/patch_worktree.py -w core/work/v35 -c core
"""
import argparse
import io
import os
import shutil
import sys

MANAGER_ACTIVITIES = (
    '        <activity android:configChanges="keyboardHidden|orientation|screenSize"'
    ' android:exported="false" android:label="@string/app_name"'
    ' android:name="com.windowsgames.firefight.ModManagerActivity"'
    ' android:theme="@android:style/Theme.DeviceDefault.NoActionBar"/>\n'
    '        <activity android:configChanges="keyboardHidden|orientation|screenSize"'
    ' android:excludeFromRecents="true" android:exported="true"'
    ' android:label="@string/app_name"'
    ' android:name="com.windowsgames.firefight.ModImportActivity"'
    ' android:theme="@android:style/Theme.DeviceDefault.Dialog">\n'
    '            <intent-filter>\n'
    '                <action android:name="android.intent.action.VIEW"/>\n'
    '                <category android:name="android.intent.category.DEFAULT"/>\n'
    '                <data android:mimeType="application/zip"/>\n'
    '            </intent-filter>\n'
    '            <intent-filter>\n'
    '                <action android:name="android.intent.action.VIEW"/>\n'
    '                <category android:name="android.intent.category.DEFAULT"/>\n'
    '                <data android:mimeType="application/x-zip-compressed"/>\n'
    '            </intent-filter>\n'
    '            <intent-filter>\n'
    '                <action android:name="android.intent.action.SEND"/>\n'
    '                <category android:name="android.intent.category.DEFAULT"/>\n'
    '                <data android:mimeType="application/zip"/>\n'
    '            </intent-filter>\n'
    '        </activity>\n')

MYGAME = 'android:name="com.windowsgames.firefight.MyGame"'


def read(path):
    with io.open(path, encoding="utf-8") as fh:
        return fh.read()


def write(path, text):
    with io.open(path, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(text)


def patch_manifest(work, dry):
    path = os.path.join(work, "AndroidManifest.xml")
    if not os.path.isfile(path):
        return "manifest: cannot find %s" % path
    text = read(path)
    if "com.windowsgames.firefight.ModManagerActivity" in text:
        return "manifest: already inserted, skipping"
    at = text.find(MYGAME)
    if at < 0:
        return "manifest: the game's MyGame Activity was not found; the worktree may not be a Firefight APK"
    end = text.find("</activity>", at)
    if end < 0:
        return "manifest: the MyGame Activity is not closed properly"
    end = text.find("\n", end) + 1
    if not dry:
        write(path, text[:end] + MANAGER_ACTIVITIES + text[end:])
    return "manifest: inserted ModManagerActivity + ModImportActivity"


def patch_assets(core, work, dry):
    src = os.path.join(core, "assets", "Mod")
    if not os.path.isdir(src):
        return "assets: core/assets/Mod does not exist, skipping (samples not wanted)"
    dst = os.path.join(work, "assets", "Mod")
    if os.path.isdir(dst):
        return "assets: target already exists, skipping"
    if not dry:
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        shutil.copytree(src, dst)
    return "assets: copied the sample mods to assets/Mod"


def find_old_smali(work):
    """Find MyGame.smali under every dex directory (smali / smali_classes2 / ...) in the worktree."""
    hits = []
    for base, dirs, names in os.walk(work):
        rel = os.path.relpath(base, work).replace("\\", "/")
        if rel.split("/")[0].startswith("smali") and "MyGame.smali" in names:
            hits.append(os.path.join(base, "MyGame.smali"))
    return hits


def drop_old_smali(core, work, dry):
    hits = find_old_smali(work)
    if not hits:
        return "smali: no old MyGame.smali in the worktree, skipping"
    bak = os.path.join(core, "backup")
    for src in hits:
        if not dry:
            os.makedirs(bak, exist_ok=True)
            shutil.copy2(src, os.path.join(bak, "MyGame.smali.orig"))
            os.remove(src)
        print("    - removed %s" % os.path.relpath(src, work).replace("\\", "/"))
    return "smali: backed up and deleted %d MyGame.smali (backup at core/backup/MyGame.smali.orig)" % len(hits)


def main():
    here = os.path.dirname(os.path.abspath(__file__))
    core_default = os.path.dirname(here)
    ap = argparse.ArgumentParser()
    ap.add_argument("-w", "--work", default=os.path.join(core_default, "work", "v35"))
    ap.add_argument("-c", "--core", default=core_default)
    ap.add_argument("-n", "--dry-run", action="store_true")
    args = ap.parse_args()
    work = os.path.abspath(args.work)
    core = os.path.abspath(args.core)
    if not os.path.isdir(work):
        sys.exit("worktree does not exist: %s (run tools/prepare_work.ps1 first to decode)" % work)
    print("  - " + patch_manifest(work, args.dry_run))
    print("  - " + patch_assets(core, work, args.dry_run))
    print("  - " + drop_old_smali(core, work, args.dry_run))
    if args.dry_run:
        print("(dry-run, nothing written)")


if __name__ == "__main__":
    main()
