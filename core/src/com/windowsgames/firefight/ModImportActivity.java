package com.windowsgames.firefight;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import java.io.InputStream;

/**
 * Activity that receives a mod file shared into the app or picked from the manager and imports it
 * into the mod library.
 * Not implemented: importing several files at once.
 */

public class ModImportActivity extends Activity implements DialogInterface.OnClickListener {

    private Uri uri;
    private String name = "mod.zip";
    private AlertDialog dialog;
    private final Handler ui = new Handler(Looper.getMainLooper());

    protected void onCreate(Bundle b) {
        super.onCreate(b);
        try {
            Intent it = getIntent();
            if (it != null) {
                if (it.getData() != null) {
                    uri = it.getData();
                } else if (it.getParcelableExtra(Intent.EXTRA_STREAM) != null) {
                    uri = (Uri) it.getParcelableExtra(Intent.EXTRA_STREAM);
                }
            }
            if (uri != null) {
                String s = uri.getLastPathSegment();
                if (s != null) {
                    int slash = s.lastIndexOf('/');
                    name = slash >= 0 ? s.substring(slash + 1) : s;
                }
            }
        } catch (Throwable ignored) {
        }
        if (uri == null) {
            toast("No mod package received");
            finish();
            return;
        }
        try {
            LogStore.event(this, "ModImportActivity: " + name);
        } catch (Throwable ignored) {
        }
        try {
            AlertDialog.Builder bld = new AlertDialog.Builder(this,
                    android.R.style.Theme_DeviceDefault_Dialog_Alert);
            bld.setTitle("Firefight Mod Manager");
            bld.setMessage("Import this mod package?\n" + name);
            bld.setPositiveButton("OK", this);
            bld.setNegativeButton("Cancel", this);
            dialog = bld.create();
            dialog.setCancelable(false);
            dialog.show();
        } catch (Throwable t) {
            doImport();
        }
    }

    public void onClick(DialogInterface d, int which) {
        if (which == DialogInterface.BUTTON_POSITIVE) {
            doImport();
        } else {
            finish();
        }
    }

    private void doImport() {
        toast("Importing...");
        new Thread(new Runnable() {
            public void run() {
                final String r;
                try {
                    InputStream in = getContentResolver().openInputStream(uri);
                    r = ModImporter.importMod(ModImportActivity.this, in, name);
                } catch (Throwable t) {
                    ui.post(new Runnable() {
                        public void run() {
                            toast("Import failed");
                            finish();
                        }
                    });
                    return;
                }
                ui.post(new Runnable() {
                    public void run() {
                        try {
                            LogStore.event(ModImportActivity.this, "import " + name + " -> " + r);
                        } catch (Throwable ignored) {
                        }
                        toast("ok".equals(r) ? "Mod imported: " + name : "Import failed");
                        try {
                            if (dialog != null && dialog.isShowing()) {
                                dialog.dismiss();
                            }
                        } catch (Throwable ignored) {
                        }
                        finish();
                    }
                });
            }
        }).start();
    }

    private void toast(String s) {
        try {
            Toast.makeText(this, s, Toast.LENGTH_LONG).show();
        } catch (Throwable ignored) {
        }
    }
}
