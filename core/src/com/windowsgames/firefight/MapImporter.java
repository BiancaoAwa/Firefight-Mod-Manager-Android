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
 * Custom map folder and the code table the game reads.
 *
 * A map lives in <external files>/Custom Maps/<CODE>/, and the game only loads a code that is
 * listed in CustomMapCodeNames.txt. The line order of that file is the map order in the game,
 * and removing a line disables the map without deleting it. The manager keeps the same order in
 * Custom Maps/.ffm-map-order and rebuilds the code table from it, so maps without a list entry
 * keep their place in the list. At most MAX_LOAD maps can be listed at once.
 *
 * Not implemented: editing a map, uploading a map, checking that all files of a map belong
 * together.
 */

public final class MapImporter {

    public static final String DIR = "Custom Maps";
    public static final String CODES = "CustomMapCodeNames.txt";
    /** Display order of the map list, one code per line; keeps maps without a list entry in place. */
    public static final String ORDER = ".ffm-map-order";
    /** The game loads at most this many listed maps. */
    public static final int MAX_LOAD = 27;

    private static final Pattern CODE = Pattern.compile("^[A-Za-z0-9]{4}$");

    private MapImporter() {
    }

    public static class MapEntry {
        public String code = "";
        public File dir;
        public int files;
        public long bytes;

        public boolean present;

        public boolean listed;

        public String name = "";

        public String label() {
            return code;
        }

        public String display(Context c) {
            if (name != null && name.length() > 0) {
                return name;
            }
            return code;
        }

        public String sizeText() {
            return human(bytes);
        }

        public String state(Context c) {
            if (present && listed) {
                return L10n.t(c, "mm_state_enabled", "enabled");
            }
            if (present) {
                return L10n.t(c, "mm_state_disabled", "disabled");
            }
            return L10n.t(c, "map_state_missing", "folder missing");
        }
    }

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

    /**
     * All entries: folders first in display order (.ffm-map-order, then the code table,
     * then the remaining folders by name), followed by listed codes without a folder.
     */
    public static List<MapEntry> listMaps(Context c) {
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
        List<String> listed = readCodes(c);
        List<MapEntry> out = new ArrayList<MapEntry>();
        List<String> order = displayOrder(c, new ArrayList<String>(byCode.keySet()), listed);
        for (int i = 0; i < order.size(); i++) {
            String code = order.get(i);
            MapEntry e = byCode.remove(code);
            if (e == null) {
                continue;
            }
            e.listed = listed.contains(code);
            out.add(e);
        }
        for (int i = 0; i < listed.size(); i++) {
            if (byCode.remove(listed.get(i)) != null) {
                continue;
            }
            MapEntry e = new MapEntry();
            e.code = listed.get(i);
            e.listed = true;
            out.add(e);
        }
        return out;
    }

    /**
     * Codes of folders that exist, in display order. The saved order wins, codes it does
     * not mention follow the code table and then the alphabetical folder order.
     */
    private static List<String> displayOrder(Context c, List<String> present, List<String> listed) {
        return mergeOrder(readOrder(c), listed, present);
    }

    /**
     * Codes of folders that exist, in display order: saved order first, then the code
     * table, then the remaining folder order. Duplicates and absent folders drop out.
     */
    public static List<String> mergeOrder(List<String> saved, List<String> listed, List<String> present) {
        List<String> out = new ArrayList<String>();
        addPresent(saved, present, out);
        addPresent(listed, present, out);
        addPresent(present, present, out);
        return out;
    }

    private static void addPresent(List<String> src, List<String> present, List<String> out) {
        for (int i = 0; i < src.size(); i++) {
            String s = src.get(i);
            if (present.contains(s) && !out.contains(s)) {
                out.add(s);
            }
        }
    }

