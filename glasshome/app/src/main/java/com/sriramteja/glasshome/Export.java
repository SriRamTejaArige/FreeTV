package com.sriramteja.glasshome;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Leaves what the phone app needs in this app's own folder (/sdcard/Android/data/com.sriramteja.glasshome/files):
 * apps.json with every app's name, icons/<package>.png, wallpaper thumbnails thumbs/<picture>.jpg and
 * walls.json. Only redone when something changed.
 */
final class Export {
    private Export() { }

    private static String lastApps = "", lastWalls = "";

    /** Pictures/GlassHome/.studio (readable by adb on every Android), or this app's own folder. */
    static File dir(Context ctx) {
        File d = new File(Store.dir(), ".studio");
        //noinspection ResultOfMethodCallIgnored
        d.mkdirs();
        if (d.isDirectory() && d.canWrite()) {
            File nm = new File(d, ".nomedia");
            try {
                if (!nm.isFile()) //noinspection ResultOfMethodCallIgnored
                    nm.createNewFile();
            } catch (Exception ignored) {
            }
            return d;
        }
        return ctx.getExternalFilesDir(null);
    }

    static synchronized void apps(Context ctx) {
        try {
            File dir = dir(ctx);
            if (dir == null) return;
            PackageManager pm = ctx.getPackageManager();
            Map<String, ResolveInfo> found = new LinkedHashMap<>();
            Set<String> tv = new HashSet<>();
            for (String cat : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
                for (ResolveInfo ri : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(cat), 0)) {
                    ActivityInfo ai = ri.activityInfo;
                    if (ai == null || found.containsKey(ai.packageName)) continue;
                    if (ai.packageName.equals(ctx.getPackageName())) continue;
                    found.put(ai.packageName, ri);
                    if (cat.equals(Intent.CATEGORY_LEANBACK_LAUNCHER)) tv.add(ai.packageName);
                }
            }
            List<String> pkgs = new ArrayList<>(found.keySet());
            Collections.sort(pkgs);
            String sig = String.join(",", pkgs);
            File json = new File(dir, "apps.json");
            if (sig.equals(lastApps) && json.isFile()) return;
            File icons = new File(dir, "icons");
            //noinspection ResultOfMethodCallIgnored
            icons.mkdirs();
            File banners = new File(dir, "banners");
            //noinspection ResultOfMethodCallIgnored
            banners.mkdirs();
            JSONArray out = new JSONArray();
            for (String p : pkgs) {
                ResolveInfo ri = found.get(p);
                JSONObject o = new JSONObject();
                o.put("pkg", p);
                o.put("label", String.valueOf(ri.loadLabel(pm)));
                o.put("tv", tv.contains(p));
                out.put(o);
                File bpng = new File(banners, p + ".png");
                if (!bpng.isFile()) {
                    Drawable bd = ri.activityInfo.loadBanner(pm);
                    if (bd == null) bd = ri.activityInfo.applicationInfo.loadBanner(pm);
                    if (bd != null) {
                        Bitmap bb = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888);
                        bd.setBounds(0, 0, 320, 180);
                        bd.draw(new Canvas(bb));
                        try (OutputStream s = new FileOutputStream(bpng)) {
                            bb.compress(Bitmap.CompressFormat.PNG, 100, s);
                        }
                        bb.recycle();
                    }
                }
                File png = new File(icons, p + ".png");
                if (!png.isFile()) {
                    Bitmap b = icon(ri.loadIcon(pm), 112);
                    if (b != null) {
                        try (OutputStream s = new FileOutputStream(png)) {
                            b.compress(Bitmap.CompressFormat.PNG, 100, s);
                        }
                        b.recycle();
                    }
                }
            }
            write(json, out.toString(1));
            lastApps = sig;
        } catch (Exception ignored) {
        }
    }

    private static Bitmap icon(Drawable d, int size) {
        if (d == null) return null;
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        if (d instanceof AdaptiveIconDrawable) {
            AdaptiveIconDrawable a = (AdaptiveIconDrawable) d;
            int k = size / 4;
            for (Drawable l : new Drawable[]{a.getBackground(), a.getForeground()}) {
                if (l == null) continue;
                l.setBounds(-k, -k, size + k, size + k);
                l.draw(c);
            }
        } else {
            d.setBounds(0, 0, size, size);
            d.draw(c);
        }
        return b;
    }

    static synchronized void thumbs(Context ctx) {
        try {
            File dir = dir(ctx);
            if (dir == null) return;
            List<File> walls = Store.wallpapers();
            StringBuilder sig = new StringBuilder();
            for (File f : walls) sig.append(f.getName()).append(':').append(f.length()).append(',');
            File json = new File(dir, "walls.json");
            if (sig.toString().equals(lastWalls) && json.isFile()) return;
            File thumbs = new File(dir, "thumbs");
            //noinspection ResultOfMethodCallIgnored
            thumbs.mkdirs();
            Set<String> keep = new HashSet<>();
            JSONArray out = new JSONArray();
            SharedPreferences lum = ctx.getSharedPreferences("glass_lum", Context.MODE_PRIVATE);
            for (File f : walls) {
                String t = f.getName() + ".jpg";
                keep.add(t);
                File tf = new File(thumbs, t);
                if (!tf.isFile() || tf.lastModified() < f.lastModified()) {
                    BitmapFactory.Options o = new BitmapFactory.Options();
                    o.inJustDecodeBounds = true;
                    BitmapFactory.decodeFile(f.getPath(), o);
                    int sample = 1;
                    while (o.outWidth / (sample * 2) >= 320) sample *= 2;
                    BitmapFactory.Options d = new BitmapFactory.Options();
                    d.inSampleSize = sample;
                    Bitmap b = BitmapFactory.decodeFile(f.getPath(), d);
                    if (b != null) {
                        int w = 320, h = Math.max(1, Math.round(320f * b.getHeight() / b.getWidth()));
                        Bitmap s = Bitmap.createScaledBitmap(b, w, h, true);
                        try (OutputStream os = new FileOutputStream(tf)) {
                            s.compress(Bitmap.CompressFormat.JPEG, 80, os);
                        }
                        if (s != b) s.recycle();
                        b.recycle();
                    }
                }
                String key = f.getName() + ":" + f.length();
                int l = lum.getInt(key, -2);
                if (l == -2) {
                    l = Noir.brightness(f);
                    lum.edit().putInt(key, l).apply();
                }
                JSONObject o = new JSONObject();
                o.put("name", f.getName());
                o.put("bytes", f.length());
                o.put("brightness", l);
                out.put(o);
            }
            File[] old = thumbs.listFiles();
            if (old != null) for (File f : old) if (!keep.contains(f.getName())) //noinspection ResultOfMethodCallIgnored
                f.delete();
            write(json, out.toString(1));
            lastWalls = sig.toString();
        } catch (Exception ignored) {
        }
    }

    /** The wallpaper on screen now, so the phone previews with the same one. */
    static void current(Context ctx, String name) {
        try {
            File dir = dir(ctx);
            if (dir != null) write(new File(dir, "current.txt"), name == null ? "" : name);
        } catch (Exception ignored) {
        }
    }

    private static void write(File f, String text) throws Exception {
        File tmp = new File(f.getPath() + ".tmp");
        try (OutputStream s = new FileOutputStream(tmp)) {
            s.write(text.getBytes(StandardCharsets.UTF_8));
        }
        //noinspection ResultOfMethodCallIgnored
        tmp.renameTo(f);
    }
}
