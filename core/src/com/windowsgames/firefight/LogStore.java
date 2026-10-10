package com.windowsgames.firefight;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Append-only event log of the manager, written next to the game data and mirrored into
 * MediaStore where the platform requires it.
 * Not implemented: log upload, size-based rotation.
 */

public final class LogStore {

    public static final String DIR_NAME = "Firefight-Mod-Manager";
    public static final int MAX_FILES = 10;
    private static final String PREFIX = "log_";

    private static final List<String> buffer = new ArrayList<String>();
    private static String baseName = null;
    private static File file = null;
    private static Uri uri = null;
    private static String shownPath = "";
    private static boolean ready = false;

    private LogStore() {
    }

    private static String stamp() {
        return new SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(new Date());
    }

    private static String newBaseName() {
        return PREFIX + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
    }

    public static File publicDir(Context c) {
        try {
            File dl = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (dl == null) {
                return null;
            }
            File d = new File(new File(dl, DIR_NAME), c.getPackageName());
            if (d.isDirectory() || d.mkdirs()) {
                return d;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    public static File fallbackDir(Context c) {
        try {
            File ext = c.getExternalFilesDir(null);
            if (ext == null) {
                return null;
            }
            File d = new File(new File(ext, DIR_NAME), c.getPackageName());
            if (d.isDirectory() || d.mkdirs()) {
                return d;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    public static synchronized void init(Context c) {
        if (ready || c == null) {
            return;
        }
        baseName = newBaseName();
        File d = publicDir(c);
        if (d != null) {
            file = new File(d, baseName + ".txt");
            shownPath = file.getAbsolutePath();
        }
        ready = true;
    }

    public static synchronized void event(Context c, String msg) {
        try {
            buffer.add(stamp() + "  " + msg);
        } catch (Throwable ignored) {
            return;
        }
        flush(c);
    }

    private static String joined() {
        StringBuilder sb = new StringBuilder(buffer.size() * 64);
        for (int i = 0; i < buffer.size(); i++) {
            sb.append(buffer.get(i)).append('\n');
        }
        return sb.toString();
    }

    private static void flush(Context c) {
        if (c == null) {
            return;
        }
        init(c);
        String text = joined();
        byte[] raw;
        try {
            raw = text.getBytes("UTF-8");
        } catch (Throwable t) {
            return;
        }
        if (file != null) {
            try {
                FileOutputStream fos = new FileOutputStream(file, false);
                fos.write(raw);
                fos.close();
                shownPath = file.getAbsolutePath();
                return;
            } catch (Throwable t) {
                file = null;
            }
        }
        if (uri != null) {
            try {
                OutputStream os = c.getContentResolver().openOutputStream(uri, "wt");
                os.write(raw);
                os.close();
                return;
            } catch (Throwable t) {
                uri = null;
            }
        }
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                ContentValues v = new ContentValues();
                v.put(MediaStore.MediaColumns.DISPLAY_NAME, baseName + ".txt");
                v.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
                v.put(MediaStore.MediaColumns.RELATIVE_PATH,
                        Environment.DIRECTORY_DOWNLOADS + "/" + DIR_NAME + "/" + c.getPackageName());
                Uri u = c.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                if (u != null) {
                    OutputStream os = c.getContentResolver().openOutputStream(u, "wt");
                    if (os != null) {
                        os.write(raw);
                        os.close();
                        uri = u;
                        shownPath = "Download/" + DIR_NAME + "/" + c.getPackageName() + "/" + baseName + ".txt";
                        return;
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        try {
            File d = fallbackDir(c);
            if (d != null) {
                File f = new File(d, baseName + ".txt");
                FileOutputStream fos = new FileOutputStream(f, false);
                fos.write(raw);
                fos.close();
                shownPath = f.getAbsolutePath();
            }
        } catch (Throwable ignored) {
        }
    }

    public static synchronized String path(Context c) {
        init(c);
        return shownPath == null ? "" : shownPath;
    }

    public static void prune(Context c) {
        try {
            File d = publicDir(c);
            if (d == null) {
                d = fallbackDir(c);
            }
            if (d == null) {
                return;
            }
            File[] all = d.listFiles();
            if (all == null) {
                return;
            }
            List<File> logs = new ArrayList<File>();
            for (int i = 0; i < all.length; i++) {
                File f = all[i];
                if (f.isFile() && f.getName().startsWith(PREFIX) && f.getName().endsWith(".txt")) {
                    logs.add(f);
                }
            }
            if (logs.size() <= MAX_FILES) {
                return;
            }
            File[] arr = logs.toArray(new File[logs.size()]);
            Arrays.sort(arr, new Comparator<File>() {
                public int compare(File a, File b) {
                    return b.getName().compareTo(a.getName());
                }
            });
            for (int i = MAX_FILES; i < arr.length; i++) {
                try {
                    arr[i].delete();
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
    }
}
