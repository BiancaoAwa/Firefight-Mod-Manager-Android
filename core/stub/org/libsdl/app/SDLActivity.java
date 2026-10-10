package org.libsdl.app;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;

/**
 * Compile-time-only stub: it is never packaged into the dex (it only appears on javac's -cp).
 * At runtime the original org.libsdl.app.SDLActivity of the APK provides the real class.
 * The methods declared here must match the real SDLActivity signatures exactly,
 * and this class declares only the members that MyGame.java actually calls.
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
