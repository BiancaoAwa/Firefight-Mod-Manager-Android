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
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
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
 * tabs mods / maps / about.
 * Not implemented: editing a mod in place, search.
 */

public class ModManagerActivity extends Activity implements View.OnClickListener {

    public static final String VERSION = "0.1.0";
    public static final String BUILD = "ffm-0.1.0";

    private static final String URL_SITE = "http://www.windowsgames.co.uk/ff.html";
    private static final String URL_AUTHOR = "https://github.com/BiancaoAwa";
    private static final String URL_REPO = "https://github.com/BiancaoAwa/Firefight-Mod-Manager-Android";
    private static final String URL_BILI = "https://space.bilibili.com/1499437543";
    private static final String URL_AFDIAN = "https://afdian.com/a/bc_awa";
    private static final String URL_MAIL = "BC_awa@163.com";

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
    private static final int ID_PREV = 15;
    private static final int ID_NEXT = 16;
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
    private static final int ID_SLOT_ON = 100;
    private static final int ID_SLOT_DEL = 200;
    private static final int ID_MAP_ROW = 300;
    private static final int ID_MAP_ON = 400;
    private static final int ID_MAP_UP = 500;
    private static final int ID_MAP_DOWN = 600;

    private final Button[] tabs = new Button[3];
    private LinearLayout content;
    private TextView stat;
    private LinearLayout rootBox;

