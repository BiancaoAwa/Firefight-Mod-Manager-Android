package com.windowsgames.firefight;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 模组包导入器（需求⑤）。
 *
 * 支持的模组包形态：
 *   A) APK 内置：assets/Mod/*.zip                —— zip 内含 mod.json 与 payload 文件
 *   B) APK 内置：assets/Mod/<id>/mod.json        —— 目录形态（含任意子目录/文件）
 *   C) 外部文件：文件选择器拿到的 .zip / .ffmod
 *
 * 安装布局（<modRoot> 通常 = files/Mod）：
 *   <modRoot>/<id>/...            模组内容
 *   <modRoot>/<id>/.ffm-meta      本管理器写入的安装元信息（导入时间/来源/文件数）
 *   <modRoot>/.backup/<id>/<ts>/  覆盖安装前的自动备份（用于回滚）
 *   <modRoot>/installed.json     供人/游戏读取的索引快照（派生文件，不参与解析）
 */
public final class ModImporter {

    public static final String META = ".ffm-meta";
    public static final String BACKUP_DIR = ".backup";
    public static final String INDEX = "installed.json";

    /** 一个候选/已安装模组的描述。 */
    public static class ModEntry {
        public String id = "";
        public String name = "";
        public String version = "";
        public String gameVersion = "";
        public String type = "";
        public String description = "";
        public String source = "";
        public int files = 0;
        public String installedTime = "";
        public boolean installed = false;

        public String label() {
            String n = (name == null || name.length() == 0) ? id : name;
            if (version != null && version.length() > 0) {
                return n + "  (v" + version + ")";
            }
            return n;
        }
    }

    private ModImporter() {
    }

    // ---------------------------------------------------------------- 工具

