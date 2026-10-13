package com.windowsgames.firefight;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.StrictMode;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The manager screen: a title bar with the language switch and the return button, plus the three
 * tabs mods / maps / about. Both lists show every entry in one scroll view, can be filtered by
 * name, id or number, and every entry can be moved or given another number.
 * Not implemented: editing a mod in place, thumbnails for mods, multi-select.
 */

public class ModManagerActivity extends Activity implements View.OnClickListener {

    public static final String VERSION = "0.1.2";
    public static final String BUILD = "ffm-0.1.2";

    private static final String URL_SITE = "http://www.windowsgames.co.uk/ff.html";
    private static final String URL_AUTHOR = "https://github.com/BiancaoAwa";
    private static final String URL_REPO = "https://github.com/BiancaoAwa/Firefight-Mod-Manager-Android";
    private static final String URL_LICENSE = URL_REPO + "/blob/main/LICENSE";
    private static final String URL_BILI = "https://space.bilibili.com/1499437543";
    private static final String URL_AFDIAN = "https://afdian.com/a/bc_awa";
    private static final String URL_MAIL = "BC_awa@163.com";

    /** Text shown on the licence page; the standard MIT wording, kept verbatim. */
    private static final String MIT_TEXT =
            "MIT License\n\n"
            + "Copyright (c) 2026 BiancaoAwa\n\n"
            + "Permission is hereby granted, free of charge, to any person obtaining a copy\n"
            + "of this software and associated documentation files (the \"Software\"), to deal\n"
            + "in the Software without restriction, including without limitation the rights\n"
            + "to use, copy, modify, merge, publish, distribute, sublicense, and/or sell\n"
            + "copies of the Software, and to permit persons to whom the Software is\n"
            + "furnished to do so, subject to the following conditions:\n\n"
            + "The above copyright notice and this permission notice shall be included in all\n"
            + "copies or substantial portions of the Software.\n\n"
            + "THE SOFTWARE IS PROVIDED \"AS IS\", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR\n"
            + "IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,\n"
            + "FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE\n"
            + "AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER\n"
            + "LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,\n"
            + "OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE\n"
            + "SOFTWARE.";

    private static final int TAB_MODS = 0;
    private static final int TAB_MAPS = 1;
    private static final int TAB_ABOUT = 2;

    private static final int MAX_SLOTS = 5;
    private static final int PAGE_SIZE = 9;

    private static final int REQ_PICK_MOD = 0x61;
    private static final int REQ_PICK_MAP = 0x62;

    private static final int ID_TAB_MODS = 1;
    private static final int ID_TAB_MAPS = 2;
    private static final int ID_TAB_ABOUT = 3;
    private static final int ID_BACK = 4;
    private static final int ID_IMPORT_MOD = 11;
    private static final int ID_OPEN_MODS = 12;
    private static final int ID_IMPORT_MAP = 13;
    private static final int ID_OPEN_MAPS = 14;
    private static final int ID_MOD_JUMP = 15;
    private static final int ID_MAP_JUMP = 16;
    private static final int ID_META = 21;
    private static final int ID_SITE = 22;
    private static final int ID_REPO = 23;
    private static final int ID_AUTHOR = 24;
    private static final int ID_AFDIAN = 25;
    private static final int ID_MAIL = 26;
    private static final int ID_META_BACK = 27;
    private static final int ID_LANG = 28;
    private static final int ID_COPY = 29;
    private static final int ID_PERM = 30;
    private static final int ID_BILI = 31;
    private static final int ID_HELP = 32;
    private static final int ID_AUTHOR_PAGE = 33;
    private static final int ID_LICENSE = 34;
    private static final int ID_LICENSE_URL = 35;

    private static final int ABOUT_MAIN = 0;
    private static final int ABOUT_META = 1;
    private static final int ABOUT_HELP = 2;
    private static final int ABOUT_AUTHOR = 3;
    private static final int ABOUT_LICENSE = 4;

    /** Help lines, in the order they appear on the help page. */
    private static final String[] HELP_KEYS = {
        "mm_help_1", "mm_help_2", "mm_help_3", "mm_help_4",
        "mm_help_5", "mm_help_6", "mm_help_7", "mm_help_8",
    };

    private final Button[] tabs = new Button[3];
    private LinearLayout content;
    private TextView stat;
    private LinearLayout rootBox;

    private ScrollView scroller;
    private LinearLayout listBox;
    private EditText searchBox;
    private final List<View> rowViews = new ArrayList<View>();

    private int tab = TAB_MODS;
    private String modQuery = "";
    private String mapQuery = "";
    private int aboutPage = ABOUT_MAIN;
    /** Set by every change that only takes effect after the game restarts. */
    private boolean dirty = false;
    private boolean resumed = false;

    private List<ModImporter.Slot> slots = new ArrayList<ModImporter.Slot>();
    private List<MapImporter.MapEntry> maps = new ArrayList<MapImporter.MapEntry>();

