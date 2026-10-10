# -*- coding: utf-8 -*-
"""签名 APK 的 zip 层静态校验（MVP 验证 G1/G2/G7）。

用法: python tools/verify_apk.py <apk> [dex-classes.dex]

检查项：
  * classes*.dex 条目齐全，且新注入的那一个存在
  * lib/**/*.so 全部 STORED（extractNativeLibs="false" 的硬要求）
  * assets/Mod 内置模组包在包里
  * AndroidManifest.xml / resources.arsc 存在
"""
import sys
import zipfile

FAIL = 0


def check(ok, msg):
    global FAIL
    print(("  OK   " if ok else "  FAIL ") + msg)
    if not ok:
        FAIL = 1


def main():
    apk = sys.argv[1]
    want_dex = sys.argv[2] if len(sys.argv) > 2 else "classes4.dex"
    print("APK: %s" % apk)
    with zipfile.ZipFile(apk) as z:
        names = z.namelist()
        infos = {i.filename: i for i in z.infolist()}

        print("[dex]")
        dexes = [n for n in names if n.endswith(".dex")]
        for n in sorted(dexes):
            i = infos[n]
            print("      %-16s %-8s %d bytes" % (
                n, "stored" if i.compress_type == 0 else "deflate", i.file_size))
        check(want_dex in names, "%s 已注入" % want_dex)
        check(len(dexes) >= 4, "dex 条目数 >= 4（实际 %d）" % len(dexes))

        print("[native]")
        sos = [n for n in names if n.startswith("lib/") and n.endswith(".so")]
        bad = [n for n in sos if infos[n].compress_type != 0]
        check(len(sos) > 0, "lib/**/*.so 存在（%d 个）" % len(sos))
        check(not bad, "所有 .so 都是 STORED（违规 %d 个）" % len(bad))
        for n in bad[:10]:
            print("      VIOLATION %s" % n)

        print("[builtin mods]")
        mods = [n for n in names if n.startswith("assets/Mod/")]
        for n in sorted(mods):
            print("      %s" % n)
        check(any(n.endswith(".zip") for n in mods), "内置 zip 模组包存在")
        check(any(n.endswith("/mod.json") for n in mods), "内置目录模组包(mod.json)存在")

        print("[core]")
        check("AndroidManifest.xml" in names, "AndroidManifest.xml")
        check("resources.arsc" in names, "resources.arsc")

    print("\nRESULT: %s" % ("PASS" if FAIL == 0 else "FAIL"))
    return FAIL


if __name__ == "__main__":
    sys.exit(main())
