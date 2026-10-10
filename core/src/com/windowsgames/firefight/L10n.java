package com.windowsgames.firefight;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;

import java.util.Locale;

/**
 * 本地化取词：优先从 APK 资源 res/values[-xx]/strings.xml 里按名字取 ffm_<key>，
 * 取不到时回退到调用方给的英文常量。这样新增语种只需要加资源文件，不用改 Java。
 *
 * 需求②：系统语言是英文时界面就是英文，用户希望能手动挑语言。
 * 这里用「应用内覆盖」的办法：把偏好存进 SharedPreferences，取词时改用
 * createConfigurationContext() 生成的资源上下文。**不碰 SDL 的 Context**
 * （原版 README 的血泪坑：把包装 Context 交给 SDL 会让游戏起不来）。
 * 也刻意不调用 LocaleManager.setApplicationLocales()，因为那会触发 Activity 重建
 * = 游戏重启。
 */
public final class L10n {

    private static final String PREFS = "ffm_prefs";
    private static final String KEY_LANG = "lang";

    /** 语言标签（Android 资源目录风格），"" = 跟随系统。 */
    public static final String[] TAGS = {
            "", "en", "zh-rCN", "zh-rTW", "ja", "ko", "ru", "de", "fr", "es", "it",
            "pt-rBR", "ar", "tr", "pl", "nl", "uk", "vi", "th", "hi", "id", "ms",
            "cs", "el", "he", "fa", "bg", "hu", "ro", "sv",
    };

    /** 语言自称（不翻译，任何语言下都看得懂）。与 TAGS 一一对应。 */
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

    /** apktool 重建后资源表里的包名（实测值），作为最后的兜底候选。 */
    private static final String RES_PKG = "com.windowsgames.firefight";

    private static String overrideTag = "";
    private static Context overrideCtx;
    private static String goodPkg;

    private L10n() {
    }

    /** 启动时读一次偏好。 */
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

    /** 当前覆盖的语言标签，"" 表示跟随系统。 */
    public static String tag() {
        return overrideTag == null ? "" : overrideTag;
    }

    /** 设置语言；同时落盘，下次启动继续生效。 */
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

    /** "zh-rCN" / "pt-rBR" / "ja" → Locale。 */
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
                // 空字符串是合法译文（例如倒计时拼接键 mm_prompt_auto 在中文里就是空），
                // 不能当成「没找到」，否则会回落到键名。
                if (s != null) {
                    return s;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /**
     * apktool 重建出来的 resources.arsc 里，资源包的包名不一定是 applicationId
     * （本项目实测资源包名是原版游戏包名 com.windowsgames.firefight，而 applicationId 是
     * com.windowsgames.firefight.mod）——按 applicationId 查名字会永远返回 0。
     * 所以这里逐个候选包名试，并把第一次成功的那个缓存下来。
     */
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
            // pkg == null 时 AssetManager2 会遍历资源表里的所有包。
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

    /** 诊断用：在真机上确认 ffm_ 资源到底能不能被找到。 */
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
