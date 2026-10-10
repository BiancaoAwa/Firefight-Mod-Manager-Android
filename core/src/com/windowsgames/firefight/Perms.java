package com.windowsgames.firefight;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;

/** Requirement 6: storage permission (all files access / WRITE_EXTERNAL_STORAGE) checks and requests. */
final class Perms {

    private Perms() {
    }

    /** Whether locations outside <files> can be read and written. */
    static boolean has(Context c) {
        if (c == null) {
            return false;
        }
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                return Environment.isExternalStorageManager();
            }
            return c.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Open the system settings page (API 30+) or request the runtime permission (older versions). */
    static void request(Activity a) {
        if (a == null) {
            return;
        }
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                Intent i = new Intent("android.settings.MANAGE_APP_ALL_FILES_ACCESS_PERMISSION");
                i.setData(Uri.parse("package:" + a.getPackageName()));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                a.startActivity(i);
            } else {
                a.requestPermissions(
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 0x64);
            }
        } catch (Throwable ignored) {
        }
    }
}
