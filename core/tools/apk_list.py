# -*- coding: utf-8 -*-
"""列出 APK 内的条目（可按关键字过滤），不解压。

用法: python tools/apk_list.py <apk> [子串1 子串2 ...]
"""
import sys
import zipfile


def main():
    apk = sys.argv[1]
    pats = [p.lower() for p in sys.argv[2:]]
    with zipfile.ZipFile(apk) as z:
        names = z.namelist()
        hits = [n for n in names
                if not pats or any(p in n.lower() for p in pats)]
        print("总条目 %d，命中 %d" % (len(names), len(hits)))
        for n in sorted(hits):
            print("  %s" % n)
        if not pats:
            top = {}
            for n in names:
                key = n.split("/")[0]
                top[key] = top.get(key, 0) + 1
            print("\n顶层分布:")
            for k, v in sorted(top.items(), key=lambda kv: -kv[1])[:30]:
                print("  %-40s %d" % (k, v))


if __name__ == "__main__":
    main()
