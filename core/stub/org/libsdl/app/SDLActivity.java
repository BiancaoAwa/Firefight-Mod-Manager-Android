package org.libsdl.app;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;

/**
 * 仅编译期使用的桩类：不参与 dex 打包（只出现在 javac 的 -cp 上）。
 * 运行时由 APK 内原有的 org.libsdl.app.SDLActivity 提供。
 * 这里声明的方法必须与真实 SDLActivity 的签名完全一致，
 * 且本类只声明 MyGame.java 实际会调用到的成员。
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
