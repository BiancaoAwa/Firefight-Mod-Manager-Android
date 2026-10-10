#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""把「管理器」需要的工作树改动应用到反编译出来的 work/v35 上。

设计成幂等：重复运行不会重复插入，已存在的文件直接跳过。

它做三件事：

1. `AndroidManifest.xml` 里插入两个 Activity：
   - `ModManagerActivity`：管理器界面（由启动提示框 / 分享入口拉起）
   - `ModImportActivity`：接收 VIEW / SEND `application/zip`，用于从文件管理器
     或别的应用「分享」一个模组 zip 进来
   位置紧跟在游戏自己的 `MyGame` Activity 之后（游戏原有节点一律不动）。
   权限不需要补：游戏本体已经声明了 `MANAGE_EXTERNAL_STORAGE` 与
   `WRITE_EXTERNAL_STORAGE`。

2. `assets/Mod/` 示例数据：`core/assets/Mod` 复制进工作树，方便在设备上直接试
   导入流程。**不想要示例模组就删掉 `core/assets/Mod` 再来跑。**

3. 删除旧 smali `smali*/com/windowsgames/firefight/MyGame.smali`：管理器用同包
   同名的新类接管游戏启动流程，旧的必须让位（先备份到 `core/backup/`）。

用法::

    python core/tools/patch_worktree.py            # 默认 work/v35
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
        return "manifest: 找不到 %s" % path
    text = read(path)
    if "com.windowsgames.firefight.ModManagerActivity" in text:
        return "manifest: 已插入过，跳过"
    at = text.find(MYGAME)
    if at < 0:
        return "manifest: 没找到游戏的 MyGame Activity，工作树可能不是交战 APK"
    end = text.find("</activity>", at)
    if end < 0:
        return "manifest: MyGame Activity 没有正常闭合"
    end = text.find("\n", end) + 1
    if not dry:
        write(path, text[:end] + MANAGER_ACTIVITIES + text[end:])
    return "manifest: 插入 ModManagerActivity + ModImportActivity"


def patch_assets(core, work, dry):
    src = os.path.join(core, "assets", "Mod")
    if not os.path.isdir(src):
        return "assets: core/assets/Mod 不存在，跳过（不需要示例模组）"
    dst = os.path.join(work, "assets", "Mod")
    if os.path.isdir(dst):
        return "assets: 目标已存在，跳过"
    if not dry:
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        shutil.copytree(src, dst)
    return "assets: 复制示例模组到 assets/Mod"


def find_old_smali(work):
    """找出工作树里所有 dex 目录（smali / smali_classes2 / ...）下的 MyGame.smali。"""
    hits = []
    for base, dirs, names in os.walk(work):
        rel = os.path.relpath(base, work).replace("\\", "/")
        if rel.split("/")[0].startswith("smali") and "MyGame.smali" in names:
            hits.append(os.path.join(base, "MyGame.smali"))
    return hits


def drop_old_smali(core, work, dry):
    hits = find_old_smali(work)
    if not hits:
        return "smali: 工作树里没有旧 MyGame.smali，跳过"
    bak = os.path.join(core, "backup")
    for src in hits:
        if not dry:
            os.makedirs(bak, exist_ok=True)
            shutil.copy2(src, os.path.join(bak, "MyGame.smali.orig"))
            os.remove(src)
        print("    - 移除 %s" % os.path.relpath(src, work).replace("\\", "/"))
    return "smali: 备份并删除 %d 个 MyGame.smali（备份在 core/backup/MyGame.smali.orig）" % len(hits)


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
        sys.exit("工作树不存在: %s（先跑 tools/prepare_work.ps1 反编译）" % work)
    print("  - " + patch_manifest(work, args.dry_run))
    print("  - " + patch_assets(core, work, args.dry_run))
    print("  - " + drop_old_smali(core, work, args.dry_run))
    if args.dry_run:
        print("（dry-run，未写盘）")


if __name__ == "__main__":
    main()
