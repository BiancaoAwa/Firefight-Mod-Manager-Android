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
 * Game entry point: extends the SDL activity, shows the launch prompt and opens the manager.
 * Reading the mod data is the game's own native code, not this class.
 */

public class MyGame extends SDLActivity {

    private static MyGame _cachedContext;

    public static MyGame getContext() {
        return _cachedContext;
    }

    private static final int PROMPT_SECONDS = 15;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private AlertDialog prompt;
    private int left = PROMPT_SECONDS;
    private boolean shown = false;

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
