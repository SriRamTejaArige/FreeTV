package com.sriramteja.freetv;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Runs a phone widget's button: one adb command to the TV over the home Wi-Fi (same key as the app). */
public class WidgetAction extends BroadcastReceiver {

    private static final String ACTION = "com.sriramteja.freetv.WIDGET";
    private static final ExecutorService ONE = Executors.newSingleThreadExecutor();
    private static AdbLink link;

    static PendingIntent pending(Context ctx, String what, int code) {
        Intent i = new Intent(ctx, WidgetAction.class).setAction(ACTION + "." + what).putExtra("what", what);
        return PendingIntent.getBroadcast(ctx, code, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String what = intent.getStringExtra("what");
        if (what == null) return;
        Context ctx = context.getApplicationContext();
        PendingResult result = goAsync();
        ONE.execute(() -> {
            try {
                String said = run(ctx, what);
                if (said != null) QuickWidget.say(ctx, said);
            } catch (Exception e) {
                synchronized (WidgetAction.class) {
                    link = null;
                }
                QuickWidget.say(ctx, ctx.getSharedPreferences("freetv", Context.MODE_PRIVATE).getString("ip", "").isEmpty()
                        ? "Open FreeTV and connect a TV first" : "TV not reachable. Is it on?");
            } finally {
                result.finish();
            }
        });
    }

    private static Tv tv(Context ctx) throws Exception {
        synchronized (WidgetAction.class) {
            String ip = ctx.getSharedPreferences("freetv", Context.MODE_PRIVATE).getString("ip", "");
            if (ip.isEmpty()) throw new IllegalStateException("no TV");
            String host = ip.contains(":") ? ip.substring(0, ip.indexOf(':')) : ip;
            int port = 5555;
            if (ip.contains(":")) {
                try {
                    port = Integer.parseInt(ip.substring(ip.indexOf(':') + 1));
                } catch (NumberFormatException ignored) { }
            }
            if (link == null) link = new AdbLink(ctx.getFilesDir());
            if (!link.isConnected()) link.connect(host, port, () -> QuickWidget.say(ctx, "On the TV, press Allow"));
            return new Tv(link);
        }
    }

    private static String run(Context ctx, String what) throws Exception {
        Tv tv = tv(ctx);
        if (what.startsWith("key:")) {
            tv.key(what.substring(4));
            return null;
        }
        switch (what) {
            case "saver":
                tv.testFace(null);
                return "Screensaver on. Any remote button closes it";
            case "free":
                return tv.closeAll();
            case "theme":
                return nextTheme(ctx, tv);
            default:
                return null;
        }
    }

    /** The next theme in the list, sent to the TV (the same as picking it in the app). */
    private static String nextTheme(Context ctx, Tv tv) throws Exception {
        File draft = new File(ctx.getFilesDir(), "config.json");
        JSONObject raw;
        try {
            raw = new JSONObject(new String(AdbLink.readAll(draft), StandardCharsets.UTF_8));
        } catch (Exception e) {
            raw = tv.readConfig();
        }
        int i = Arrays.asList(Themes.IDS).indexOf(raw.optString("theme", "glass"));
        String next = Themes.IDS[(i + 1) % Themes.IDS.length];
        for (String k : MainActivity.STYLE_KEYS) raw.remove(k);
        raw.remove("faces");
        raw.put("theme", next);
        try (FileOutputStream o = new FileOutputStream(draft)) {
            o.write(raw.toString(1).getBytes(StandardCharsets.UTF_8));
        }
        SharedPreferences p = ctx.getSharedPreferences("freetv", Context.MODE_PRIVATE);
        p.edit().putBoolean("have_draft", true).apply();
        tv.sendConfig(raw, true);
        return "Look: " + Themes.name(next);
    }
}
