package com.sriramteja.glasshome;

import android.accessibilityservice.AccessibilityService;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

import org.json.JSONObject;

/**
 * Remote buttons that open an app of your choice: config "remote_keys" = {"KEYCODE_XYZ" or "123": "package"}.
 * Only the listed buttons are touched; every other key goes on to the TV as normal.
 */
public class KeyService extends AccessibilityService {

    private static JSONObject map = new JSONObject();
    private static String stamp = "";
    private static long checkedAt;

    /** The button map from the settings, re-read at most every 2 seconds and only when the file changed. */
    static JSONObject keys() {
        long now = System.currentTimeMillis();
        if (now - checkedAt > 2000) {
            checkedAt = now;
            String s = Store.stamp();
            if (!s.equals(stamp)) {
                stamp = s;
                JSONObject m = Store.config().optJSONObject("remote_keys");
                map = m == null ? new JSONObject() : m;
            }
        }
        return map;
    }

    static String target(int keyCode) {
        JSONObject m = keys();
        if (m.length() == 0) return null;
        String p = m.optString(KeyEvent.keyCodeToString(keyCode), "");
        if (p.isEmpty()) p = m.optString(String.valueOf(keyCode), "");
        return p.isEmpty() ? null : p;
    }

    @Override
    protected boolean onKeyEvent(KeyEvent e) {
        // game controllers keep all their buttons
        if ((e.getSource() & android.view.InputDevice.SOURCE_GAMEPAD) == android.view.InputDevice.SOURCE_GAMEPAD
                || (e.getSource() & android.view.InputDevice.SOURCE_JOYSTICK) == android.view.InputDevice.SOURCE_JOYSTICK) return false;
        String pkg = target(e.getKeyCode());
        if (pkg == null) return false;
        if (e.getAction() == KeyEvent.ACTION_UP && e.getRepeatCount() == 0) open(pkg);
        return true;
    }

    private void open(String pkg) {
        PackageManager pm = getPackageManager();
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = pm.getLaunchIntentForPackage(pkg);
        if (i == null) return;
        try {
            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
        } catch (ActivityNotFoundException | SecurityException ignored) { }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) { }

    @Override
    public void onInterrupt() { }
}
