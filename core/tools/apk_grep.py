"""Search an APK for byte strings, to check which file names the game recognises.

Usage: python apk_grep.py <apk> needle1 needle2 ..."""
import re
import sys
import zipfile

SKIP = re.compile(r"\.(png|jpg|jpeg|ogg|wav|mp3|ttf|otf|webp|dat)$", re.I)
KEEP = re.compile(r"(\.so$|\.dex$|^assets/.*\.(txt|json|xml|cfg|ini)$)", re.I)

def main():
    apk = sys.argv[1]
    needles = [n.encode("utf-8") for n in sys.argv[2:]]
    counts = {n: 0 for n in sys.argv[2:]}
    hits = {n: [] for n in sys.argv[2:]}
    with zipfile.ZipFile(apk) as z:
        for info in z.infolist():
            name = info.filename
            if SKIP.search(name) or not KEEP.search(name):
                continue
            if info.file_size > 80 * 1024 * 1024:
                continue
            try:
                data = z.read(name)
            except Exception:
                continue
            for text, needle in zip(sys.argv[2:], needles):
                c = data.count(needle)
                if c:
                    counts[text] += c
                    if len(hits[text]) < 8:
                        hits[text].append((name, c))
    for text in sys.argv[2:]:
        print("### %s -> %d hits" % (text, counts[text]))
        for name, c in hits[text]:
            print("    %-60s x%d" % (name, c))

if __name__ == "__main__":
    main()
