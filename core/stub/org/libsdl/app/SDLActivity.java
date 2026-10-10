package org.libsdl.app;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;

/**
 * Compile-time stand-in for the SDL activity of the game APK. The real class is used at runtime,
 * so this file is never packaged.
 */

public class SDLActivity extends Activity {

    public static Context getContext() {
        return null;
    }

    public static void openURL(String url) {
    }

    public static void minimizeWindow() {
    }

    public static void manualBackButton() {
    }

    public static boolean sendMessage(int command, int param) {
        return false;
    }

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    protected void onResume() {
        super.onResume();
    }

    protected void onPause() {
        super.onPause();
    }

    protected void onDestroy() {
        super.onDestroy();
    }

    public void onBackPressed() {
        super.onBackPressed();
    }
}
