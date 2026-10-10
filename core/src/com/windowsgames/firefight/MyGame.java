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
 * Requirement 3 (round 3): the launch entry point = SDLActivity (the game itself).
 *
 * This class does only three things:
 *   1. let the game start as usual (invoke-super SDLActivity.onCreate);
 *   2. show a prompt on top of the game: "use the mod manager? / Yes No (exit after 15 s)",
 *      where "Yes" opens {@link ModManagerActivity} and "No" or the countdown expiring goes straight into the game;
 *   3. provide {@link #rootOfStatic(Context)} to ModImporter / MapImporter / LogStore.
 *
 * Round-3 spec: the manager itself is a full-screen UI, no more menus stacked in an AlertDialog.
 */
public class MyGame extends SDLActivity {

    private static MyGame _cachedContext;

    /** External code such as BillingManager calls this statically by signature -- do not change. */
    public static MyGame getContext() {
        return _cachedContext;
    }

    /** Countdown seconds of the prompt (spec: exit after 15 s). */
    private static final int PROMPT_SECONDS = 15;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private AlertDialog prompt;
    private int left = PROMPT_SECONDS;
    private boolean shown = false;

    // ------------------------------------------------------------ lifecycle

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

    // ------------------------------------------------------------ launch prompt

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

    // ------------------------------------------------------------ storage root

    /** &lt;files&gt; root directory with a three-level fallback; ModImporter / MapImporter / LogStore all use it. */
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