    public static String now() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
    }

    private static String ts() {
        return new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
    }

    public static String safeName(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '/' || ch == '\\' || ch == ':' || ch == '*' || ch == '?' || ch == '"'
                    || ch == '<' || ch == '>' || ch == '|' || ch < ' ') {
                sb.append('_');
            } else {
                sb.append(ch);
            }
        }
        String out = sb.toString().trim();
        while (out.startsWith(".")) {
            out = out.substring(1);
        }
        return out;
    }

    /** 极简 JSON 取值：在 text 里找 "key" 后第一个字符串或数字。够用即可。 */
    public static String jsonGet(String text, String key) {
        if (text == null || key == null) {
            return "";
        }
        int p = text.indexOf("\"" + key + "\"");
        if (p < 0) {
            return "";
        }
        int c = text.indexOf(':', p);
        if (c < 0) {
            return "";
        }
        int i = c + 1;
        while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
            i++;
        }
        if (i >= text.length()) {
            return "";
        }
        if (text.charAt(i) == '"') {
            int j = text.indexOf('"', i + 1);
            if (j < 0) {
                return "";
            }
            return text.substring(i + 1, j);
        }
        int j = i;
        while (j < text.length() && ",}\n\r".indexOf(text.charAt(j)) < 0) {
            j++;
        }
        return text.substring(i, j).trim();
    }

    private static String readText(InputStream in) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                bos.write(buf, 0, n);
            }
            in.close();
            return new String(bos.toByteArray(), "UTF-8");
        } catch (Throwable t) {
            return "";
        }
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[65536];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
        }
    }

    private static void copyFile(File src, File dst) throws IOException {
        FileInputStream in = new FileInputStream(src);
        try {
            FileOutputStream out = new FileOutputStream(dst);
            try {
                copy(in, out);
            } finally {
                out.close();
            }
        } finally {
            in.close();
        }
    }

    private static boolean deleteTree(File f) {
        if (f == null || !f.exists()) {
            return false;
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
        if (dir == null || !dir.isDirectory()) {
            return 0;
        }
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

    // ------------------------------------------------------- 元信息读写

    public static ModEntry readMeta(File modDir) {
        ModEntry e = new ModEntry();
        e.id = modDir.getName();
        e.name = e.id;
        e.installed = true;
        File m = new File(modDir, META);
        if (m.isFile()) {
            try {
                FileInputStream in = new FileInputStream(m);
                String text = readText(in);
                String[] lines = text.split("\n");
                for (int i = 0; i < lines.length; i++) {
                    String line = lines[i].trim();
                    int eq = line.indexOf('=');
                    if (eq <= 0) {
                        continue;
                    }
                    String k = line.substring(0, eq);
                    String v = line.substring(eq + 1);
                    if ("id".equals(k)) {
                        e.id = v;
                    } else if ("name".equals(k)) {
                        e.name = v;
                    } else if ("version".equals(k)) {
                        e.version = v;
                    } else if ("gameVersion".equals(k)) {
                        e.gameVersion = v;
                    } else if ("type".equals(k)) {
                        e.type = v;
                    } else if ("description".equals(k)) {
                        e.description = v;
                    } else if ("source".equals(k)) {
                        e.source = v;
                    } else if ("time".equals(k)) {
                        e.installedTime = v;
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        e.files = countFiles(modDir);
        return e;
    }

    private static void writeMeta(File modDir, ModEntry e) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("id=").append(e.id).append('\n');
            sb.append("name=").append(e.name).append('\n');
            sb.append("version=").append(e.version).append('\n');
            sb.append("gameVersion=").append(e.gameVersion).append('\n');
            sb.append("type=").append(e.type).append('\n');
            sb.append("description=").append(e.description).append('\n');
            sb.append("source=").append(e.source).append('\n');
            sb.append("time=").append(e.installedTime).append('\n');
            sb.append("files=").append(e.files).append('\n');
            FileOutputStream out = new FileOutputStream(new File(modDir, META), false);
            out.write(sb.toString().getBytes("UTF-8"));
            out.close();
        } catch (Throwable ignored) {
        }
    }

    // ------------------------------------------------------- 列表

    public static File modRoot(Context c) {
        File base = MyGame.rootOfStatic(c);
        if (base == null) {
            return null;
        }
        File root = new File(base, "Mod");
        if (!root.isDirectory() && !root.mkdirs()) {
            return null;
        }
        return root;
    }

    /** 已安装模组（按名称排序，跳过 .backup 等隐藏目录）。 */
    public static List<ModEntry> listInstalled(File modRoot) {
        List<ModEntry> out = new ArrayList<ModEntry>();
        if (modRoot == null || !modRoot.isDirectory()) {
            return out;
        }
        File[] kids = modRoot.listFiles();
        if (kids == null) {
            return out;
        }
        Arrays.sort(kids, new Comparator<File>() {
            public int compare(File a, File b) {
                return a.getName().compareToIgnoreCase(b.getName());
            }
        });
        for (int i = 0; i < kids.length; i++) {
            File f = kids[i];
            if (f.isDirectory() && !f.getName().startsWith(".")) {
                out.add(readMeta(f));
            }
        }
        return out;
    }


    // ------------------------------------------------- 启用 / 禁用（第 3 轮）

    /**
     * 已禁用模组的存放目录，与 Mod/ 同级。游戏只读 &lt;files&gt;/Mod，所以「禁用」
     * 只能把模组目录搬出去，不能只改目录名（改了名字游戏可能照样扫得到）。
     */
    public static final String DISABLED_DIR = "Mod-disabled";

    /** 最近一次 installZip 装出来的目录名，给界面提示用。 */
    public static String lastInstalledId = "";

    public static File disabledRoot(Context c) {
        File base = MyGame.rootOfStatic(c);
        if (base == null) {
            return null;
        }
        File root = new File(base, DISABLED_DIR);
        if (!root.isDirectory() && !root.mkdirs()) {
            return null;
        }
        return root;
    }

    /** 界面上的一个模组槽位。 */
    public static class Slot {
        public File dir;
        /** true = 在 Mod/ 里，游戏会加载。 */
        public boolean enabled;
        public String id = "";
        /** mod.txt / mod.json 里的名字，取不到为空串。 */
        public String name = "";
        public long bytes = 0;
        public int files = 0;

        /** 规格②：名字读 mod.txt 的 &lt;name&gt;，取不到显示 NA。 */
        public String display(Context c) {
            if (name != null && name.length() > 0) {
                return name;
            }
            return L10n.t(c, "mm_name_na", "NA");
        }

        /** 规格②：模组文件大小，单位 MB。 */
        public String sizeText() {
            return ModImporter.mb(bytes);
        }
    }

    /** 磁盘上全部模组：启用的排前面，禁用的排后面，各自按目录名排序。 */
    public static List<Slot> listSlots(Context c) {
        List<Slot> out = new ArrayList<Slot>();
        addSlots(out, modRoot(c), true);
        addSlots(out, disabledRoot(c), false);
        return out;
    }

    private static void addSlots(List<Slot> out, File root, final boolean enabled) {
        if (root == null || !root.isDirectory()) {
            return;
        }
        File[] kids = root.listFiles();
        if (kids == null) {
            return;
        }
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
            Slot s = new Slot();
            s.dir = f;
            s.enabled = enabled;
            s.id = f.getName();
            s.name = readName(f);
            s.files = countFiles(f);
            s.bytes = dirSize(f);
            out.add(s);
        }
    }

    /** 规格②：名字 = mod.txt 的 &lt;name&gt; → mod.json 的 name → ""（界面显示 NA）。 */
    public static String readName(File dir) {
        if (dir == null || !dir.isDirectory()) {
            return "";
        }
        File mt = findFile(dir, "mod.txt", 2);
        if (mt != null) {
            try {
                String n = tagGet(readText(new FileInputStream(mt)), "name");
                if (n != null && n.length() > 0) {
                    return n;
                }
            } catch (Throwable ignored) {
            }
        }
        File mj = findFile(dir, "mod.json", 2);
        if (mj != null) {
            try {
                String n = jsonGet(readText(new FileInputStream(mj)), "name");
                if (n != null && n.length() > 0) {
                    return n;
                }
            } catch (Throwable ignored) {
            }
        }
        return "";
    }

    /** 在 dir 及其子目录（最多 depth 层）里找第一个叫 name 的文件。 */
    private static File findFile(File dir, String name, int depth) {
        File f = new File(dir, name);
        if (f.isFile()) {
            return f;
        }
        if (depth <= 0) {
            return null;
        }
        File[] kids = dir.listFiles();
        if (kids == null) {
            return null;
        }
        for (int i = 0; i < kids.length; i++) {
            File k = kids[i];
            if (k.isDirectory() && !k.getName().startsWith(".")) {
                File r = findFile(k, name, depth - 1);
                if (r != null) {
                    return r;
                }
            }
        }
        return null;
    }

    public static long dirSize(File f) {
        if (f == null) {
            return 0;
        }
        if (f.isFile()) {
            return f.length();
        }
        long n = 0;
        File[] kids = f.listFiles();
        if (kids != null) {
            for (int i = 0; i < kids.length; i++) {
                n += dirSize(kids[i]);
            }
        }
        return n;
    }

    /** 规格②：模组大小显示成 MB。 */
    public static String mb(long bytes) {
        return String.format(Locale.US, "%.1f MB", Double.valueOf(bytes / 1048576.0));
    }

    /**
     * 启用 / 禁用：把模组目录在 Mod/ 与 Mod-disabled/ 之间搬。
     * 先 rename（同一分区是原子操作），失败再退回「复制 + 删源」。
     */
    public static boolean setEnabled(Context c, Slot s, boolean on) {
        if (s == null || s.dir == null || !s.dir.isDirectory()) {
            return false;
        }
        File dstRoot = on ? modRoot(c) : disabledRoot(c);
        if (dstRoot == null) {
            return false;
        }
        String id = s.id == null || s.id.length() == 0 ? s.dir.getName() : s.id;
        File dst = new File(dstRoot, id);
        if (s.dir.getAbsolutePath().equals(dst.getAbsolutePath())) {
            s.enabled = on;
            return true;
        }
        try {
            if (dst.exists()) {
                deleteTree(dst);
            }
            if (s.dir.renameTo(dst)) {
                s.dir = dst;
                s.enabled = on;
                return true;
            }
            if (!dst.mkdirs() && !dst.isDirectory()) {
                return false;
            }
            copyTree(s.dir, dst);
            deleteTree(s.dir);
            s.dir = dst;
            s.enabled = on;
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 规格②：导入模组 —— 先落到 Mod-disabled/，由用户点「启用」再搬进 Mod/。 */
    public static String importMod(Context c, InputStream in, String suggestedName) {
        File root = disabledRoot(c);
        if (root == null) {
            return "fail";
        }
        String r = installZip(in, root, suggestedName);
        if ("ok".equals(r)) {
            LogStore.event(c, "import mod -> " + DISABLED_DIR + "/" + lastInstalledId);
        }
        return r;
    }

    /** 规格②：删除一个模组槽位（目录连同内容）。 */
    public static boolean deleteSlot(Context c, Slot s) {
        if (s == null || s.dir == null) {
            return false;
        }
        boolean ok = deleteTree(s.dir);
        LogStore.event(c, "delete mod " + (s.id == null ? "?" : s.id) + " -> " + ok);
        return ok;
    }

    // ------------------------------------------------------- 备份 / 回滚

    public static File backupRoot(File modRoot, String id) {
        return new File(new File(modRoot, BACKUP_DIR), id);
    }


    private static boolean backup(File modRoot, String id) {
        File cur = new File(modRoot, id);
        if (!cur.isDirectory()) {
            return true;
        }
        File dst = new File(backupRoot(modRoot, id), ts() + "_" + System.currentTimeMillis() % 1000);
        if (!dst.mkdirs()) {
            return false;
        }
        try {
            copyTree(cur, dst);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static void copyTree(File src, File dst) throws IOException {
        File[] kids = src.listFiles();
        if (kids == null) {
            return;
        }
        for (int i = 0; i < kids.length; i++) {
            File k = kids[i];
            File d = new File(dst, k.getName());
            if (k.isDirectory()) {
                if (!d.mkdirs() && !d.isDirectory()) {
                    throw new IOException("mkdir " + d);
                }
                copyTree(k, d);
            } else {
                copyFile(k, d);
            }
        }
    }



    // ------------------------------------------------------- 安装


    /** 安装外部 zip（文件选择器/分享进来的包）。 */
    public static String installZip(InputStream in, File modRoot, String suggestedName) {
        if (modRoot == null) {
            return "fail";
        }
        File tmp = new File(modRoot, ".tmp_" + System.currentTimeMillis());
        try {
            if (!tmp.mkdirs() && !tmp.isDirectory()) {
                return "fail";
            }
            if (extractZip(in, tmp, true) <= 0) {
                deleteTree(tmp);
                return "fail";
            }
            ModEntry e = readModJson(tmp);
            if (e.id == null || e.id.length() == 0) {
                e.id = safeName(suggestedName == null ? "mod" : suggestedName);
                if (e.id.endsWith(".zip") || e.id.endsWith(".ffmod")) {
                    e.id = e.id.substring(0, e.id.lastIndexOf('.'));
                }
            }
            if (e.id.length() == 0) {
                e.id = "mod_" + System.currentTimeMillis();
            }
            e.id = safeName(e.id);
            backup(modRoot, e.id);
            File dst = new File(modRoot, e.id);
            if (dst.exists()) {
                deleteTree(dst);
            }
            if (!tmp.renameTo(dst) && !dst.isDirectory()) {
                if (!dst.mkdirs()) {
                    deleteTree(tmp);
                    return "fail";
                }
                copyTree(tmp, dst);
                deleteTree(tmp);
            }
            finishInstall(e, dst, modRoot, "file:" + suggestedName);
            lastInstalledId = e.id;
            return "ok";
        } catch (Throwable t) {
            deleteTree(tmp);
            return "fail";
        }
    }

    private static ModEntry readModJson(File dir) {
        ModEntry e = new ModEntry();
        File j = new File(dir, "mod.json");
        if (j.isFile()) {
            try {
                FileInputStream in = new FileInputStream(j);
                String text = readText(in);
                e.id = safeName(jsonGet(text, "id"));
                e.name = jsonGet(text, "name");
                e.version = jsonGet(text, "version");
                e.gameVersion = jsonGet(text, "gameVersion");
                e.type = jsonGet(text, "type");
                e.description = jsonGet(text, "description");
            } catch (Throwable ignored) {
            }
        }
        // 需求⑥：真正的 Firefight 模组用 mod.txt（HTML 风格标签），没有 mod.json。
        // 游戏认定「Mod/<id>/mod.txt」就是一个模组，所以名字优先取 <name>。
        if (e.id == null || e.id.length() == 0 || e.name == null || e.name.length() == 0) {
            File mt = new File(dir, "mod.txt");
            if (mt.isFile()) {
                try {
                    String txt = readText(new FileInputStream(mt));
                    String nm = tagGet(txt, "name");
                    String au = tagGet(txt, "author");
                    if (e.name == null || e.name.length() == 0) {
                        e.name = nm;
                    }
                    if (e.id == null || e.id.length() == 0) {
                        e.id = safeName(nm);
                    }
                    if (e.description == null || e.description.length() == 0) {
                        e.description = au == null || au.length() == 0 ? "" : ("by " + au);
                    }
                    if (e.type == null || e.type.length() == 0) {
                        e.type = "mod.txt";
                    }
                } catch (Throwable ignored) {
                }
            }
        }
        if (e.name == null || e.name.length() == 0) {
            e.name = e.id;
        }
        return e;
    }

    /** 取 mod.txt 里 &lt;tag&gt;值&lt;/tag&gt; 的第一个值。 */
    public static String tagGet(String text, String tag) {
        if (text == null || tag == null) {
            return "";
        }
        try {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                    "<" + tag + "\\s*>\\s*([^<]*)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(text);
            if (m.find()) {
                return m.group(1).trim();
            }
        } catch (Throwable ignored) {
        }
        return "";
    }

    private static void finishInstall(ModEntry e, File dst, File modRoot, String source) {
        e.source = source;
        e.installedTime = now();
        e.installed = true;
        e.files = countFiles(dst);
        writeMeta(dst, e);
        writeIndex(modRoot);
    }


    /**
     * 解压 zip 到 dst。stripRoot=true 时若压缩包只有一个顶层目录则剥掉它。
     * 返回写出的文件数；防 zip-slip。
     */
    public static int extractZip(InputStream raw, File dst, boolean stripRoot) {
        int count = 0;
        try {
            byte[] all = null;
            if (stripRoot) {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                copy(raw, bos);
                raw.close();
                all = bos.toByteArray();
                String root = commonRoot(all);
                if (root != null) {
                    all = stripPrefix(all, root);
                }
            }
            ZipInputStream zin = new ZipInputStream(
                    all != null ? new java.io.ByteArrayInputStream(all) : raw);
            ZipEntry ze;
            while ((ze = zin.getNextEntry()) != null) {
                String name = ze.getName().replace('\\', '/');
                if (name.startsWith("/")) {
                    name = name.substring(1);
                }
                if (name.length() == 0 || name.contains("..")) {
                    zin.closeEntry();
                    continue;
                }
                File out = new File(dst, name);
                String canDst = dst.getCanonicalPath();
                String canOut = out.getCanonicalPath();
                if (!canOut.startsWith(canDst)) {
                    zin.closeEntry();
                    continue;
                }
                if (ze.isDirectory()) {
                    out.mkdirs();
                } else {
                    File parent = out.getParentFile();
                    if (parent != null && !parent.isDirectory()) {
                        parent.mkdirs();
                    }
                    FileOutputStream fos = new FileOutputStream(out);
                    try {
                        copy(zin, fos);
                    } finally {
                        fos.close();
                    }
                    count++;
                }
                zin.closeEntry();
            }
            zin.close();
        } catch (Throwable ignored) {
        }
        return count;
    }

    /** 读出 zip 数据的「唯一顶层目录」（如 "example-mod/"），没有则返回 null。 */
    private static String commonRoot(byte[] zip) {
        try {
            java.util.zip.ZipInputStream zin =
                    new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(zip));
            String root = null;
            ZipEntry ze;
            while ((ze = zin.getNextEntry()) != null) {
                String n = ze.getName().replace('\\', '/');
                int slash = n.indexOf('/');
                zin.closeEntry();
                if (slash <= 0) {
                    return null; // 有文件直接在根上
                }
                String head = n.substring(0, slash);
                if (root == null) {
                    root = head;
                } else if (!root.equals(head)) {
                    return null;
                }
            }
            zin.close();
            return root;
        } catch (Throwable t) {
            return null;
        }
    }

    /** 按 zip 原始字节流剥掉前缀目录（保留 zip 结构与 CRCs）。 */
    private static byte[] stripPrefix(byte[] zip, String prefix) throws IOException {
        File tmpIn = File.createTempFile("ffm", ".zip");
        FileOutputStream fos = new FileOutputStream(tmpIn);
        fos.write(zip);
        fos.close();
        File tmpOut = File.createTempFile("ffm", ".out");
        java.util.zip.ZipInputStream zin =
                new java.util.zip.ZipInputStream(new FileInputStream(tmpIn));
        java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(new FileOutputStream(tmpOut));
        byte[] buf = new byte[65536];
        ZipEntry ze;
        try {
            while ((ze = zin.getNextEntry()) != null) {
                String n = ze.getName().replace('\\', '/');
                if (!n.startsWith(prefix + "/")) {
                    zin.closeEntry();
                    continue;
                }
                String nn = n.substring(prefix.length() + 1);
                if (nn.length() == 0) {
                    zin.closeEntry();
                    continue;
                }
                ZipEntry out = new ZipEntry(nn);
                zos.putNextEntry(out);
                int k;
                while ((k = zin.read(buf)) > 0) {
                    zos.write(buf, 0, k);
                }
                zos.closeEntry();
                zin.closeEntry();
            }
        } finally {
            zos.close();
            zin.close();
            tmpIn.delete();
        }
        FileInputStream in = new FileInputStream(tmpOut);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        copy(in, bos);
        in.close();
        tmpOut.delete();
        return bos.toByteArray();
    }

    // ------------------------------------------------------- 索引快照

    /** 生成 installed.json（派生文件，只写不读；权威状态在 .ffm-meta 与目录本身）。 */
    public static void writeIndex(File modRoot) {
        if (modRoot == null) {
            return;
        }
        try {
            List<ModEntry> list = listInstalled(modRoot);
            StringBuilder sb = new StringBuilder();
            sb.append("{\n  \"manager\": \"Firefight Mod Manager\",\n  \"root\": \"")
                    .append(esc(modRoot.getAbsolutePath())).append("\",\n  \"updated\": \"")
                    .append(now()).append("\",\n  \"mods\": [\n");
            for (int i = 0; i < list.size(); i++) {
                ModEntry e = list.get(i);
                sb.append("    {\"id\": \"").append(esc(e.id)).append("\", \"name\": \"").append(esc(e.name))
                        .append("\", \"version\": \"").append(esc(e.version)).append("\", \"gameVersion\": \"")
                        .append(esc(e.gameVersion)).append("\", \"type\": \"").append(esc(e.type))
                        .append("\", \"files\": ").append(e.files).append(", \"source\": \"").append(esc(e.source))
                        .append("\", \"installed\": \"").append(esc(e.installedTime)).append("\"}");
                if (i + 1 < list.size()) {
                    sb.append(',');
                }
                sb.append('\n');
            }
            sb.append("  ]\n}\n");
            FileOutputStream out = new FileOutputStream(new File(modRoot, INDEX), false);
            out.write(sb.toString().getBytes("UTF-8"));
            out.close();
        } catch (Throwable ignored) {
        }
    }

    private static String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public static String describe(ModEntry e) {
        StringBuilder sb = new StringBuilder();
        sb.append("id: ").append(e.id).append('\n');
        sb.append("name: ").append(e.name).append('\n');
        sb.append("version: ").append(e.version.length() == 0 ? "-" : e.version).append('\n');
        sb.append("gameVersion: ").append(e.gameVersion.length() == 0 ? "-" : e.gameVersion).append('\n');
        sb.append("type: ").append(e.type.length() == 0 ? "-" : e.type).append('\n');
        sb.append("files: ").append(e.files).append('\n');
        sb.append("source: ").append(e.source).append('\n');
        if (e.installedTime.length() > 0) {
            sb.append("installed: ").append(e.installedTime).append('\n');
        }
        if (e.description.length() > 0) {
            sb.append("description: ").append(e.description).append('\n');
        }
        return sb.toString();
    }
}
