"""Append classes4.dex to a built APK without rewriting the existing entries, because every
lib/**/*.so has to stay STORED (the manifest sets extractNativeLibs="false").

Usage: python inject_dex.py <apk> <classes.dex>"""
import sys
import zipfile

def main():
    if len(sys.argv) < 3:
        print("usage: inject_dex.py <apk> <dex> [entry-name]")
        return 2
    apk, dex = sys.argv[1], sys.argv[2]
    entry = sys.argv[3] if len(sys.argv) > 3 else "classes4.dex"

    with open(dex, "rb") as fh:
        data = fh.read()

    with zipfile.ZipFile(apk, "a", zipfile.ZIP_STORED) as z:
        if entry in z.namelist():
            print("SKIP %s already present in %s" % (entry, apk))
            return 3
        z.writestr(entry, data, compress_type=zipfile.ZIP_STORED)

    with zipfile.ZipFile(apk) as z:
        dexes = [(i.filename, "stored" if i.compress_type == 0 else "deflate",
                  i.file_size) for i in z.infolist() if i.filename.endswith(".dex")]
        bad = [i.filename for i in z.infolist()
               if i.filename.startswith("lib/") and i.filename.endswith(".so")
               and i.compress_type != 0]
        print("injected %s (%d bytes)" % (entry, len(data)))
        for name, how, size in dexes:
            print("  dex: %-16s %-8s %d" % (name, how, size))
        print("stored-so-violations: %d" % len(bad))
        if bad:
            for name in bad[:10]:
                print("  VIOLATION %s" % name)
            return 4
    return 0

if __name__ == "__main__":
    sys.exit(main())