    /** Codes of folders that exist, in display order. */
    public static List<String> presentOrder(Context c) {
        List<MapEntry> all = listMaps(c);
        List<String> out = new ArrayList<String>();
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).present) {
                out.add(all.get(i).code.toUpperCase(Locale.US));
            }
        }
        return out;
    }

    public static List<String> readOrder(Context c) {
        List<String> out = new ArrayList<String>();
        File root = mapsRoot(c);
        if (root == null) {
            return out;
        }
        File f = new File(root, ORDER);
        if (!f.isFile()) {
            return out;
        }
        try {
            String[] lines = readText(new FileInputStream(f)).split("\n");
            for (int i = 0; i < lines.length; i++) {
                String s = lines[i].replace("\r", "").trim().toUpperCase(Locale.US);
                if (CODE.matcher(s).matches() && !out.contains(s)) {
                    out.add(s);
                }
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    private static void writeOrder(Context c, List<String> codes) {
        File root = mapsRoot(c);
        if (root == null) {
            return;
        }
        try {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < codes.size(); i++) {
                sb.append(codes.get(i)).append('\n');
            }
            writeText(new File(root, ORDER), sb.toString());
        } catch (Throwable ignored) {
        }
    }

    /**
     * Writes the display order. The code table is left alone: the display order and the
     * load order are independent, so reordering the list must not renumber the maps.
     */
    private static void applyOrder(Context c, List<String> codes) {
        writeOrder(c, codes);
    }

    /** Moves one element inside a list; false when the move is a no-op or out of range. */
    public static boolean moveIn(List<String> list, int from, int to) {
        if (list == null || from < 0 || to < 0 || from >= list.size() || to >= list.size() || from == to) {
            return false;
        }
        list.add(to, list.remove(from));
        return true;
    }

    /** Moves one map delta places in the display order, negative is up. */
    public static boolean moveMap(Context c, String code, int delta) {
        List<String> order = presentOrder(c);
        int at = order.indexOf(upper(code));
        if (!moveIn(order, at, at + delta)) {
            return false;
        }
        applyOrder(c, order);
        return true;
    }

    /** Moves one map to a 1-based display position. */
    public static boolean setMapIndex(Context c, String code, int oneBased) {
        List<String> order = presentOrder(c);
        int at = order.indexOf(upper(code));
        if (at < 0) {
            return false;
        }
        int to = oneBased - 1;
        if (to < 0) {
            to = 0;
        }
        if (to >= order.size()) {
            to = order.size() - 1;
        }
        if (to == at) {
            return false;
        }
        if (!moveIn(order, at, to)) {
            return false;
        }
        applyOrder(c, order);
        return true;
    }

    /**
     * Moves one listed map to a 1-based position among the listed maps. The code table
     * carries that order; the display order is frozen first so it stays untouched.
     */
    public static boolean setMapLoadIndex(Context c, String code, int oneBased) {
        return permuteLoad(c, code, oneBased - 1);
    }

    /**
     * Moves one listed map delta places among the listed maps, negative is up. Maps
     * without a code-table line and codes without a folder keep their positions.
     */
    public static boolean moveMapLoad(Context c, String code, int delta) {
        if (delta == 0) {
            return false;
        }
        List<String> seq = listedPresent(c);
        int at = seq.indexOf(upper(code));
        if (at < 0) {
            return false;
        }
        return permuteLoad(c, code, at + (delta < 0 ? -1 : 1));
    }

    /** 1-based position among the listed maps, -1 when the map is not listed. */
    public static int loadIndexOf(Context c, String code) {
        int at = listedPresent(c).indexOf(upper(code));
        return at < 0 ? -1 : at + 1;
    }

    /** Number of listed maps that have a folder. */
    public static int loadCount(Context c) {
        return listedPresent(c).size();
    }

    /** Codes of the code table that also have a folder, in code-table order. */
    public static List<String> listedPresent(Context c) {
        List<String> all = readCodes(c);
        List<String> present = presentOrder(c);
        List<String> out = new ArrayList<String>();
        for (int i = 0; i < all.size(); i++) {
            if (present.contains(all.get(i)) && !out.contains(all.get(i))) {
                out.add(all.get(i));
            }
        }
        return out;
    }

    /**
     * Code-table order after moving one listed map to a 0-based position among the listed
     * maps. Listed codes keep the slots they occupy, codes without a folder keep their
     * lines, and the move shifts the codes in between. Null when nothing moves.
     */
    public static List<String> permuteList(List<String> all, List<String> present, String code, int to) {
        List<Integer> slots = new ArrayList<Integer>();
        List<String> seq = new ArrayList<String>();
        for (int i = 0; i < all.size(); i++) {
            String s = all.get(i);
            if (present.contains(s) && !seq.contains(s)) {
                slots.add(Integer.valueOf(i));
                seq.add(s);
            }
        }
        int at = seq.indexOf(upper(code));
        if (at < 0) {
            return null;
        }
        if (to < 0) {
            to = 0;
        }
        if (to >= seq.size()) {
            to = seq.size() - 1;
        }
        if (to == at || !moveIn(seq, at, to)) {
            return null;
        }
        List<String> out = new ArrayList<String>(all);
        for (int i = 0; i < slots.size(); i++) {
            out.set(slots.get(i).intValue(), seq.get(i));
        }
        return out;
    }

    /**
     * Moves one listed map to a 0-based position among the listed maps. Only the code-table
     * lines move; the display order is written out unchanged first, so the two orders stay
     * independent.
     */
    private static boolean permuteLoad(Context c, String code, int to) {
        List<String> next = permuteList(readCodes(c), presentOrder(c), code, to);
        if (next == null) {
            return false;
        }
        writeOrder(c, presentOrder(c));
        return writeCodes(c, next);
    }

    private static String upper(String code) {
        return code == null ? "" : code.toUpperCase(Locale.US);
    }

    /**
     * Rewrites the code table with its code lines in the given order. Lines that carry no
     * code keep their text and their relative position, and a code line keeps its original
     * text. The order of these lines is the load order inside the game.
     */
    public static boolean writeCodes(Context c, List<String> want) {
        File f = codesFile(c);
        if (f == null) {
            return false;
        }
        try {
            String text = f.isFile() ? readText(new FileInputStream(f)) : "";
            String[] lines = text.split("\n", -1);
            List<String> others = new ArrayList<String>();
            Map<String, String> codeLine = new LinkedHashMap<String, String>();
            for (int i = 0; i < lines.length; i++) {
                String raw = lines[i].replace("\r", "");
                String code = firstCode(raw);
                if (code == null) {
                    others.add(raw);
                } else if (!codeLine.containsKey(code)) {
                    codeLine.put(code, raw);
                }
            }
            List<String> out = new ArrayList<String>();
            int oi = 0;
            for (int i = 0; i < want.size(); i++) {
                String raw = codeLine.get(want.get(i));
                out.add(raw == null ? want.get(i) : raw);
                if (oi < others.size()) {
                    out.add(others.get(oi));
                    oi++;
                }
            }
            while (oi < others.size()) {
                out.add(others.get(oi));
                oi++;
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < out.size(); i++) {
                sb.append(out.get(i)).append('\n');
            }
            writeText(f, sb.toString());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

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

    /** Code of one code-table line, or null when the line carries none. */
    public static String firstCode(String line) {
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
            if (loadCount(c) >= MAX_LOAD) {
                return false;
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

    /**
     * Preview image of a map folder: <CODE>-map.jpg first, then any image whose name
     * contains "map", then any image. Null when the folder has no image.
     */
    public static File mapImage(File dir, String code) {
        if (dir == null || !dir.isDirectory()) {
            return null;
        }
        String up = upper(code);
        if (up.length() > 0) {
            File f = new File(dir, up + "-map.jpg");
            if (f.isFile()) {
                return f;
            }
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
        File any = null;
        File named = null;
        File prefixed = null;
        for (int i = 0; i < kids.length; i++) {
            File k = kids[i];
            if (!k.isFile()) {
                continue;
            }
            String n = k.getName().toLowerCase(Locale.US);
            if (n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png")) {
                if (any == null) {
                    any = k;
                }
                if (n.contains("map")) {
                    if (named == null) {
                        named = k;
                    }
                    if (up.length() > 0 && n.startsWith(up.toLowerCase(Locale.US))) {
                        prefixed = k;
                        break;
                    }
                }
            }
        }
        if (prefixed != null) {
            return prefixed;
        }
        return named != null ? named : any;
    }

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
            boolean on = registerCode(c, code);
            LogStore.event(c, "map import " + zipName + " -> " + code + (on ? "" : " (load list full)"));
            return "ok:" + code;
        } catch (Throwable t) {
            deleteTree(tmp);
            return "fail";
        }
    }

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
