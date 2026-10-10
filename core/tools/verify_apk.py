"""Zip-level checks on a signed APK: the dex entries, the STORED .so entries and the bundled
assets.

Usage: python verify_apk.py <apk>"""
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
        check(want_dex in names, "%s injected" % want_dex)
        check(len(dexes) >= 4, "dex entry count >= 4 (actual %d)" % len(dexes))

        print("[native]")
        sos = [n for n in names if n.startswith("lib/") and n.endswith(".so")]
        bad = [n for n in sos if infos[n].compress_type != 0]
        check(len(sos) > 0, "lib/**/*.so present (%d files)" % len(sos))
        check(not bad, "all .so entries are STORED (%d violations)" % len(bad))
        for n in bad[:10]:
            print("      VIOLATION %s" % n)

        print("[builtin mods]")
        mods = [n for n in names if n.startswith("assets/Mod/")]
        for n in sorted(mods):
            print("      %s" % n)
        check(any(n.endswith(".zip") for n in mods), "built-in zip mod package present")
        check(any(n.endswith("/mod.json") for n in mods), "built-in directory mod package (mod.json) present")

        print("[core]")
        check("AndroidManifest.xml" in names, "AndroidManifest.xml")
        check("resources.arsc" in names, "resources.arsc")

    print("\nRESULT: %s" % ("PASS" if FAIL == 0 else "FAIL"))
    return FAIL

if __name__ == "__main__":
    sys.exit(main())
