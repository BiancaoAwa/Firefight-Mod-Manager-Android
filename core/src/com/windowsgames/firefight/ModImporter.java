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
 * Mod library and the flat Mod/ folder the game reads.
 *
 * Layout under <external files>/:
 *   Mod/                     raw files of the one active mod, no sub-folder
 *   Mod-disabled/<id>/       installed mods that are not active
 *   Mod-disabled/.ffm-enabled   id of the active mod (kept out of Mod/)
 *   Mod-disabled/.backup/<id>/<timestamp>/
 *   Mod-disabled/installed.json
 *
 * Not implemented: rollback to a backup, duplicate detection between mods, dependency checks.
 */

public final class ModImporter {

    public static final String META = ".ffm-meta";
    public static final String BACKUP_DIR = ".backup";
    public static final String INDEX = "installed.json";

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

    public static final String DISABLED_DIR = "Mod-disabled";

    private static final String STATE = ".ffm-enabled";

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

    public static String enabledId(Context c) {
        File root = disabledRoot(c);
        if (root == null) {
            return "";
        }
        File f = new File(root, STATE);
        if (!f.isFile()) {
            return "";
        }
        try {
            return readText(new FileInputStream(f)).trim();
        } catch (Throwable t) {
            return "";
        }
    }

    private static void setEnabledId(Context c, String id) {
        File root = disabledRoot(c);
        if (root == null) {
            return;
        }
        File f = new File(root, STATE);
        if (id == null || id.length() == 0) {
            f.delete();
            return;
        }
        try {
            OutputStream out = new FileOutputStream(f);
            out.write(id.getBytes("UTF-8"));
            out.close();
        } catch (Throwable ignored) {
        }
    }

    private static boolean hasData(File dir) {
        if (dir == null || !dir.isDirectory()) {
            return false;
        }
        File[] kids = dir.listFiles();
        if (kids == null) {
            return false;
        }
        for (int i = 0; i < kids.length; i++) {
            if (!kids[i].getName().startsWith(".")) {
                return true;
            }
        }
        return false;
    }

    private static boolean moveData(File src, File dst) {
        if (src == null || !src.isDirectory()) {
            return false;
        }
        if (!dst.isDirectory() && !dst.mkdirs()) {
            return false;
        }
        File[] kids = src.listFiles();
        if (kids == null) {
            return true;
        }
        for (int i = 0; i < kids.length; i++) {
            File k = kids[i];
            if (k.getName().startsWith(".")) {
                continue;
            }
            File d = new File(dst, k.getName());
            if (d.exists()) {
                deleteTree(d);
            }
            if (k.renameTo(d)) {
                continue;
            }
            try {
                if (k.isDirectory()) {
                    if (!d.mkdirs() && !d.isDirectory()) {
                        return false;
                    }
                    copyTree(k, d);
                } else {
                    copyFile(k, d);
                }
            } catch (Throwable t) {
                return false;
            }
            deleteTree(k);
        }
        return true;
    }

    public static class Slot {
        public File dir;

        public boolean enabled;
        public String id = "";

        public String name = "";
        public long bytes = 0;
        public int files = 0;

        public String display(Context c) {
            if (name != null && name.length() > 0) {
                return name;
            }
            return L10n.t(c, "mm_name_na", "NA");
        }

        public String sizeText() {
            return ModImporter.mb(bytes);
        }
    }

    public static List<Slot> listSlots(Context c) {
        List<Slot> out = new ArrayList<Slot>();
        File active = modRoot(c);
        if (hasData(active)) {
            Slot s = new Slot();
            s.dir = active;
            s.enabled = true;
            s.id = enabledId(c);
            if (s.id.length() == 0) {
                s.id = "Mod";
            }
            s.name = readName(active);
            s.files = countFiles(active);
            s.bytes = dirSize(active);
            out.add(s);
        }
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
            if (f.getName().startsWith(".") || !hasData(f)) {
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

    public static String mb(long bytes) {
        return String.format(Locale.US, "%.1f MB", Double.valueOf(bytes / 1048576.0));
    }

    public static boolean setEnabled(Context c, Slot s, boolean on) {
        if (s == null || s.enabled == on) {
            return s != null;
        }
        File lib = disabledRoot(c);
        File active = modRoot(c);
        if (lib == null || active == null) {
            return false;
        }
        try {
            String id = s.id == null || s.id.length() == 0 ? "mod" : s.id;
            if (on) {
                String cur = enabledId(c);
                if (cur.length() > 0 && !cur.equals(id)) {
                    moveData(active, new File(lib, cur));
                }
                if (!moveData(s.dir, active)) {
                    return false;
                }
                setEnabledId(c, id);
                s.dir = active;
                s.enabled = true;
                return true;
            }
            if (!moveData(active, new File(lib, id))) {
                return false;
            }
            setEnabledId(c, "");
            s.dir = new File(lib, id);
            s.enabled = false;
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

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

    public static boolean deleteSlot(Context c, Slot s) {
        if (s == null || s.dir == null) {
            return false;
        }
        boolean ok = deleteTree(s.dir);
        if (s.enabled) {
            setEnabledId(c, "");
        }
        LogStore.event(c, "delete mod " + (s.id == null ? "?" : s.id) + " -> " + ok);
        return ok;
    }

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
                    return null;
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
