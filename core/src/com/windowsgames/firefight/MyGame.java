package com.windowsgames.firefight;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import org.libsdl.app.SDLActivity;

import java.io.File;

/**
 * 需求③（第 3 轮）：启动入口 = SDLActivity（游戏本体）。
 *
 * 本类只做三件事：
 *   1. 让游戏照常启动（invoke-super SDLActivity.onCreate）；
 *   2. 在游戏界面上弹一个「是否使用模组管理器 / 是 否（15 秒退出）」提示框，
 *      选「是」打开 {@link ModManagerActivity}，选「否」或倒计时结束都直接进游戏；
 *   3. 提供 {@link #rootOfStatic(Context)} 给 ModImporter / MapImporter / LogStore 用。
 *
 * 第 3 轮规格：管理器本身是整屏界面，不再用 AlertDialog 堆菜单。
 */
public class MyGame extends SDLActivity {

    private static MyGame _cachedContext;

    /** BillingManager 等外部代码按这个签名静态调用，勿改。 */
    public static MyGame getContext() {
        return _cachedContext;
    }

    /** 提示框倒计时秒数（规格：15 秒退出）。 */
    private static final int PROMPT_SECONDS = 15;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private AlertDialog prompt;
    private int left = PROMPT_SECONDS;
    private boolean shown = false;

    // ------------------------------------------------------------ 生命周期

    protected void onCreate(Bundle savedInstanceState) {
        try {
            super.onCreate(savedInstanceState);
        } catch (Throwable t) {
        }
        _cachedContext = this;
        try {
            L10n.init(this);
            LogStore.init(this);
            LogStore.event(this, "== Firefight Mod Manager "
                    + ModManagerActivity.VERSION + " start ==");
            LogStore.prune(this);
        } catch (Throwable t) {
        }
        ui.postDelayed(new Runnable() {
            public void run() {
                showPrompt();
            }
        }, 600);
    }

    // ------------------------------------------------------------ 入口提示框

    private void showPrompt() {
        if (shown || isFinishing()) {
            return;
        }
        shown = true;
        try {
            AlertDialog.Builder b = new AlertDialog.Builder(
                    this, android.R.style.Theme_DeviceDefault_Dialog_Alert);
            b.setTitle(t("mm_prompt_title"));
            b.setMessage(promptText(PROMPT_SECONDS));
            b.setCancelable(false);
            b.setPositiveButton(t("mm_yes"), new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int which) {
                    openManager();
                }
            });
            b.setNegativeButton(t("mm_no"), null);
            prompt = b.create();
            prompt.setCancelable(false);
            prompt.show();
            ui.postDelayed(tick, 1000);
        } catch (Throwable t) {
        }
    }

    private final Runnable tick = new Runnable() {
        public void run() {
            if (prompt == null || !prompt.isShowing()) {
                return;
            }
            left--;
            if (left <= 0) {
                try {
                    prompt.dismiss();
                } catch (Throwable t) {
                }
                try {
                    LogStore.event(MyGame.this, "entry prompt timed out; game continues");
                } catch (Throwable t) {
                }
                return;
            }
            try {
                prompt.setMessage(promptText(left));
            } catch (Throwable t) {
            }
            ui.postDelayed(this, 1000);
        }
    };

    private String promptText(int seconds) {
        return t("mm_prompt_msg") + "\n\n" + t("mm_prompt_auto") + seconds + t("mm_prompt_sec");
    }

    private void openManager() {
        try {
            startActivity(new Intent(this, ModManagerActivity.class));
        } catch (Throwable t) {
            try {
                LogStore.event(this, "open manager failed: " + t);
            } catch (Throwable t2) {
            }
        }
    }

    private String t(String key) {
        return L10n.t(this, key, key);
    }

    // ------------------------------------------------------------ 存储根目录

    /** &lt;files&gt; 根目录，三级退化；ModImporter / MapImporter / LogStore 都用这个。 */
    public static File rootOfStatic(Context c) {
        if (c == null) {
            return null;
        }
        try {
            File d = new File("/storage/emulated/0/Android/data/"
                    + c.getPackageName() + "/files");
            if (d.isDirectory() || d.mkdirs()) {
                return d;
            }
        } catch (Throwable ignored) {
        }
        try {
            File d = c.getExternalFilesDir(null);
            if (d != null && (d.isDirectory() || d.mkdirs())) {
                return d;
            }
        } catch (Throwable ignored) {
        }
        try {
            File d = new File(c.getApplicationInfo().dataDir, "files");
            if (d.isDirectory() || d.mkdirs()) {
                return d;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}
