package com.windowsgames.firefight;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 自定义地图导入（需求⑦）。
 *
 * 游戏侧事实（从 libmain.so 的字符串表和官方文档核实）：
 *   - 地图目录 = &lt;base&gt;/Custom Maps/，每张地图一个 4 字符代码命名的文件夹：
 *     Custom Maps/&lt;CODE&gt;/&lt;CODE&gt;-world.dat / -contours.png / -tileNN.jpg / -scenarios.dat ...
 *   - 同目录下必须有一份 CustomMapCodeNames.txt，把地图的 4 字符代码列进去，
 *     否则游戏不认这张地图。
 *   - 地图包（网上下载的）是以 4 字符代码命名的压缩包。
 * 管理器要做的：把 zip 解到 Custom Maps/&lt;CODE&gt;/，并把 CODE 登记进 CustomMapCodeNames.txt。
 */
public final class MapImporter {

    public static final String DIR = "Custom Maps";
    public static final String CODES = "CustomMapCodeNames.txt";

    private static final Pattern CODE = Pattern.compile("^[A-Za-z0-9]{4}$");

    private MapImporter() {
    }

    // ------------------------------------------------------------ 数据结构

    public static class MapEntry {
        public String code = "";
        public File dir;
        public int files;
        public long bytes;
        /** 文件夹在磁盘上。 */
        public boolean present;
        /** 代码已登记进 CustomMapCodeNames.txt。 */
        public boolean listed;
        /** <CODE>-scenarios.txt 里 <map> 下的第一个 <name>，取不到为空串。 */
        public String name = "";

        public String label() {
            return code;
        }

        /** 规格③：名字读 ****-scenarios.txt 的 &lt;name&gt;，取不到显示 4 位代码。 */
        public String display(Context c) {
            if (name != null && name.length() > 0) {
                return name;
            }
            return code;
        }

        /** 规格③：地图文件大小（自适应 KB / MB）。 */
        public String sizeText() {
            return human(bytes);
        }

        public String state(Context c) {
            if (present && listed) {
                return "OK";
            }
            if (present && !listed) {
                return L10n.t(c, "map_state_unlisted", "not listed");
            }
            return L10n.t(c, "map_state_missing", "folder missing");
        }
    }

    /** 规格③：读 &lt;dir&gt;/&lt;CODE&gt;-scenarios.txt 里 &lt;map&gt; 的第一个 &lt;name&gt;。 */
    public static String mapName(File dir, String code) {
        if (dir == null || code == null || code.length() == 0) {
            return "";
        }
        File f = scenariosFile(dir, code);
        if (f == null || !f.isFile()) {
            return "";
        }
        try {
            String t = readText(new FileInputStream(f));
            Matcher m = Pattern.compile("<name[^>]*>\\s*([^<]*)",
                    Pattern.CASE_INSENSITIVE).matcher(t);
            if (m.find()) {
                String s = m.group(1);
                s = s == null ? "" : s.trim().replace('\n', ' ').replace('\r', ' ').trim();
                if (s.length() > 0) {
                    return s;
                }
            }
        } catch (Throwable ignored) {
        }
        return "";
    }

