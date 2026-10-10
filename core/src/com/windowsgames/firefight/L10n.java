package com.windowsgames.firefight;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;

import java.util.Locale;

/**
 * Language handling for the manager. The chosen tag lives in SharedPreferences (empty = follow
 * the system) and every ffm_* string is read from the app's own resources.
 * Not implemented: per-key overrides.
 */

public final class L10n {

    private static final String PREFS = "ffm_prefs";
    private static final String KEY_LANG = "lang";

    public static final String[] TAGS = {
            "", "en", "zh-rCN", "zh-rTW", "ja", "ko", "ru", "de", "fr", "es", "it",
            "pt-rBR", "ar", "tr", "pl", "nl", "uk", "vi", "th", "hi", "id", "ms",
            "cs", "el", "he", "fa", "bg", "hu", "ro", "sv",
    };

    public static final String[] LABELS = {
            "\u8DDF\u968F\u7CFB\u7EDF / System",
            "English",
            "\u7B80\u4F53\u4E2D\u6587",
            "\u7E41\u9AD4\u4E2D\u6587",
            "\u65E5\u672C\u8A9E",
            "\uD55C\uAD6D\uC5B4",
            "\u0420\u0443\u0441\u0441\u043A\u0438\u0439",
            "Deutsch",
            "Fran\u00E7ais",
            "Espa\u00F1ol",
            "Italiano",
            "Portugu\u00EAs (BR)",
            "\u0627\u0644\u0639\u0631\u0628\u064A\u0629",
            "T\u00FCrk\u00E7e",
            "Polski",
            "Nederlands",
            "\u0423\u043A\u0440\u0430\u0457\u043D\u0441\u044C\u043A\u0430",
            "Ti\u1EBFng Vi\u1EC7t",
            "\u0E44\u0E17\u0E22",
            "\u0939\u093F\u0928\u094D\u0926\u0940",
            "Bahasa Indonesia",
            "Bahasa Melayu",
            "\u010Ce\u0161tina",
            "\u0395\u03BB\u03BB\u03B7\u03BD\u03B9\u03BA\u03AC",
            "\u05E2\u05D1\u05E8\u05D9\u05EA",
            "\u0641\u0627\u0631\u0633\u06CC",
            "\u0411\u044A\u043B\u0433\u0430\u0440\u0441\u043A\u0438",
            "Magyar",
            "Rom\u00E2n\u0103",
            "Svenska",
    };

    private static final String RES_PKG = "com.windowsgames.firefight";

    private static String overrideTag = "";
    private static Context overrideCtx;
    private static String goodPkg;

    private L10n() {
    }

    public static void init(Context c) {
        if (c == null) {
            return;
        }
        try {
            SharedPreferences sp = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            overrideTag = sp.getString(KEY_LANG, "");
            if (overrideTag == null) {
                overrideTag = "";
            }
            applyOverride(c);
        } catch (Throwable ignored) {
        }
    }

    public static String tag() {
        return overrideTag == null ? "" : overrideTag;
    }

    public static void set(Context c, String tag) {
        overrideTag = tag == null ? "" : tag;
        overrideCtx = null;
        if (c == null) {
            return;
        }
        try {
            SharedPreferences sp = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            sp.edit().putString(KEY_LANG, overrideTag).commit();
        } catch (Throwable ignored) {
        }
        applyOverride(c);
    }

    private static void applyOverride(Context c) {
        overrideCtx = null;
        if (overrideTag.length() == 0) {
            return;
        }
        try {
            Configuration cfg = new Configuration(c.getResources().getConfiguration());
            cfg.setLocale(toLocale(overrideTag));
            overrideCtx = c.createConfigurationContext(cfg);
        } catch (Throwable t) {
            overrideCtx = null;
        }
    }

    public static Locale toLocale(String tag) {
        if (tag == null || tag.length() == 0) {
            return Locale.getDefault();
        }
        int i = tag.indexOf("-r");
        if (i > 0 && i + 2 < tag.length()) {
            return new Locale(tag.substring(0, i), tag.substring(i + 2));
        }
        int j = tag.indexOf('-');
        if (j > 0 && j + 1 < tag.length()) {
            return new Locale(tag.substring(0, j), tag.substring(j + 1));
        }
        return new Locale(tag);
    }

    public static String t(Context c, String key, String fallback) {
        if (c == null) {
            return fallback;
        }
        String s = pick(overrideCtx, c, key);
        if (s == null) {
            s = pick(c, c, key);
        }
        if (s == null) {
            s = pick(c.getApplicationContext(), c, key);
        }
        return s == null ? fallback : s;
    }

    private static String pick(Context ctx, Context c, String key) {
        if (ctx == null) {
            return null;
        }
        try {
            Resources r = ctx.getResources();
            int id = lookup(r, "ffm_" + key, c);
            if (id != 0) {
                String s = r.getString(id);

                if (s != null) {
                    return s;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static int lookup(Resources r, String name, Context c) {
        if (goodPkg != null) {
            int id = id(r, name, goodPkg);
            if (id != 0) {
                return id;
            }
            goodPkg = null;
        }
        String[] candidates = {pkgOf(c), RES_PKG, null};
        for (int i = 0; i < candidates.length; i++) {
            int id = id(r, name, candidates[i]);
            if (id != 0) {
                goodPkg = candidates[i];
                return id;
            }
        }
        return 0;
    }

    private static int id(Resources r, String name, String pkg) {
        try {

            return r.getIdentifier(name, "string", pkg);
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static String pkgOf(Context c) {
        try {
            return c.getPackageName();
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static String probe(Context c) {
        StringBuilder sb = new StringBuilder();
        sb.append("lang tag: ").append(tag().length() == 0 ? "(system)" : tag()).append('\n');
        sb.append("res pkg: ").append(goodPkg == null ? "(unknown)" : goodPkg).append('\n');
        String[] keys = {"mm_title", "mm_tab_mods", "mm_btn_delete", "mm_prompt_title"};
        for (int i = 0; i < keys.length; i++) {
            String s = t(c, keys[i], null);
            sb.append(keys[i]).append(" -> ").append(s == null ? "(MISSING)" : s).append('\n');
        }
        return sb.toString();
    }
}
