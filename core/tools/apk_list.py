"""List the entries of an APK, optionally filtered by substring.

Usage: python apk_list.py <apk> [substring ...]"""
import sys
import zipfile

def main():
    apk = sys.argv[1]
    pats = [p.lower() for p in sys.argv[2:]]
    with zipfile.ZipFile(apk) as z:
        names = z.namelist()
        hits = [n for n in names
                if not pats or any(p in n.lower() for p in pats)]
        print("total entries %d, matches %d" % (len(names), len(hits)))
        for n in sorted(hits):
            print("  %s" % n)
        if not pats:
            top = {}
            for n in names:
                key = n.split("/")[0]
                top[key] = top.get(key, 0) + 1
            print("\nTop-level distribution:")
            for k, v in sorted(top.items(), key=lambda kv: -kv[1])[:30]:
                print("  %-40s %d" % (k, v))

if __name__ == "__main__":
    main()