    protected void onCreate(Bundle savedInstanceState) {
        try {
            super.onCreate(savedInstanceState);
        } catch (Throwable t) {
        }
        try {
            StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder().build());
        } catch (Throwable t) {
        }
        try {
            L10n.init(this);
            LogStore.init(this);
            LogStore.event(this, "== manager " + VERSION + " open ==");
        } catch (Throwable t) {
        }
        try {
            setContentView(buildRoot());
        } catch (Throwable t) {
            toast(String.valueOf(t));
            finish();
            return;
        }
        showTab(TAB_MODS);
        settleLayout();
        if (!Perms.has(this)) {
            Perms.request(this);
        }
    }

    protected void onResume() {
        try {
            super.onResume();
        } catch (Throwable t) {
        }
        if (!resumed) {
            resumed = true;
            return;
        }
        showTab(tab);
    }

    public void onBackPressed() {
        if (aboutPage != ABOUT_MAIN) {
            aboutPage = ABOUT_MAIN;
            showTab(TAB_ABOUT);
            return;
        }
        if (tab != TAB_MODS) {
            showTab(TAB_MODS);
            return;
        }
        askExit();
    }

    private View buildRoot() {

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setBackgroundColor(Ui.bg(this));
        scroller = sv;

        // Keep the header clear of the status bar, the display cutout and the navigation bar.
        final ScrollView insetView = sv;
        sv.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            public WindowInsets onApplyWindowInsets(View v, WindowInsets ins) {
                insetView.setPadding(ins.getSystemWindowInsetLeft(), ins.getSystemWindowInsetTop(),
                        ins.getSystemWindowInsetRight(), ins.getSystemWindowInsetBottom());
                return ins;
            }
        });

        LinearLayout root = Ui.column(this);
        Ui.pad(root, this, 12, 10, 12, 16);
        sv.addView(root, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        rootBox = root;

        LinearLayout head = Ui.row(this);
        head.addView(Ui.title(this, t("mm_title")), Ui.weight(1f));
        Button lang = Ui.headerAction(this, t("mm_lang"));
        lang.setId(ID_LANG);
        lang.setOnClickListener(this);
        head.addView(lang);
        Button back = Ui.headerAction(this, t("mm_exit_quit"));
        back.setId(ID_BACK);
        back.setOnClickListener(this);
        head.addView(back);
        root.addView(head, Ui.fill());

        TextView ver = Ui.small(this, t("mm_meta_version") + " " + VERSION + "  (" + BUILD + ")");
        LinearLayout.LayoutParams verLp = Ui.fill();
        verLp.setMargins(0, Ui.dp(this, 2), 0, 0);
        root.addView(ver, verLp);

        stat = Ui.subtitle(this, "");
        LinearLayout.LayoutParams statLp = Ui.fill();
        statLp.setMargins(0, Ui.dp(this, 2), 0, 0);
        root.addView(stat, statLp);

        root.addView(Ui.spacer(this, 10));

        LinearLayout bar = Ui.row(this);
        String[] labels = {t("mm_tab_mods"), t("mm_tab_maps"), t("mm_tab_about")};
        int[] ids = {ID_TAB_MODS, ID_TAB_MAPS, ID_TAB_ABOUT};
        for (int i = 0; i < 3; i++) {
            Button b = Ui.tabButton(this, labels[i]);
            b.setId(ids[i]);
            b.setOnClickListener(this);
            tabs[i] = b;
            bar.addView(b, Ui.weight(1f));
        }
        root.addView(bar, Ui.fill());

        root.addView(Ui.divider(this), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, Ui.dp(this, 1))));

        content = Ui.column(this);
        Ui.pad(content, this, 0, 4, 0, 12);
        root.addView(content, Ui.fill());
        return sv;
    }

    private void setStat(String base) {
        stat.setText(base == null ? "" : base);
    }

    private void settleLayout() {
        if (rootBox == null) {
            return;
        }
        rootBox.postDelayed(new Runnable() {
            public void run() {
                try {
                    if (content != null && content.getHeight() <= 0) {
                        LogStore.event(ModManagerActivity.this, "manager: content h=0, rebuild");
                        showTab(tab);
                    }
                } catch (Throwable t) {
                }
            }
        }, 400);
    }

    private void showTab(int which) {
        tab = which;
        for (int i = 0; i < 3; i++) {
            boolean on = i == which;
            if (tabs[i] == null) {
                continue;
            }
            tabs[i].setTextColor(on ? Ui.accent(this) : Ui.secondary(this));
            tabs[i].setTypeface(on ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        }
        if (which == TAB_MAPS) {
            showMaps();
        } else if (which == TAB_ABOUT) {
            showAbout();
        } else {
            showMods();
        }
    }

    private void showMods() {
        content.removeAllViews();
        slots = ModImporter.listSlots(this);
        setStat(modStat(slots, modQuery));

        LinearLayout bar = Ui.row(this);
        searchBox = Ui.searchBox(this, t("mm_search_hint"));
        searchBox.setText(modQuery);
        bar.addView(searchBox, Ui.weight(1f));
        Button jump = Ui.headerAction(this, t("mm_jump"));
        jump.setId(ID_MOD_JUMP);
        jump.setOnClickListener(this);
        bar.addView(jump);
        content.addView(bar, Ui.fill());
        content.addView(Ui.small(this, t("mm_mods_one_note")), Ui.fill());

        listBox = Ui.column(this);
        content.addView(listBox, Ui.fill());
        searchBox.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            public void afterTextChanged(Editable s) {
                modQuery = s == null ? "" : s.toString();
                fillMods();
                setStat(modStat(slots, modQuery));
            }
        });
        fillMods();

        content.addView(Ui.spacer(this, 6));
        content.addView(button(ID_IMPORT_MOD, t("mm_btn_import_mod")), Ui.fill());
        content.addView(Ui.spacer(this, 4));
        content.addView(button(ID_OPEN_MODS, t("mm_btn_open_mods")), Ui.fill());
    }

    private String modStat(List<ModImporter.Slot> all, String query) {
        long total = 0;
        for (int i = 0; i < all.size(); i++) {
            total += all.get(i).bytes;
        }
        String s = t("mm_mods_count") + " " + all.size()
                + "    " + t("mm_mods_total") + " " + ModImporter.mb(total);
        if (query != null && query.trim().length() > 0) {
            s = s + "    (" + filterSlots(all, query).size() + "/" + all.size() + ")";
        }
        return s;
    }

    private List<ModImporter.Slot> filterSlots(List<ModImporter.Slot> all, String query) {
        List<ModImporter.Slot> out = new ArrayList<ModImporter.Slot>();
        String key = query == null ? "" : query.trim().toLowerCase(Locale.US);
        for (int i = 0; i < all.size(); i++) {
            ModImporter.Slot s = all.get(i);
            if (key.length() == 0) {
                out.add(s);
                continue;
            }
            String hay = (s.display(this) + " " + s.name + " " + s.id + " " + (i + 1))
                    .toLowerCase(Locale.US);
            if (hay.contains(key)) {
                out.add(s);
            }
        }
        return out;
    }

    private void fillMods() {
        if (listBox == null) {
            return;
        }
        listBox.removeAllViews();
        rowViews.clear();
        if (slots.isEmpty()) {
            listBox.addView(Ui.small(this, t("mm_no_mods")), Ui.fill());
            return;
        }
        List<ModImporter.Slot> shown = filterSlots(slots, modQuery);
        if (shown.isEmpty()) {
            listBox.addView(Ui.small(this, t("mm_no_result")), Ui.fill());
            return;
        }
        for (int i = 0; i < shown.size(); i++) {
            ModImporter.Slot s = shown.get(i);
            View v = slotCard(ModImporter.indexOfId(slots, s.id) + 1, s);
            listBox.addView(v, cardLp());
            rowViews.add(v);
        }
    }

    /** Layout params for a button row inside a card, with a gap above it. */
    private LinearLayout.LayoutParams rowLp() {
        LinearLayout.LayoutParams lp = Ui.fill();
        lp.setMargins(0, Ui.dp(this, 6), 0, 0);
        return lp;
    }

    private View slotCard(final int number, final ModImporter.Slot s) {
        LinearLayout box = Ui.card(this);
        box.addView(Ui.strong(this, "#" + number + "  " + s.display(this)), Ui.fill());
        box.addView(Ui.small(this, (s.enabled ? t("mm_state_enabled") : t("mm_state_disabled"))
                + "  ·  " + s.sizeText() + "  ·  " + s.id), Ui.fill());

        LinearLayout main = Ui.row(this);
        Button toggle = Ui.button(this, s.enabled ? t("mm_btn_disable") : t("mm_btn_enable"));
        toggle.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                toggleSlot(s);
            }
        });
        Button del = Ui.button(this, t("mm_btn_delete"));
        del.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                askDeleteMod(s);
            }
        });
        main.addView(toggle, Ui.weight(1f));
        main.addView(del, Ui.weight(1f));
        box.addView(main, rowLp());

        LinearLayout nums = Ui.row(this);
        Button no = Ui.mini(this, t("mm_sort_no") + " " + number);
        no.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                askModNumber(s, number);
            }
        });
        Button up = Ui.mini(this, "\u2191");
        up.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                moveSlot(s, -1);
            }
        });
        Button down = Ui.mini(this, "\u2193");
        down.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                moveSlot(s, 1);
            }
        });
        nums.addView(no, Ui.weight(1f));
        nums.addView(up, Ui.weight(0.5f));
        nums.addView(down, Ui.weight(0.5f));
        box.addView(nums, rowLp());
        return box;
    }

    private void moveSlot(final ModImporter.Slot s, final int delta) {
        if (s == null || !ModImporter.moveSlot(this, s, delta)) {
            toast(t("mm_op_failed"));
            return;
        }
        LogStore.event(this, "mod " + s.id + " move " + delta);
        showTab(TAB_MODS);
    }

    private void askModNumber(final ModImporter.Slot s, int current) {
        askNumber(t("mm_sort_no"), current, new NumberSink() {
            public void apply(int n) {
                if (!ModImporter.setSlotIndex(ModManagerActivity.this, s, n)) {
                    toast(t("mm_op_failed"));
                    return;
                }
                LogStore.event(ModManagerActivity.this, "mod " + s.id + " -> " + n);
                showTab(TAB_MODS);
            }
        });
    }

    private void toggleSlot(final ModImporter.Slot s) {
        if (s == null) {
            return;
        }
        final boolean on = !s.enabled;
        new Thread(new Runnable() {
            public void run() {
                final boolean ok = ModImporter.setEnabled(getApplicationContext(), s, on);
                runOnUiThread(new Runnable() {
                    public void run() {
                        toast(ok ? t(on ? "mm_enable_ok" : "mm_disable_ok") : t("mm_op_failed"));
                        showTab(TAB_MODS);
                        if (ok) {
                            markDirty();
                        }
                    }
                });
            }
        }).start();
    }

    private void askDeleteMod(final ModImporter.Slot s) {
        if (s == null) {
            return;
        }
        confirm(t("mm_del_mod") + "\n\n" + s.display(this), new Runnable() {
            public void run() {
                final boolean ok = ModImporter.deleteSlot(getApplicationContext(), s);
                toast(ok ? t("mm_deleted") : t("mm_del_failed"));
                showTab(TAB_MODS);
                if (ok) {
                    markDirty();
                }
            }
        });
    }

    private void showMaps() {
        content.removeAllViews();
        maps = new ArrayList<MapImporter.MapEntry>();
        List<MapImporter.MapEntry> all = MapImporter.listMaps(this);
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).present) {
                maps.add(all.get(i));
            }
        }
        setStat(mapStat(maps, mapQuery));

        LinearLayout bar = Ui.row(this);
        searchBox = Ui.searchBox(this, t("mm_search_hint"));
        searchBox.setText(mapQuery);
        bar.addView(searchBox, Ui.weight(1f));
        Button jump = Ui.headerAction(this, t("mm_jump"));
        jump.setId(ID_MAP_JUMP);
        jump.setOnClickListener(this);
        bar.addView(jump);
        content.addView(bar, Ui.fill());
        content.addView(Ui.small(this, t("mm_map_order_hint")), Ui.fill());

        listBox = Ui.column(this);
        content.addView(listBox, Ui.fill());
        searchBox.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            public void afterTextChanged(Editable s) {
                mapQuery = s == null ? "" : s.toString();
                fillMaps();
                setStat(mapStat(maps, mapQuery));
            }
        });
        fillMaps();

        content.addView(Ui.spacer(this, 6));
        content.addView(button(ID_IMPORT_MAP, t("mm_btn_import_map")), Ui.fill());
        content.addView(Ui.spacer(this, 4));
        content.addView(button(ID_OPEN_MAPS, t("mm_btn_open_maps")), Ui.fill());
    }

    private String mapStat(List<MapImporter.MapEntry> all, String query) {
        long total = 0;
        for (int i = 0; i < all.size(); i++) {
            total += all.get(i).bytes;
        }
        String s = t("mm_maps_count") + " " + all.size()
                + "    " + t("mm_maps_total") + " " + MapImporter.human(total)
                + "    " + t("mm_load_no") + " " + MapImporter.loadCount(this)
                + "/" + MapImporter.MAX_LOAD;
        if (query != null && query.trim().length() > 0) {
            s = s + "    (" + filterMaps(all, query).size() + "/" + all.size() + ")";
        }
        return s;
    }

    private List<MapImporter.MapEntry> filterMaps(List<MapImporter.MapEntry> all, String query) {
        List<MapImporter.MapEntry> out = new ArrayList<MapImporter.MapEntry>();
        String key = query == null ? "" : query.trim().toLowerCase(Locale.US);
        for (int i = 0; i < all.size(); i++) {
            MapImporter.MapEntry e = all.get(i);
            if (key.length() == 0) {
                out.add(e);
                continue;
            }
            String hay = (e.display(this) + " " + e.name + " " + e.code + " " + (i + 1))
                    .toLowerCase(Locale.US);
            if (hay.contains(key)) {
                out.add(e);
            }
        }
        return out;
    }

    private void fillMaps() {
        if (listBox == null) {
            return;
        }
        listBox.removeAllViews();
        rowViews.clear();
        if (maps.isEmpty()) {
            listBox.addView(Ui.small(this, t("mm_no_maps")), Ui.fill());
            return;
        }
        List<MapImporter.MapEntry> shown = filterMaps(maps, mapQuery);
        if (shown.isEmpty()) {
            listBox.addView(Ui.small(this, t("mm_no_result")), Ui.fill());
            return;
        }
        for (int i = 0; i < shown.size(); i++) {
            MapImporter.MapEntry e = shown.get(i);
            int at = maps.indexOf(e);
            View v = mapCard((at < 0 ? i : at) + 1, e);
            listBox.addView(v, cardLp());
            rowViews.add(v);
        }
    }

    private View mapCard(final int number, final MapImporter.MapEntry e) {
        final int load = e.listed ? MapImporter.loadIndexOf(this, e.code) : -1;

        LinearLayout box = Ui.card(this);
        LinearLayout top = Ui.row(this);
        top.addView(Ui.thumb(this, MapImporter.mapImage(e.dir, e.code), 64));
        LinearLayout info = Ui.column(this);
        info.addView(Ui.strong(this, "#" + number + "  " + e.display(this)), Ui.fill());
        info.addView(Ui.small(this, e.code + "  ·  " + e.sizeText()
                + "  ·  " + e.state(this)), Ui.fill());
        info.addView(Ui.small(this, t("mm_sort_no") + " " + number
                + "    " + t("mm_load_no") + " "
                + (load > 0 ? String.valueOf(load) : t("mm_load_none"))), Ui.fill());
        top.addView(info, Ui.weight(1f));
        box.addView(top, Ui.fill());

        LinearLayout main = Ui.row(this);
        Button on = Ui.button(this, e.listed ? t("mm_btn_disable") : t("mm_btn_enable"));
        on.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                toggleMap(e);
            }
        });
        Button del = Ui.button(this, t("mm_btn_delete"));
        del.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                askDeleteMap(e);
            }
        });
        main.addView(on, Ui.weight(1f));
        main.addView(del, Ui.weight(1f));
        box.addView(main, rowLp());

        LinearLayout sortRow = Ui.row(this);
        Button sort = Ui.mini(this, t("mm_sort_no") + " " + number);
        sort.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                askMapNumber(e, number);
            }
        });
        Button sup = Ui.mini(this, "\u2191");
        sup.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                moveMap(e, -1);
            }
        });
        Button sdown = Ui.mini(this, "\u2193");
        sdown.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                moveMap(e, 1);
            }
        });
        sortRow.addView(sort, Ui.weight(1f));
        sortRow.addView(sup, Ui.weight(0.4f));
        sortRow.addView(sdown, Ui.weight(0.4f));
        box.addView(sortRow, rowLp());

        LinearLayout loadRow = Ui.row(this);
        Button loadBtn = Ui.mini(this, t("mm_load_no") + " "
                + (load > 0 ? String.valueOf(load) : t("mm_load_none")));
        loadBtn.setEnabled(e.listed);
        loadBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                askMapLoadNumber(e, load);
            }
        });
        Button lup = Ui.mini(this, "\u2191");
        lup.setEnabled(e.listed);
        lup.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                moveMapLoad(e, -1);
            }
        });
        Button ldown = Ui.mini(this, "\u2193");
        ldown.setEnabled(e.listed);
        ldown.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                moveMapLoad(e, 1);
            }
        });
        loadRow.addView(loadBtn, Ui.weight(1f));
        loadRow.addView(lup, Ui.weight(0.4f));
        loadRow.addView(ldown, Ui.weight(0.4f));
        box.addView(loadRow, rowLp());

        box.setClickable(true);
        box.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mapActions(e);
            }
        });
        return box;
    }

    private void toggleMap(final MapImporter.MapEntry e) {
        if (e == null) {
            return;
        }
        final boolean on = !e.listed;
        if (on && MapImporter.loadCount(this) >= MapImporter.MAX_LOAD) {
            toast(t("mm_map_limit"));
            return;
        }
        boolean ok = on
                ? MapImporter.registerCode(this, e.code)
                : MapImporter.unregisterCode(this, e.code);
        LogStore.event(this, "map " + e.code + " -> " + (on ? "enabled " : "disabled ") + ok);
        toast(ok ? t(on ? "mm_enable_ok" : "mm_disable_ok") : t("mm_op_failed"));
        showTab(TAB_MAPS);
        if (ok) {
            markDirty();
        }
    }

    private void moveMap(final MapImporter.MapEntry e, final int delta) {
        if (e == null || !MapImporter.moveMap(this, e.code, delta)) {
            toast(t("mm_op_failed"));
            return;
        }
        LogStore.event(this, "map " + e.code + " sort " + delta);
        showTab(TAB_MAPS);
        markDirty();
    }

    private void moveMapLoad(final MapImporter.MapEntry e, final int delta) {
        if (e == null || !MapImporter.moveMapLoad(this, e.code, delta)) {
            toast(t("mm_op_failed"));
            return;
        }
        LogStore.event(this, "map " + e.code + " load " + delta);
        showTab(TAB_MAPS);
        markDirty();
    }

    private void askMapNumber(final MapImporter.MapEntry e, int current) {
        askNumber(t("mm_sort_no"), current, new NumberSink() {
            public void apply(int n) {
                if (!MapImporter.setMapIndex(ModManagerActivity.this, e.code, n)) {
                    toast(t("mm_op_failed"));
                    return;
                }
                showTab(TAB_MAPS);
            }
        });
    }

    private void askMapLoadNumber(final MapImporter.MapEntry e, int current) {
        askNumber(t("mm_load_no"), current < 1 ? 1 : current, new NumberSink() {
            public void apply(int n) {
                if (!MapImporter.setMapLoadIndex(ModManagerActivity.this, e.code, n)) {
                    toast(t("mm_op_failed"));
                    return;
                }
                showTab(TAB_MAPS);
            }
        });
    }

    private void askDeleteMap(final MapImporter.MapEntry e) {
        confirm(t("mm_del_map") + "\n\n" + e.display(this), new Runnable() {
            public void run() {
                String r = MapImporter.remove(getApplicationContext(), e.code);
                toast("ok".equals(r) ? t("mm_deleted") : t("mm_del_failed"));
                showTab(TAB_MAPS);
                if ("ok".equals(r)) {
                    markDirty();
                }
            }
        });
    }

    private void mapActions(final MapImporter.MapEntry e) {
        final String[] items = {t("mm_map_details"), t("mm_map_delete"), t("mm_map_copy")};
        try {
            new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle(e.display(this))
                    .setItems(items, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface d, int which) {
                            if (which == 0) {
                                mapDetails(e);
                            } else if (which == 1) {
                                confirm(t("mm_del_map") + "\n\n" + e.display(ModManagerActivity.this),
                                        new Runnable() {
                                            public void run() {
                                                String r = MapImporter.remove(
                                                        getApplicationContext(), e.code);
                                                toast("ok".equals(r)
                                                        ? t("mm_deleted") : t("mm_del_failed"));
                                                showTab(TAB_MAPS);
                                                if ("ok".equals(r)) {
                                                    markDirty();
                                                }
                                            }
                                        });
                            } else {
                                copy("firefight-map", e.dir == null ? e.code : e.dir.getAbsolutePath());
                                toast(t("mm_copied"));
                            }
                        }
                    })
                    .setNegativeButton(t("mm_cancel"), null)
                    .show();
        } catch (Throwable t) {
            toast(String.valueOf(t));
        }
    }

    private void mapDetails(MapImporter.MapEntry e) {
        StringBuilder b = new StringBuilder();
        b.append(t("mm_map_code")).append(": ").append(e.code).append('\n');
        b.append(t("mm_map_files")).append(": ").append(e.files).append('\n');
        b.append(t("mm_map_size")).append(": ").append(e.sizeText()).append('\n');
        b.append(t("mm_map_state")).append(": ").append(e.state(this)).append('\n');
        b.append(t("mm_map_imported")).append(": ").append(e.listed).append('\n');
        b.append(t("mm_meta_mapdir")).append(": ")
                .append(e.dir == null ? "-" : e.dir.getAbsolutePath());
        try {
            new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle(e.display(this))
                    .setMessage(b.toString())
                    .setPositiveButton(t("mm_ok"), null)
                    .show();
        } catch (Throwable t) {
            toast(b.toString());
        }
    }

    private void showAbout() {
        content.removeAllViews();
        if (aboutPage != ABOUT_MAIN) {
            showAboutPage();
            return;
        }
        setStat(VERSION + "  (" + BUILD + ")");
        content.addView(infoRow(ID_HELP, t("mm_about_help"), t("mm_help_1")), cardLp());
        content.addView(infoRow(ID_META, t("mm_about_meta"), VERSION + " / " + BUILD), cardLp());
        content.addView(infoRow(ID_SITE, t("mm_about_site"), URL_SITE), cardLp());
        content.addView(infoRow(ID_REPO, t("mm_about_repo"), URL_REPO), cardLp());
        content.addView(infoRow(ID_AUTHOR_PAGE, t("mm_about_author_page"), URL_AUTHOR), cardLp());
        content.addView(infoRow(ID_LICENSE, t("mm_about_license"), "MIT"), cardLp());
    }

    /** Sub-pages of the about tab: manager information, help, author links, licence. */
    private void showAboutPage() {
        if (aboutPage == ABOUT_META) {
            setStat(t("mm_meta_title"));
            showMetaRows();
            return;
        }
        if (aboutPage == ABOUT_HELP) {
            setStat(t("mm_about_help"));
            for (int i = 0; i < HELP_KEYS.length; i++) {
                TextView line = Ui.body(this, t(HELP_KEYS[i]));
                LinearLayout.LayoutParams lp = Ui.fill();
                lp.setMargins(0, Ui.dp(this, 8), 0, 0);
                content.addView(line, lp);
            }
        } else if (aboutPage == ABOUT_AUTHOR) {
            setStat(t("mm_about_author_page"));
            content.addView(infoRow(ID_AUTHOR, t("mm_about_author"), URL_AUTHOR), cardLp());
            content.addView(infoRow(ID_BILI, t("mm_about_bili"), URL_BILI), cardLp());
            content.addView(infoRow(ID_AFDIAN, t("mm_about_afdian"), URL_AFDIAN), cardLp());
            content.addView(Ui.small(this, t("mm_author_note")), cardLp());
            content.addView(infoRow(ID_MAIL, t("mm_about_mail"), URL_MAIL), cardLp());
        } else if (aboutPage == ABOUT_LICENSE) {
            setStat(t("mm_about_license"));
            TextView body = Ui.small(this, MIT_TEXT);
            body.setTextIsSelectable(true);
            content.addView(body, cardLp());
            content.addView(infoRow(ID_LICENSE_URL, t("mm_about_repo"), URL_LICENSE), cardLp());
        }
        content.addView(Ui.spacer(this, 12));
        content.addView(button(ID_META_BACK, t("mm_back")), Ui.fill());
    }

    private void showMetaRows() {
        content.addView(kv(t("mm_meta_version"), VERSION), Ui.fill());
        content.addView(kv(t("mm_meta_build"), BUILD), Ui.fill());
        content.addView(kv(t("mm_meta_pkg"), getPackageName()), Ui.fill());
        content.addView(kv(t("mm_meta_moddir"), pathOf(ModImporter.modRoot(this))), Ui.fill());
        content.addView(kv(t("mm_meta_mapdir"), pathOf(MapImporter.mapsRoot(this))), Ui.fill());
        content.addView(kv(t("mm_meta_logdir"), safeLogPath()), Ui.fill());
        content.addView(kv(t("mm_meta_lang"),
                L10n.tag() == null || L10n.tag().length() == 0
                        ? t("mm_meta_lang_follow") : L10n.tag()), Ui.fill());
        content.addView(Ui.small(this, t("mm_lang_hint")), Ui.fill());

        content.addView(Ui.spacer(this, 12));
        content.addView(button(ID_COPY, t("mm_meta_copy")), Ui.fill());
        if (!Perms.has(this)) {
            content.addView(Ui.spacer(this, 4));
            content.addView(button(ID_PERM, t("mm_root_unavailable")), Ui.fill());
        }

        content.addView(Ui.spacer(this, 12));
        content.addView(Ui.subtitle(this, t("mm_meta_diag")), Ui.fill());
        TextView d = Ui.small(this, diag());
        d.setTextIsSelectable(true);
        content.addView(d, Ui.fill());
        content.addView(Ui.spacer(this, 10));
        content.addView(button(ID_META_BACK, t("mm_back")), Ui.fill());
    }

    private View infoRow(int id, String label, String value) {
        LinearLayout box = Ui.card(this);
        box.addView(Ui.strong(this, label), Ui.fill());
        box.addView(Ui.small(this, value), Ui.fill());
        box.setId(id);
        box.setClickable(true);
        box.setOnClickListener(this);
        return box;
    }

    private View kv(String label, String value) {
        LinearLayout box = Ui.column(this);
        box.addView(Ui.small(this, label), Ui.fill());
        box.addView(Ui.body(this, value == null ? "-" : value), Ui.fill());
        LinearLayout.LayoutParams lp = Ui.fill();
        lp.setMargins(0, Ui.dp(this, 4), 0, 0);
        box.setLayoutParams(lp);
        return box;
    }

    private void chooseLanguage() {
        final String[] tags = L10n.TAGS;
        final String[] labels = L10n.LABELS;
        if (tags == null || labels == null) {
            return;
        }
        int cur = 0;
        String now = L10n.tag();
        for (int i = 0; i < tags.length; i++) {
            if (tags[i].equals(now)) {
                cur = i;
                break;
            }
        }
        final String[] shown = new String[labels.length];
        for (int i = 0; i < labels.length; i++) {
            shown[i] = (i == cur ? "\u2714  " : "      ") + labels[i];
        }
        try {
            new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle(t("mm_meta_lang"))
                    .setSingleChoiceItems(shown, cur, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface d, int which) {
                            L10n.set(ModManagerActivity.this, tags[which]);
                            try {
                                d.dismiss();
                            } catch (Throwable t) {
                            }
                            aboutPage = ABOUT_MAIN;
                            setContentView(buildRoot());
                            showTab(TAB_ABOUT);
                            settleLayout();
                        }
                    })
                    .setNegativeButton(t("mm_cancel"), null)
                    .show();
        } catch (Throwable t) {
            toast(String.valueOf(t));
        }
    }

    private void pick(int req) {
        try {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            startActivityForResult(i, req);
            return;
        } catch (Throwable t) {
        }
        try {
            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            startActivityForResult(i, req);
        } catch (Throwable t) {
            toast(t("mm_import_fail"));
        }
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        try {
            super.onActivityResult(requestCode, resultCode, data);
        } catch (Throwable t) {
        }
        if (requestCode != REQ_PICK_MOD && requestCode != REQ_PICK_MAP) {
            return;
        }
        Uri uri = data == null ? null : data.getData();
        if (resultCode != RESULT_OK || uri == null) {
            toast(t("mm_import_cancel"));
            return;
        }
        final boolean isMap = requestCode == REQ_PICK_MAP;
        final String name = displayName(uri);
        InputStream in = null;
        try {
            in = getContentResolver().openInputStream(uri);
        } catch (Throwable t) {
        }
        if (in == null) {
            toast(t("mm_import_fail"));
            return;
        }
        toast(t("mm_import_mod_hint"));
        final InputStream fin = in;
        new Thread(new Runnable() {
            public void run() {
                String r = "fail";
                try {
                    r = isMap
                            ? MapImporter.importZip(getApplicationContext(), fin, name)
                            : ModImporter.importMod(getApplicationContext(), fin, name);
                } catch (Throwable t) {
                } finally {
                    try {
                        fin.close();
                    } catch (Throwable t) {
                    }
                }
                final String rr = r;
                runOnUiThread(new Runnable() {
                    public void run() {
                        afterImport(isMap, rr);
                    }
                });
            }
        }).start();
    }

    private void afterImport(boolean isMap, String r) {
        String msg;
        boolean ok;
        if (isMap) {
            ok = r != null && r.startsWith("ok:");
            if (ok) {
                msg = t("mm_import_ok") + "  " + r.substring(3);
            } else {
                msg = t("mm_import_fail");
            }
        } else if ("ok".equals(r)) {
            ok = true;
            int n = ModImporter.lastInstalledIds.size();
            msg = n > 1 ? (t("mm_import_multi") + "  " + n) : t("mm_import_ok");
            if (ModImporter.lastInstalledId != null && ModImporter.lastInstalledId.length() > 0) {
                msg = msg + "  " + ModImporter.lastInstalledId;
            }
        } else {
            ok = false;
            msg = t("mm_import_fail");
        }
        toast(msg);
        try {
            LogStore.event(this, "manager import " + (isMap ? "map" : "mod") + " -> " + r);
        } catch (Throwable t) {
        }
        showTab(isMap ? TAB_MAPS : TAB_MODS);
        if (ok) {
            markDirty();
        }
    }

    private String displayName(Uri uri) {
        try {
            String p = uri.getLastPathSegment();
            if (p != null) {
                int i = p.lastIndexOf('/');
                return i >= 0 ? p.substring(i + 1) : p;
            }
        } catch (Throwable t) {
        }
        return "mod.zip";
    }

    public void onClick(View v) {
        int id = v == null ? 0 : v.getId();
        if (id == ID_TAB_MODS) {
            aboutPage = ABOUT_MAIN;
            showTab(TAB_MODS);
        } else if (id == ID_TAB_MAPS) {
            aboutPage = ABOUT_MAIN;
            showTab(TAB_MAPS);
        } else if (id == ID_TAB_ABOUT) {
            aboutPage = ABOUT_MAIN;
            showTab(TAB_ABOUT);
        } else if (id == ID_META_BACK) {
            aboutPage = ABOUT_MAIN;
            showTab(TAB_ABOUT);
        } else if (id == ID_BACK) {
            askExit();
        } else if (id == ID_HELP) {
            aboutPage = ABOUT_HELP;
            showTab(TAB_ABOUT);
        } else if (id == ID_AUTHOR_PAGE) {
            aboutPage = ABOUT_AUTHOR;
            showTab(TAB_ABOUT);
        } else if (id == ID_LICENSE) {
            aboutPage = ABOUT_LICENSE;
            showTab(TAB_ABOUT);
        } else if (id == ID_LICENSE_URL) {
            openUrl(URL_LICENSE);
        } else if (id == ID_IMPORT_MOD) {
            pick(REQ_PICK_MOD);
        } else if (id == ID_IMPORT_MAP) {
            pick(REQ_PICK_MAP);
        } else if (id == ID_OPEN_MODS) {
            openFolder(ModImporter.modRoot(this));
        } else if (id == ID_OPEN_MAPS) {
            openFolder(MapImporter.mapsRoot(this));
        } else if (id == ID_MOD_JUMP) {
            askJump(false);
        } else if (id == ID_MAP_JUMP) {
            askJump(true);
        } else if (id == ID_META) {
            aboutPage = ABOUT_META;
            showTab(TAB_ABOUT);
        } else if (id == ID_LANG) {
            chooseLanguage();
        } else if (id == ID_COPY) {
            copy("firefight", diag());
            toast(t("mm_copied"));
        } else if (id == ID_PERM) {
            Perms.request(this);
        } else if (id == ID_SITE) {
            openUrl(URL_SITE);
        } else if (id == ID_REPO) {
            openUrl(URL_REPO);
        } else if (id == ID_AUTHOR) {
            openUrl(URL_AUTHOR);
        } else if (id == ID_BILI) {
            openUrl(URL_BILI);
        } else if (id == ID_AFDIAN) {
            openUrl(URL_AFDIAN);
        } else if (id == ID_MAIL) {
            openUrl("mailto:" + URL_MAIL);
        }
    }

    /** Callback of the number dialog; the value is at least 1. */
    private interface NumberSink {
        void apply(int n);
    }

    private void askNumber(String title, int current, final NumberSink sink) {
        final EditText in = new EditText(this);
        in.setInputType(InputType.TYPE_CLASS_NUMBER);
        in.setText(String.valueOf(current));
        try {
            new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle(title)
                    .setView(in)
                    .setPositiveButton(t("mm_ok"), new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface d, int which) {
                            int n;
                            try {
                                n = Integer.parseInt(in.getText().toString().trim());
                            } catch (Throwable t) {
                                n = -1;
                            }
                            if (n < 1) {
                                toast(t("mm_jump_bad"));
                                return;
                            }
                            sink.apply(n);
                        }
                    })
                    .setNegativeButton(t("mm_cancel"), null)
                    .show();
        } catch (Throwable t) {
            toast(String.valueOf(t));
        }
    }

    private void askJump(final boolean isMap) {
        int total = isMap ? maps.size() : slots.size();
        if (total <= 0) {
            toast(t("mm_jump_bad"));
            return;
        }
        askNumber(t("mm_jump_title") + "  1 - " + total, 1, new NumberSink() {
            public void apply(int n) {
                jumpTo(isMap, n);
            }
        });
    }

    /** Clears the filter, marks the row with this library number and scrolls to it. */
    private void jumpTo(boolean isMap, int oneBased) {
        if (searchBox != null) {
            searchBox.setText("");
        }
        if (oneBased < 1 || oneBased > rowViews.size()) {
            toast(t("mm_jump_bad"));
            return;
        }
        final View v = rowViews.get(oneBased - 1);
        final android.graphics.drawable.Drawable keep = v.getBackground();
        v.setBackground(Ui.cardBgMarked(this));
        v.postDelayed(new Runnable() {
            public void run() {
                try {
                    v.setBackground(keep);
                } catch (Throwable t) {
                }
            }
        }, 2000);
        scrollTo(v);
    }

    private void scrollTo(View v) {
        if (scroller == null || v == null) {
            return;
        }
        int y = 0;
        View x = v;
        while (x != null && !(x.getParent() instanceof ScrollView)) {
            y += x.getTop();
            x = (View) x.getParent();
        }
        if (x != null) {
            y += x.getTop();
        }
        scroller.smoothScrollTo(0, Math.max(0, y - Ui.dp(this, 40)));
    }

    private String t(String key) {
        return L10n.t(this, key, key);
    }

    /** Records a change that only takes effect once the game restarts. */
    private void markDirty() {
        dirty = true;
    }

    /** Exit action: leaves directly when nothing is pending, asks otherwise. */
    private void askExit() {
        if (!dirty) {
            quitGame();
            return;
        }
        try {
            new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle(t("mm_exit_title"))
                    .setPositiveButton(t("mm_exit_quit"), new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface d, int which) {
                            quitGame();
                        }
                    })
                    .setNegativeButton(t("mm_exit_stay"), null)
                    .show();
        } catch (Throwable t) {
            quitGame();
        }
    }

    /**
     * Ends the game process instead of restarting the activity in place: restarting in place is
     * what left the sound inoperative, so the user starts the game again from the launcher.
     */
    private void quitGame() {
        try {
            LogStore.event(this, "== manager " + VERSION + " quit ==");
        } catch (Throwable t) {
        }
        try {
            finishAffinity();
        } catch (Throwable t) {
            finish();
        }
        try {
            android.os.Process.killProcess(android.os.Process.myPid());
        } catch (Throwable t) {
        }
    }

    private LinearLayout.LayoutParams cardLp() {
        LinearLayout.LayoutParams lp = Ui.fill();
        lp.setMargins(0, Ui.dp(this, 8), 0, 0);
        return lp;
    }

    private Button button(int id, String label) {
        Button b = Ui.button(this, label);
        b.setId(id);
        b.setOnClickListener(this);
        return b;
    }

    private void confirm(String msg, final Runnable yes) {
        try {
            new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle(t("mm_del_title"))
                    .setMessage(msg)
                    .setPositiveButton(t("mm_ok"), new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface d, int w) {
                            try {
                                yes.run();
                            } catch (Throwable t) {
                                toast(t("mm_del_failed"));
                            }
                        }
                    })
                    .setNegativeButton(t("mm_cancel"), null)
                    .show();
        } catch (Throwable t) {
            yes.run();
        }
    }

    private void toast(String msg) {
        try {
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
        } catch (Throwable t) {
        }
    }

    private void copy(String label, String text) {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText(label, text == null ? "" : text));
        } catch (Throwable t) {
        }
    }

    private void openUrl(String url) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Throwable t) {
            toast(url);
        }
    }

    private void openFolder(File dir) {
        if (dir == null || !dir.isDirectory()) {
            toast(t("mm_root_unavailable"));
            return;
        }
        try {
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(Uri.fromFile(dir), "resource/folder");
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Throwable t) {
            toast(dir.getAbsolutePath());
        }
    }

    private String pathOf(File f) {
        return f == null ? "-" : f.getAbsolutePath();
    }

    private String safeLogPath() {
        try {
            String p = LogStore.path(this);
            return p == null ? "-" : p;
        } catch (Throwable t) {
            return "-";
        }
    }

    private String diag() {
        StringBuilder b = new StringBuilder();
        b.append("version: ").append(VERSION).append(" (").append(BUILD).append(")\n");
        b.append("package: ").append(getPackageName()).append('\n');
        b.append("sdk: ").append(Build.VERSION.SDK_INT).append('\n');
        b.append("locale: ").append(Locale.getDefault().toString())
                .append("  ui: ").append(L10n.tag()).append('\n');
        b.append("all-files: ").append(Perms.has(this)).append('\n');
        b.append("root: ").append(pathOf(MyGame.rootOfStatic(this))).append('\n');
        b.append("mod dir: ").append(pathOf(ModImporter.modRoot(this))).append('\n');
        b.append("mod-disabled: ").append(pathOf(ModImporter.disabledRoot(this))).append('\n');
        b.append("map dir: ").append(pathOf(MapImporter.mapsRoot(this))).append('\n');
        b.append("mods: ").append(slots == null ? 0 : slots.size()).append('\n');
        b.append("maps: ").append(maps == null ? 0 : maps.size()).append('\n');
        b.append("log: ").append(safeLogPath()).append('\n');
        b.append(L10n.probe(this));
        return b.toString();
    }

}