    /** 地图的剧本文件：&lt;CODE&gt;-scenarios.txt 优先，其次 .dat（.so 里两种都出现过）。 */
    public static File scenariosFile(File dir, String code) {
        if (dir == null || code == null) {
            return null;
        }
        String[] exts = {".txt", ".dat"};
        for (int i = 0; i < exts.length; i++) {
            File f = new File(dir, code + "-scenarios" + exts[i]);
            if (f.isFile()) {
                return f;
            }
        }
        File[] kids = dir.listFiles();
        if (kids != null) {
            String want = (code + "-scenarios").toLowerCase(Locale.US);
            for (int i = 0; i < kids.length; i++) {
                String n = kids[i].getName().toLowerCase(Locale.US);
                if (kids[i].isFile() && n.startsWith(want)) {
                    return kids[i];
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------ 目录

    public static File mapsRoot(Context c) {
        File base = MyGame.rootOfStatic(c);
        if (base == null) {
            return null;
        }
        File r = new File(base, DIR);
        if (!r.isDirectory() && !r.mkdirs()) {
            return null;
        }
        return r;
    }

    private static File codesFile(Context c) {
        File r = mapsRoot(c);
        return r == null ? null : new File(r, CODES);
    }

    // ------------------------------------------------------------ 列表

    public static List<MapEntry> listMaps(Context c) {
        List<MapEntry> out = new ArrayList<MapEntry>();
        Map<String, MapEntry> byCode = new LinkedHashMap<String, MapEntry>();
        File root = mapsRoot(c);
        if (root != null) {
            File[] kids = root.listFiles();
            if (kids != null) {
                Arrays.sort(kids, new Comparator<File>() {
                    public int compare(File a, File b) {
                        return a.getName().compareToIgnoreCase(b.getName());
                    }
                });
                for (int i = 0; i < kids.length; i++) {
                    File f = kids[i];
                    if (!f.isDirectory() || f.getName().startsWith(".")) {
                        continue;
                    }
                    MapEntry e = new MapEntry();
                    e.code = f.getName();
                    e.dir = f;
                    e.present = true;
                    e.files = countFiles(f);
                    e.bytes = sizeOf(f);
                    e.name = mapName(f, e.code);
                    byCode.put(e.code.toUpperCase(Locale.US), e);
                }
            }
        }
        List<String> codes = readCodes(c);
        for (int i = 0; i < codes.size(); i++) {
            String code = codes.get(i);
            String up = code.toUpperCase(Locale.US);
            MapEntry e = byCode.get(up);
            if (e == null) {
                e = new MapEntry();
                e.code = code;
                byCode.put(up, e);
            }
            e.listed = true;
        }
        out.addAll(byCode.values());
        return out;
    }

    /** 读 CustomMapCodeNames.txt 里的 4 字符代码（按行取第一个 token）。 */
    public static List<String> readCodes(Context c) {
        List<String> out = new ArrayList<String>();
        File f = codesFile(c);
        if (f == null || !f.isFile()) {
            return out;
        }
        try {
            String text = readText(new FileInputStream(f));
            if (text.length() > 0 && text.charAt(0) == '\uFEFF') {
                text = text.substring(1);
            }
            String[] lines = text.split("\n");
            for (int i = 0; i < lines.length; i++) {
                String code = firstCode(lines[i]);
                if (code != null && !out.contains(code)) {
                    out.add(code);
                }
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    /**
     * 从一行里取地图代码。官方文件的具体写法没有样本，
     * 所以这里尽量宽容：取第一个 token，遇到 '=' ':' '|' 就截断，再校验是不是 4 位代码。
     */
    private static String firstCode(String line) {
        if (line == null) {
            return null;
        }
        String s = line.replace("\r", "").trim();
        if (s.length() == 0 || s.startsWith("//") || s.startsWith("#") || s.startsWith(";")) {
            return null;
        }
        String[] tok = s.split("[\\s,;\\t]+");
        if (tok.length == 0) {
            return null;
        }
        String t = tok[0];
        int cut = -1;
        for (int i = 0; i < t.length(); i++) {
            char ch = t.charAt(i);
            if (ch == '=' || ch == ':' || ch == '|') {
                cut = i;
                break;
            }
        }
        if (cut > 0) {
            t = t.substring(0, cut);
        }
        if (!CODE.matcher(t).matches()) {
            return null;
        }
        return t.toUpperCase(Locale.US);
    }

    // ------------------------------------------------------------ 登记 / 注销代码

    public static boolean registerCode(Context c, String code) {
        if (code == null || code.length() != 4) {
            return false;
        }
        code = code.toUpperCase(Locale.US);
        File f = codesFile(c);
        if (f == null) {
            return false;
        }
        try {
            String text = f.isFile() ? readText(new FileInputStream(f)) : "";
            if (containsToken(text, code)) {
                return true;
            }
            StringBuilder sb = new StringBuilder(text);
            if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '\n') {
                sb.append('\n');
            }
            sb.append(code).append('\n');
            writeText(f, sb.toString());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean unregisterCode(Context c, String code) {
        if (code == null || code.length() != 4) {
            return false;
        }
        code = code.toUpperCase(Locale.US);
        File f = codesFile(c);
        if (f == null || !f.isFile()) {
            return false;
        }
        try {
            String text = readText(new FileInputStream(f));
            String[] lines = text.split("\n");
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < lines.length; i++) {
                String raw = lines[i].replace("\r", "");
                String c2 = firstCode(raw);
                if (c2 != null && c2.equals(code)) {
                    continue;
                }
                if (raw.length() == 0 && sb.length() == 0) {
                    continue;
                }
                sb.append(raw).append('\n');
            }
            writeText(f, sb.toString());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean containsToken(String text, String code) {
        Matcher m = Pattern.compile("(?<![A-Za-z0-9])" + Pattern.quote(code) + "(?![A-Za-z0-9])",
                Pattern.CASE_INSENSITIVE).matcher(text);
        return m.find();
    }


    // ------------------------------------------------------------ 导入

    /**
     * 导入一个地图 zip。
     *
     * @return "ok:CODE" / "nocode"（名字里找不到 4 字符代码）/ "fail"
     */
    public static String importZip(Context c, InputStream in, String zipName) {
        File root = mapsRoot(c);
        if (root == null) {
            return "fail";
        }
        File tmp = new File(root, ".tmp_" + System.currentTimeMillis());
        try {
            if (!tmp.mkdirs() && !tmp.isDirectory()) {
                return "fail";
            }
            if (extractZip(in, tmp) <= 0) {
                deleteTree(tmp);
                return "fail";
            }
            String code = detectCode(tmp, zipName);
            if (code == null) {
                deleteTree(tmp);
                return "nocode";
            }
            File content = unwrap(tmp, code);
            File dst = new File(root, code);
            if (dst.exists()) {
                File old = new File(root, ".old/" + code + "-" + System.currentTimeMillis());
                File oldParent = old.getParentFile();
                if (oldParent != null) {
                    oldParent.mkdirs();
                }
                if (!dst.renameTo(old)) {
                    deleteTree(dst);
                }
            }
            if (!content.renameTo(dst)) {
                if (!dst.mkdirs()) {
                    deleteTree(tmp);
                    return "fail";
                }
                copyTree(content, dst);
            }
            deleteTree(tmp);
            registerCode(c, code);
            LogStore.event(c, "map import " + zipName + " -> " + code);
            return "ok:" + code;
        } catch (Throwable t) {
            deleteTree(tmp);
            return "fail";
        }
    }

    /** 删除一张地图：删文件夹 + 从代码表注销。 */
    public static String remove(Context c, String code) {
        if (code == null || code.length() == 0) {
            return "fail";
        }
        File root = mapsRoot(c);
        if (root == null) {
            return "fail";
        }
        boolean ok = true;
        File dir = new File(root, code);
        if (dir.isDirectory() && !deleteTree(dir)) {
            ok = false;
        }
        unregisterCode(c, code);
        LogStore.event(c, "map remove " + code + " -> " + (ok ? "ok" : "fail"));
        return ok ? "ok" : "fail";
    }

    /** 解包：zip 根下就一层同名文件夹时，把内层拿出来用。 */
    private static File unwrap(File tmp, String code) {
        File[] kids = tmp.listFiles();
        if (kids != null && kids.length == 1 && kids[0].isDirectory()) {
            String n = kids[0].getName();
            if (n.equalsIgnoreCase(code) || !CODE.matcher(n).matches()) {
                return kids[0];
            }
        }
        return tmp;
    }

    /**
     * 找 4 字符代码：优先「叫 <CODE> 且里面有 <CODE>-xxx 文件」的文件夹，
     * 其次地图内容文件名前缀，最后回落 zip 文件名。
     */
    public static String detectCode(File dir, String zipName) {
        String fromZip = fromName(zipName);
        String hit = scanForCode(dir, 0);
        if (hit != null) {
            return hit;
        }
        if (fromZip != null && looksLikeMap(dir)) {
            return fromZip;
        }
        return fromZip;
    }

    private static String fromName(String zipName) {
        if (zipName == null) {
            return null;
        }
        String s = zipName;
        int slash = Math.max(s.lastIndexOf('/'), s.lastIndexOf('\\'));
        if (slash >= 0) {
            s = s.substring(slash + 1);
        }
        int dot = s.lastIndexOf('.');
        if (dot > 0) {
            s = s.substring(0, dot);
        }
        s = s.trim();
        if (CODE.matcher(s).matches()) {
            return s.toUpperCase(Locale.US);
        }
        return null;
    }

    /** 找一个「文件夹名是 4 字符」且内部有 <该代码>- 开头的文件的位置。 */
    private static String scanForCode(File dir, int depth) {
        if (depth > 3) {
            return null;
        }
        File[] kids = dir.listFiles();
        if (kids == null) {
            return null;
        }
        Arrays.sort(kids, new Comparator<File>() {
            public int compare(File a, File b) {
                return a.getName().compareToIgnoreCase(b.getName());
            }
        });
        // 先看目录本身有没有 <CODE>-world.dat 这种内容
        String own = codeFromContent(kids);
        if (own != null) {
            return own;
        }
        for (int i = 0; i < kids.length; i++) {
            File f = kids[i];
            if (f.isDirectory() && !f.getName().startsWith(".") && !f.getName().equals("__MACOSX")) {
                String name = f.getName();
                if (CODE.matcher(name).matches() && hasPrefixedFile(f, name)) {
                    return name.toUpperCase(Locale.US);
                }
                String inner = scanForCode(f, depth + 1);
                if (inner != null) {
                    return inner;
                }
            }
        }
        return null;
    }

    private static String codeFromContent(File[] kids) {
        for (int i = 0; i < kids.length; i++) {
            String n = kids[i].getName();
            if (n.endsWith("-world.dat") && n.length() > "-world.dat".length()) {
                String code = n.substring(0, n.length() - "-world.dat".length());
                if (CODE.matcher(code).matches()) {
                    return code.toUpperCase(Locale.US);
                }
            }
        }
        return null;
    }

    private static boolean hasPrefixedFile(File dir, String code) {
        File[] kids = dir.listFiles();
        if (kids == null) {
            return false;
        }
        for (int i = 0; i < kids.length; i++) {
            if (kids[i].getName().startsWith(code + "-")) {
                return true;
            }
        }
        return false;
    }

    private static boolean looksLikeMap(File dir) {
        File[] kids = dir.listFiles();
        if (kids == null) {
            return false;
        }
        for (int i = 0; i < kids.length; i++) {
            String n = kids[i].getName();
            if (n.endsWith("-world.dat") || n.endsWith("-map.jpg") || n.endsWith("-contours.png")
                    || n.endsWith(".dat") || n.endsWith(".png") || n.endsWith(".jpg")) {
                return true;
            }
            if (kids[i].isDirectory()) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------ zip / 文件工具

    /** 解 zip 到 dst，返回解出的条目数；跳过 __MACOSX 与 .DS_Store，拒绝路径穿越。 */
    public static int extractZip(InputStream raw, File dst) throws Exception {
        ZipInputStream zin = new ZipInputStream(raw);
        int n = 0;
        byte[] buf = new byte[8192];
        ZipEntry e;
        while ((e = zin.getNextEntry()) != null) {
            String name = e.getName().replace('\\', '/');
            if (name.startsWith("/") || name.contains("..")) {
                zin.closeEntry();
                continue;
            }
            if (name.startsWith("__MACOSX/") || name.endsWith(".DS_Store") || name.endsWith("/.DS_Store")) {
                zin.closeEntry();
                continue;
            }
            File out = new File(dst, name);
            if (e.isDirectory()) {
                out.mkdirs();
            } else {
                File parent = out.getParentFile();
                if (parent != null) {
                    parent.mkdirs();
                }
                FileOutputStream fos = new FileOutputStream(out);
                int r;
                while ((r = zin.read(buf)) > 0) {
                    fos.write(buf, 0, r);
                }
                fos.close();
                n++;
            }
            zin.closeEntry();
        }
        zin.close();
        return n;
    }

    private static String readText(InputStream in) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) > 0) {
                bos.write(buf, 0, r);
            }
        } finally {
            try {
                in.close();
            } catch (Throwable ignored) {
            }
        }
        return new String(bos.toByteArray(), "UTF-8");
    }

    private static void writeText(File f, String s) throws Exception {
        FileOutputStream out = new FileOutputStream(f, false);
        try {
            out.write(s.getBytes("UTF-8"));
        } finally {
            out.close();
        }
    }

    private static void copyTree(File src, File dst) throws Exception {
        if (src.isDirectory()) {
            dst.mkdirs();
            File[] kids = src.listFiles();
            if (kids != null) {
                for (int i = 0; i < kids.length; i++) {
                    copyTree(kids[i], new File(dst, kids[i].getName()));
                }
            }
        } else {
            File parent = dst.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            FileInputStream in = new FileInputStream(src);
            OutputStream out = new FileOutputStream(dst);
            try {
                byte[] buf = new byte[8192];
                int r;
                while ((r = in.read(buf)) > 0) {
                    out.write(buf, 0, r);
                }
            } finally {
                try {
                    in.close();
                } catch (Throwable ignored) {
                }
                try {
                    out.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    public static boolean deleteTree(File f) {
        if (f == null || !f.exists()) {
            return true;
        }
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) {
                for (int i = 0; i < kids.length; i++) {
                    deleteTree(kids[i]);
                }
            }
        }
        return f.delete();
    }

    private static int countFiles(File dir) {
        int n = 0;
        File[] kids = dir.listFiles();
        if (kids == null) {
            return 0;
        }
        for (int i = 0; i < kids.length; i++) {
            n += kids[i].isDirectory() ? countFiles(kids[i]) : 1;
        }
        return n;
    }

    private static long sizeOf(File dir) {
        long n = 0;
        File[] kids = dir.listFiles();
        if (kids == null) {
            return 0;
        }
        for (int i = 0; i < kids.length; i++) {
            n += kids[i].isDirectory() ? sizeOf(kids[i]) : kids[i].length();
        }
        return n;
    }

    public static String human(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024L * 1024L) {
            return (bytes / 1024) + " KB";
        }
        return String.format(Locale.US, "%.1f MB", bytes / 1048576.0);
    }

    public static String describe(Context c, MapEntry e) {
        if (e == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(L10n.t(c, "map_code", "Map code")).append(": ").append(e.code).append('\n');
        sb.append(L10n.t(c, "map_dir", "Folder")).append(": ")
                .append(e.dir == null ? "-" : e.dir.getAbsolutePath()).append('\n');
        sb.append(L10n.t(c, "map_files", "Files")).append(": ").append(e.files)
                .append("  ").append(human(e.bytes)).append('\n');
        sb.append(L10n.t(c, "map_state", "State")).append(": ").append(e.state(c)).append('\n');
        sb.append(L10n.t(c, "map_codesfile", "Code list")).append(": ").append(CODES).append('\n');
        return sb.toString();
    }
}
