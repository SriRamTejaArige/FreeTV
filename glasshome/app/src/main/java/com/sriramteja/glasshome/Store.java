package com.sriramteja.glasshome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Environment;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Settings and pictures. Everything lives in /sdcard/Pictures/GlassHome so the laptop (adb push) and the
 * phone app can change it: config.json and a wallpapers/ folder. Choices made on the TV (app order, hidden
 * apps) are kept in this app's own preferences.
 */
final class Store {
    private Store() { }

    static File dir() {
        return new File(Environment.getExternalStorageDirectory(), "Pictures/GlassHome");
    }

    private static String cachedKey = "";
    private static long cachedAt;
    private static JSONObject cachedRaw = new JSONObject();

    /** The settings file as written (no theme defaults). */
    static synchronized JSONObject rawConfig() {
        // Android 11 hides other apps' non-picture files, so the laptop/phone also put a copy in this app's folder.
        File own = new File(Environment.getExternalStorageDirectory(),
                "Android/data/com.sriramteja.glasshome/files/config.json");
        File pics = new File(dir(), "config.json");
        boolean ownOk = own.isFile() && own.canRead(), picsOk = pics.isFile() && pics.canRead();
        // whichever copy was written last (the phone and the laptop write both)
        File f = ownOk && (!picsOk || own.lastModified() > pics.lastModified()) ? own : pics;
        if (!f.isFile() || f.length() > 256 * 1024) return new JSONObject();
        // adb push keeps the laptop file's time, so two different files can share time and size: the
        // cache is only trusted for a second, then the file is read again (it is tiny).
        String key = f.getPath() + ":" + f.lastModified() + ":" + f.length();
        long now = android.os.SystemClock.uptimeMillis();
        if (key.equals(cachedKey) && now - cachedAt < 1000) return cachedRaw;
        cachedAt = now;
        try (InputStream in = new FileInputStream(f)) {
            byte[] b = new byte[(int) f.length()];
            int n = 0, r;
            while (n < b.length && (r = in.read(b, n, b.length - n)) > 0) n += r;
            cachedRaw = new JSONObject(new String(b, 0, n, StandardCharsets.UTF_8));
            cachedKey = key;
        } catch (Exception e) {
            cachedRaw = new JSONObject();
            cachedKey = "";
        }
        return cachedRaw;
    }

    /** The settings in force: the chosen theme's defaults with the file's settings over them. */
    static JSONObject config() {
        return Themes.merge(rawConfig());
    }

    static List<String> strings(JSONObject o, String key, String... fallback) {
        JSONArray a = o.optJSONArray(key);
        List<String> out = new ArrayList<>();
        if (a == null) {
            out.addAll(Arrays.asList(fallback));
        } else {
            for (int i = 0; i < a.length(); i++) out.add(a.optString(i));
        }
        return out;
    }

    static List<File> wallpapers() {
        List<File> out = new ArrayList<>();
        for (File d : new File[]{new File(dir(), "wallpapers"), dir()}) {
            File[] f = d.listFiles((x, n) -> {
                String l = n.toLowerCase();
                return l.endsWith(".jpg") || l.endsWith(".jpeg") || l.endsWith(".png") || l.endsWith(".webp");
            });
            if (f != null && f.length > 0) {
                Arrays.sort(f);
                out.addAll(Arrays.asList(f));
                break;
            }
        }
        return out;
    }

    /**
     * The picture to show now. A new one is picked when config "wallpaper_minutes" have passed (0 = keep the
     * same one), shuffled unless "wallpaper_order" is "sequential". The choice survives restarts.
     */
    static File currentWallpaper(Context ctx) {
        List<File> all = wallpapers();
        if (config().optBoolean("dark_only", false)) all = darkOnly(ctx, all);
        if (all.isEmpty()) return null;
        JSONObject c = config();
        SharedPreferences p = ctx.getSharedPreferences("glass", Context.MODE_PRIVATE);
        String name = p.getString("wallpaper", "");
        long since = p.getLong("wallpaper_at", 0);
        int minutes = c.optInt("wallpaper_minutes", 5);
        File current = null;
        for (File f : all) if (f.getName().equals(name)) current = f;
        boolean due = current == null || (minutes > 0 && System.currentTimeMillis() - since >= minutes * 60_000L);
        String fixed = c.optString("wallpaper_fixed", "");
        if (!fixed.isEmpty()) {
            for (File f : all) if (f.getName().equals(fixed)) return f;
        }
        if (!due) return current;
        File next;
        if ("sequential".equals(c.optString("wallpaper_order", "shuffle"))) {
            int i = current == null ? -1 : all.indexOf(current);
            next = all.get((i + 1) % all.size());
        } else {
            Random r = new Random();
            next = all.get(r.nextInt(all.size()));
            if (all.size() > 1 && next.equals(current)) next = all.get((all.indexOf(next) + 1) % all.size());
        }
        p.edit().putString("wallpaper", next.getName()).putLong("wallpaper_at", System.currentTimeMillis()).apply();
        return next;
    }

    /** Only the dark pictures (average brightness under config "dark_level", default 45 of 255), or all if none. */
    static List<File> darkOnly(Context ctx, List<File> all) {
        SharedPreferences p = ctx.getSharedPreferences("glass_lum", Context.MODE_PRIVATE);
        int limit = config().optInt("dark_level", 45);
        List<File> dark = new ArrayList<>();
        SharedPreferences.Editor e = null;
        for (File f : all) {
            String key = f.getName() + ":" + f.length();
            int lum = p.getInt(key, -2);
            if (lum == -2) {
                lum = Noir.brightness(f);
                if (e == null) e = p.edit();
                e.putInt(key, lum);
            }
            if (lum >= 0 && lum < limit) dark.add(f);
        }
        if (e != null) e.apply();
        return dark.isEmpty() ? all : dark;
    }

    /** Millis until the next picture is due, or -1 when it never changes. */
    static long nextChangeIn(Context ctx) {
        int minutes = config().optInt("wallpaper_minutes", 5);
        if (minutes <= 0 || !config().optString("wallpaper_fixed", "").isEmpty()) return -1;
        long since = ctx.getSharedPreferences("glass", Context.MODE_PRIVATE).getLong("wallpaper_at", 0);
        return Math.max(5_000L, minutes * 60_000L - (System.currentTimeMillis() - since));
    }

    /** Decodes a picture filling w x h exactly (centre-cropped), or null. */
    static Bitmap decode(File f, int w, int h) {
        if (f == null || w <= 0 || h <= 0) return null;
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getPath(), o);
            if (o.outWidth <= 0) return null;
            int sample = 1;
            while (o.outWidth / (sample * 2) >= w && o.outHeight / (sample * 2) >= h) sample *= 2;
            BitmapFactory.Options d = new BitmapFactory.Options();
            d.inSampleSize = sample;
            d.inPreferredConfig = Bitmap.Config.RGB_565;
            Bitmap src = BitmapFactory.decodeFile(f.getPath(), d);
            if (src == null) return null;
            Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565);
            float scale = Math.max((float) w / src.getWidth(), (float) h / src.getHeight());
            int cw = Math.round(w / scale), ch = Math.round(h / scale);
            int cx = (src.getWidth() - cw) / 2, cy = (src.getHeight() - ch) / 2;
            new Canvas(out).drawBitmap(src, new Rect(cx, cy, cx + cw, cy + ch), new Rect(0, 0, w, h),
                    new Paint(Paint.FILTER_BITMAP_FLAG));
            src.recycle();
            return out;
        } catch (OutOfMemoryError | RuntimeException e) {
            return null;
        }
    }
}