    private int tab = TAB_MODS;
    private int page = 0;
    private boolean metaOpen = false;
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
        if (metaOpen) {
            metaOpen = false;
            showTab(TAB_ABOUT);
            return;
        }
        if (tab != TAB_MODS) {
            showTab(TAB_MODS);
            return;
        }
        try {
            super.onBackPressed();
        } catch (Throwable t) {
        }
    }

    private View buildRoot() {

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setBackgroundColor(Ui.bg(this));

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
        Button back = Ui.headerAction(this, t("mm_back"));
        back.setId(ID_BACK);
        back.setOnClickListener(this);
        head.addView(back);
        root.addView(head, Ui.fill());

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
        long total = 0;
        for (int i = 0; i < slots.size(); i++) {
            total += slots.get(i).bytes;
        }
        setStat(t("mm_mods_count") + " " + slots.size()
                + "    " + t("mm_mods_total") + " " + ModImporter.mb(total));

        if (slots.isEmpty()) {
            content.addView(Ui.small(this, t("mm_no_mods")), Ui.fill());
            content.addView(Ui.spacer(this, 8));
        }
        int show = Math.min(slots.size(), MAX_SLOTS);
        for (int i = 0; i < show; i++) {
            content.addView(slotCard(i, slots.get(i)), cardLp());
        }
        if (slots.size() > MAX_SLOTS) {
            content.addView(Ui.small(this, t("mm_mods_slots_note")), Ui.fill());
        }
        content.addView(Ui.small(this, t("mm_mods_one_note")), Ui.fill());
        content.addView(Ui.spacer(this, 6));
        content.addView(button(ID_IMPORT_MOD, t("mm_btn_import_mod")), Ui.fill());
        content.addView(Ui.spacer(this, 4));
        content.addView(button(ID_OPEN_MODS, t("mm_btn_open_mods")), Ui.fill());
    }

    private View slotCard(final int i, final ModImporter.Slot s) {
        LinearLayout box = Ui.card(this);
        String head = t("mm_slot") + " " + i + (i == 0 && s.enabled ? "  " + t("mm_slot_active") : "");
        box.addView(Ui.strong(this, head), Ui.fill());
        box.addView(Ui.body(this, s.display(this)), Ui.fill());
        box.addView(Ui.small(this, (s.enabled ? t("mm_state_enabled") : t("mm_state_disabled"))
                + "  ·  " + s.sizeText() + "  ·  " + s.id), Ui.fill());
        LinearLayout btns = Ui.row(this);
        Button toggle = Ui.button(this, s.enabled ? t("mm_btn_disable") : t("mm_btn_enable"));
        toggle.setId(ID_SLOT_ON + i);
        toggle.setOnClickListener(this);
        Button del = Ui.button(this, t("mm_btn_delete"));
        del.setId(ID_SLOT_DEL + i);
        del.setOnClickListener(this);
        btns.addView(toggle, Ui.weight(1f));
        btns.addView(del, Ui.weight(1f));
        box.addView(btns, Ui.fill());
        return box;
    }

    private void toggleSlot(int i) {
        if (i < 0 || i >= slots.size() || i >= MAX_SLOTS) {
            return;
        }
        final ModImporter.Slot s = slots.get(i);
        final boolean on = !s.enabled;
        new Thread(new Runnable() {
            public void run() {
                final boolean ok = ModImporter.setEnabled(getApplicationContext(), s, on);
                runOnUiThread(new Runnable() {
                    public void run() {
                        toast(ok ? t(on ? "mm_enable_ok" : "mm_disable_ok") : t("mm_op_failed"));
                        showTab(TAB_MODS);
                        if (ok) {
                            promptRestart();
                        }
                    }
                });
            }
        }).start();
    }

    private void askDeleteMod(int i) {
        if (i < 0 || i >= slots.size() || i >= MAX_SLOTS) {
            return;
        }
        final ModImporter.Slot s = slots.get(i);
        confirm(t("mm_del_mod") + "\n\n" + s.display(this), new Runnable() {
            public void run() {
                final boolean ok = ModImporter.deleteSlot(getApplicationContext(), s);
                toast(ok ? t("mm_deleted") : t("mm_del_failed"));
                showTab(TAB_MODS);
                if (ok) {
                    promptRestart();
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
        long total = 0;
        for (int i = 0; i < maps.size(); i++) {
            total += maps.get(i).bytes;
        }
        setStat(t("mm_maps_count") + " " + maps.size()
                + "    " + t("mm_maps_total") + " " + MapImporter.human(total));

        int pages = Math.max(1, (maps.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        if (page >= pages) {
            page = pages - 1;
        }
        if (page < 0) {
            page = 0;
        }

        LinearLayout pager = Ui.row(this);
        Button prev = Ui.tabButton(this, t("mm_prev"));
        prev.setId(ID_PREV);
        prev.setOnClickListener(this);
        Button next = Ui.tabButton(this, t("mm_next"));
        next.setId(ID_NEXT);
        next.setOnClickListener(this);
        TextView num = Ui.small(this, t("mm_page") + " " + (page + 1) + " / " + pages);
        num.setGravity(Gravity.CENTER);
        pager.addView(prev, Ui.weight(1f));
        pager.addView(num, Ui.weight(1f));
        pager.addView(next, Ui.weight(1f));
        content.addView(pager, Ui.fill());
        content.addView(Ui.small(this, t("mm_map_order_hint")), Ui.fill());

        if (maps.isEmpty()) {
            content.addView(Ui.small(this, t("mm_no_maps")), Ui.fill());
            content.addView(Ui.spacer(this, 8));
        }
        int from = page * PAGE_SIZE;
        int to = Math.min(maps.size(), from + PAGE_SIZE);
        for (int i = from; i < to; i++) {
            content.addView(mapRow(i - from, maps.get(i)), cardLp());
        }
        content.addView(Ui.spacer(this, 6));
        content.addView(button(ID_IMPORT_MAP, t("mm_btn_import_map")), Ui.fill());
        content.addView(Ui.spacer(this, 4));
        content.addView(button(ID_OPEN_MAPS, t("mm_btn_open_maps")), Ui.fill());
    }

    private View mapRow(final int index, final MapImporter.MapEntry e) {
        LinearLayout box = Ui.card(this);
        box.addView(Ui.strong(this, e.display(this)), Ui.fill());
        box.addView(Ui.small(this, e.code + "  ·  " + e.sizeText()
                + "  ·  " + e.state(this)), Ui.fill());
        LinearLayout btns = Ui.row(this);
        Button on = Ui.button(this, e.listed ? t("mm_btn_disable") : t("mm_btn_enable"));
        on.setId(ID_MAP_ON + index);
        on.setOnClickListener(this);
        Button up = Ui.button(this, t("mm_map_up"));
        up.setId(ID_MAP_UP + index);
        up.setOnClickListener(this);
        Button down = Ui.button(this, t("mm_map_down"));
        down.setId(ID_MAP_DOWN + index);
        down.setOnClickListener(this);
        btns.addView(on, Ui.weight(1f));
        btns.addView(up, Ui.weight(1f));
        btns.addView(down, Ui.weight(1f));
        box.addView(btns, Ui.fill());
        box.setId(ID_MAP_ROW + index);
        box.setClickable(true);
        box.setOnClickListener(this);
        return box;
    }

    private void toggleMap(final int index) {
        if (index < 0 || index >= maps.size()) {
            return;
        }
        final MapImporter.MapEntry e = maps.get(index);
        final boolean on = !e.listed;
        boolean ok = on
                ? MapImporter.registerCode(this, e.code)
                : MapImporter.unregisterCode(this, e.code);
        LogStore.event(this, "map " + e.code + " -> " + (on ? "enabled " : "disabled ") + ok);
        toast(ok ? t(on ? "mm_enable_ok" : "mm_disable_ok") : t("mm_op_failed"));
        showTab(TAB_MAPS);
        if (ok) {
            promptRestart();
        }
    }

    private void moveMap(final int index, final int delta) {
        if (index < 0 || index >= maps.size()) {
            return;
        }
        final MapImporter.MapEntry e = maps.get(index);
        boolean ok = MapImporter.moveCode(this, e.code, delta);
        if (!ok) {
            toast(t("mm_op_failed"));
            return;
        }
        LogStore.event(this, "map " + e.code + " move " + delta);
        showTab(TAB_MAPS);
        promptRestart();
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
                                                    promptRestart();
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
        if (metaOpen) {
            setStat(t("mm_meta_title"));
            showMetaRows();
            return;
        }
        setStat(VERSION + "  (" + BUILD + ")");
        content.addView(infoRow(ID_META, t("mm_about_meta"), VERSION + " / " + BUILD), cardLp());
        content.addView(infoRow(ID_SITE, t("mm_about_site"), URL_SITE), cardLp());
        content.addView(infoRow(ID_REPO, t("mm_about_repo"), URL_REPO), cardLp());
        content.addView(infoRow(ID_AUTHOR, t("mm_about_author"), URL_AUTHOR), cardLp());
        content.addView(infoRow(ID_BILI, t("mm_about_bili"), URL_BILI), cardLp());
        content.addView(infoRow(ID_AFDIAN, t("mm_about_afdian"), URL_AFDIAN), cardLp());
        content.addView(infoRow(ID_MAIL, t("mm_about_mail"), URL_MAIL), cardLp());
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
                            metaOpen = true;
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
            msg = t("mm_import_ok");
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
        page = 0;
        showTab(isMap ? TAB_MAPS : TAB_MODS);
        if (ok) {
            promptRestart();
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
            metaOpen = false;
            showTab(TAB_MODS);
        } else if (id == ID_TAB_MAPS) {
            metaOpen = false;
            showTab(TAB_MAPS);
        } else if (id == ID_TAB_ABOUT) {
            metaOpen = false;
            showTab(TAB_ABOUT);
        } else if (id == ID_BACK || id == ID_META_BACK) {
            if (id == ID_META_BACK) {
                metaOpen = false;
                showTab(TAB_ABOUT);
            } else {
                finish();
            }
        } else if (id == ID_IMPORT_MOD) {
            pick(REQ_PICK_MOD);
        } else if (id == ID_IMPORT_MAP) {
            pick(REQ_PICK_MAP);
        } else if (id == ID_OPEN_MODS) {
            openFolder(ModImporter.modRoot(this));
        } else if (id == ID_OPEN_MAPS) {
            openFolder(MapImporter.mapsRoot(this));
        } else if (id == ID_PREV) {
            if (page > 0) {
                page--;
            }
            showTab(TAB_MAPS);
        } else if (id == ID_NEXT) {
            int pages = Math.max(1, (maps.size() + PAGE_SIZE - 1) / PAGE_SIZE);
            if (page < pages - 1) {
                page++;
            }
            showTab(TAB_MAPS);
        } else if (id == ID_META) {
            metaOpen = true;
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
        } else if (id >= ID_SLOT_ON && id < ID_SLOT_ON + MAX_SLOTS) {
            toggleSlot(id - ID_SLOT_ON);
        } else if (id >= ID_SLOT_DEL && id < ID_SLOT_DEL + MAX_SLOTS) {
            askDeleteMod(id - ID_SLOT_DEL);
        } else if (id >= ID_MAP_ON && id < ID_MAP_ON + PAGE_SIZE) {
            toggleMap(page * PAGE_SIZE + id - ID_MAP_ON);
        } else if (id >= ID_MAP_UP && id < ID_MAP_UP + PAGE_SIZE) {
            moveMap(page * PAGE_SIZE + id - ID_MAP_UP, -1);
        } else if (id >= ID_MAP_DOWN && id < ID_MAP_DOWN + PAGE_SIZE) {
            moveMap(page * PAGE_SIZE + id - ID_MAP_DOWN, 1);
        } else if (id >= ID_MAP_ROW && id < ID_MAP_ROW + PAGE_SIZE) {
            int i = page * PAGE_SIZE + id - ID_MAP_ROW;
            if (i >= 0 && i < maps.size()) {
                mapActions(maps.get(i));
            }
        }
    }

    private String t(String key) {
        return L10n.t(this, key, key);
    }

    private void promptRestart() {
        try {
            new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle(t("mm_restart_title"))
                    .setMessage(t("mm_restart_msg"))
                    .setPositiveButton(t("mm_restart_now"), new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface d, int which) {
                            restartGame();
                        }
                    })
                    .setNegativeButton(t("mm_ok"), null)
                    .show();
        } catch (Throwable t) {
            toast(t("mm_restart_title"));
        }
    }

    private void restartGame() {
        try {
            Intent i = new Intent(this, MyGame.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
        } catch (Throwable t) {
        }
        finish();
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
