package com.sriramteja.glasshome;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LightingColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * The screensaver watch faces, drawn straight onto a canvas so the very same code runs in the TV's
 * screensaver and in the phone app's preview. Each face has its own defaults; every "ss_" setting in the
 * config can override them.
 */
final class Faces {

    static final String[] IDS = {"glass", "noir", "neon", "analog", "minimal", "photo", "dot", "material"};
    static final String[] NAMES = {"Glass clock", "Noir", "Neon ring", "Analog watch", "Minimal", "Photo slideshow",
            "Dot matrix", "Material"};

    /** What a face needs besides the settings. */
    static final class Scene {
        Bitmap wall;          // the wallpaper now
        Bitmap prev;          // the one before (photo face cross-fade), may be null
        float fade = 1f;      // 0..1: how far the new wallpaper has faded in over prev
        int photoIndex;       // which picture of the slideshow this is (pan direction)
        float photoT;         // seconds since this picture appeared
        int accent;           // the wallpaper's own colour, 0 if none
        String sun;           // "06:14   17:44" style line, or null
        String weather;       // "24°  Partly cloudy", or null
        long now = System.currentTimeMillis();
        float t;              // seconds since the face started
    }

    /** Defaults per face. */
    static JSONObject defaults(String face) {
        JSONObject d = new JSONObject();
        try {
            d.put("ss_dim", 0).put("ss_date", true).put("ss_sun", true).put("ss_bubbles", false)
                    .put("ss_seconds", true).put("ss_wallpaper", true).put("ss_color", "auto")
                    .put("ss_photo_seconds", 20).put("ss_smooth", true).put("ss_numerals", false)
                    .put("ss_weather", true);
            switch (face) {
                case "noir":
                    d.put("ss_dim", 55).put("ss_font", "light").put("ss_clock_size", 24).put("ss_clock_top", 74)
                            .put("ss_align", "left");
                    break;
                case "neon":
                    d.put("ss_dim", 78).put("ss_font", "thin").put("ss_clock_size", 62).put("ss_clock_top", 50)
                            .put("ss_align", "center");
                    break;
                case "analog":
                    d.put("ss_dim", 45).put("ss_font", "medium").put("ss_clock_size", 64).put("ss_clock_top", 50)
                            .put("ss_align", "center").put("ss_sun", false);
                    break;
                case "minimal":
                    d.put("ss_dim", 20).put("ss_font", "thin").put("ss_clock_size", 8).put("ss_clock_top", 84)
                            .put("ss_align", "right").put("ss_sun", false).put("ss_seconds", false);
                    break;
                case "dot":
                    d.put("ss_dim", 88).put("ss_font", "medium").put("ss_clock_size", 26).put("ss_clock_top", 44)
                            .put("ss_align", "center").put("ss_sun", false).put("ss_seconds", true).put("ss_color", "white");
                    break;
                case "material":
                    d.put("ss_dim", 55).put("ss_font", "regular").put("ss_clock_size", 30).put("ss_clock_top", 50)
                            .put("ss_align", "left").put("ss_sun", false).put("ss_seconds", false);
                    break;
                case "photo":
                    d.put("ss_dim", 0).put("ss_font", "light").put("ss_clock_size", 9).put("ss_clock_top", 84)
                            .put("ss_align", "left").put("ss_sun", false).put("ss_seconds", false);
                    break;
                default: // glass
                    d.put("ss_font", "medium").put("ss_clock_size", 15).put("ss_clock_top", 48)
                            .put("ss_align", "center").put("ss_bubbles", true).put("ss_seconds", false);
                    break;
            }
        } catch (JSONException ignored) {
        }
        return d;
    }

    /** How long until the next frame is worth drawing: only as often as something on screen moves. */
    static long frameDelay(String face, JSONObject cfg) {
        long now = System.currentTimeMillis();
        Faces f = new Faces(face, cfg);
        long nextSecond = 1000 - now % 1000 + 5, nextMinute = 60_000 - now % 60_000 + 5;
        boolean rolling = now % 1000 < 450;
        switch (face) {
            case "photo": return 50;
            case "glass": return f.b("ss_bubbles") ? 66 : f.b("ss_seconds") ? nextSecond : nextMinute;
            case "neon": return f.b("ss_smooth") ? 50 : nextSecond;
            case "analog": return !f.b("ss_seconds") ? nextMinute : f.b("ss_smooth") ? 50 : nextSecond;
            case "dot": return f.b("ss_seconds") ? nextSecond : nextMinute;
            case "material": return nextMinute;
            default: // noir, minimal: seconds roll for under half a second, then nothing moves
                return !f.b("ss_seconds") ? nextMinute : rolling ? 33 : nextSecond;
        }
    }

    // ------------------------------------------------------------------ setup

    private final String face;
    private final JSONObject cfg, def, own;
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pic = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Rect src = new Rect();
    private final RectF dst = new RectF(), box = new RectF();
    private final Path path = new Path();

    Faces(String face, JSONObject cfg) {
        this.face = face == null ? "glass" : face;
        this.cfg = cfg == null ? new JSONObject() : cfg;
        this.def = defaults(this.face);
        JSONObject all = this.cfg.optJSONObject("faces");
        JSONObject mine = all == null ? null : all.optJSONObject(this.face);
        this.own = mine == null ? new JSONObject() : mine;
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
    }

    /** Setting lookup: this face's own settings, then shared "ss_" settings, then the face's defaults. */
    private Object raw(String k) {
        if (own.has(k)) return own.opt(k);
        if (cfg.has(k)) return cfg.opt(k);
        return def.opt(k);
    }

    private boolean b(String k) {
        Object v = raw(k);
        return v instanceof Boolean ? (Boolean) v : "true".equals(String.valueOf(v));
    }

    private float f(String k) {
        Object v = raw(k);
        if (v instanceof Number) return ((Number) v).floatValue();
        try {
            return Float.parseFloat(String.valueOf(v));
        } catch (RuntimeException e) {
            return (float) def.optDouble(k, 0);
        }
    }

    private String s(String k) {
        Object v = raw(k);
        return v == null ? "" : String.valueOf(v);
    }

    /** The value in force for a setting of this face (for the phone's controls). */
    static Object value(String face, JSONObject cfg, String key) {
        return new Faces(face, cfg).raw(key);
    }

    private int color(Scene sc, int fallbackWhenAuto) {
        String c = s("ss_color");
        if ("white".equals(c)) return Color.WHITE;
        if ("accent".equals(c) || "auto".equals(c) || c.isEmpty()) {
            String fixed = cfg.optString("accent", "auto");
            int ac = "auto".equals(fixed) ? sc.accent : parse(fixed, sc.accent);
            if ("accent".equals(c)) return ac != 0 ? ac : 0xFF39E6FF;
            return fallbackWhenAuto == 0 ? (ac != 0 ? ac : 0xFF39E6FF) : fallbackWhenAuto;
        }
        return parse(c, Color.WHITE);
    }

    private int accent(Scene sc) {
        String fixed = cfg.optString("accent", "auto");
        int ac = "auto".equals(fixed) ? sc.accent : parse(fixed, sc.accent);
        return ac != 0 ? ac : 0xFF39E6FF;
    }

    static Typeface font(String f) {
        switch (f == null ? "" : f) {
            case "thin": return Typeface.create("sans-serif-thin", Typeface.NORMAL);
            case "light": return Typeface.create("sans-serif-light", Typeface.NORMAL);
            case "regular": return Typeface.create("sans-serif", Typeface.NORMAL);
            case "bold": return Typeface.create("sans-serif", Typeface.BOLD);
            case "condensed": return Typeface.create("sans-serif-condensed", Typeface.NORMAL);
            default: return Typeface.create("sans-serif-medium", Typeface.NORMAL);
        }
    }

    static int withAlpha(int c, int a) {
        return (Math.max(0, Math.min(255, a)) << 24) | (c & 0xFFFFFF);
    }

    static int parse(String s, int def) {
        try {
            return Color.parseColor(s.trim());
        } catch (RuntimeException e) {
            return def;
        }
    }

    private String time(long now) {
        boolean h12 = "12".equals(cfg.optString("clock", "24"));
        return new SimpleDateFormat(h12 ? "h:mm" : "HH:mm", Locale.getDefault()).format(new Date(now));
    }

    private static String fmt(String pattern, long now) {
        return new SimpleDateFormat(pattern, Locale.getDefault()).format(new Date(now)).toUpperCase(Locale.getDefault());
    }

    // ------------------------------------------------------------------ drawing

    void draw(Canvas c, int w, int h, Scene sc) {
        c.drawColor(0xFF06070A);
        if (b("ss_wallpaper")) {
            float dim = Math.max(0f, Math.min(0.95f, f("ss_dim") / 100f));
            if ("photo".equals(face)) {
                float dur = Math.max(5f, f("ss_photo_seconds"));
                if (sc.prev != null && sc.fade < 1f) kenBurns(c, w, h, sc.prev, dim, 1f, sc.photoIndex - 1, dur + sc.photoT, dur);
                kenBurns(c, w, h, sc.wall, dim, sc.fade, sc.photoIndex, sc.photoT, dur);
            } else {
                wall(c, w, h, sc.wall, dim, 1f, 1f, 0f, 0f);
            }
        }
        if (b("ss_bubbles")) bubbles(c, w, h, sc.t);
        switch (face) {
            case "noir": noir(c, w, h, sc); break;
            case "neon": neon(c, w, h, sc); break;
            case "analog": analog(c, w, h, sc); break;
            case "minimal": corner(c, w, h, sc, false); break;
            case "photo": corner(c, w, h, sc, true); break;
            case "dot": dotFace(c, w, h, sc); break;
            case "material": material(c, w, h, sc); break;
            default: glass(c, w, h, sc); break;
        }
    }

    private void wall(Canvas c, int w, int h, Bitmap b, float dim, float alpha, float zoom, float panX, float panY) {
        if (b == null || b.isRecycled()) return;
        float bw = b.getWidth(), bh = b.getHeight();
        float scale = Math.max(w / bw, h / bh) * zoom;
        float cw = w / scale, ch = h / scale;
        float cx = bw / 2f + panX * (bw - cw) / 2f, cy = bh / 2f + panY * (bh - ch) / 2f;
        src.set(Math.round(cx - cw / 2), Math.round(cy - ch / 2), Math.round(cx + cw / 2), Math.round(cy + ch / 2));
        dst.set(0, 0, w, h);
        int v = Math.round(255 * (1f - dim));
        pic.setColorFilter(dim > 0 ? new LightingColorFilter(Color.rgb(v, v, v), 0) : null);
        pic.setAlpha(Math.round(255 * Math.max(0f, Math.min(1f, alpha))));
        c.drawBitmap(b, src, dst, pic);
        pic.setAlpha(255);
    }

    private void kenBurns(Canvas c, int w, int h, Bitmap b, float dim, float alpha, int index, float t, float dur) {
        float k = Math.max(0f, Math.min(1.2f, t / dur));
        float zoom = 1.02f + 0.12f * k;
        int dir = Math.floorMod(index, 4);
        float px = (dir == 0 || dir == 3 ? -1f : 1f) * (0.6f - 1.2f * k) * 0.5f;
        float py = (dir < 2 ? -1f : 1f) * (0.6f - 1.2f * k) * 0.35f;
        wall(c, w, h, b, dim, alpha, zoom, px, py);
    }

    /** Soft rings rising slowly; their place is worked out from the time alone, so nothing is stored. */
    private void bubbles(Canvas c, int w, int h, float t) {
        line.setStrokeWidth(Math.max(1f, h * 0.0016f));
        line.setColor(0xB3FFFFFF);
        line.setShader(null);
        for (int i = 0; i < 22; i++) {
            float r1 = rnd(i, 1), r2 = rnd(i, 2), r3 = rnd(i, 3), r4 = rnd(i, 4), r5 = rnd(i, 5), r6 = rnd(i, 6);
            float speed = h * (0.008f + 0.012f * r2);
            float span = h * 1.2f;
            float y = h * 1.08f - ((t * speed + r3 * span) % span);
            float x = r1 * w + (float) Math.sin(t * 0.8f + r4 * 6.28f) * w * (0.002f + 0.006f * r5);
            c.drawCircle(x, y, h * (0.004f + 0.005f * r6), line);
        }
    }

    private static float rnd(int i, int k) {
        double v = Math.sin(i * 12.9898 + k * 78.233) * 43758.5453;
        return (float) (v - Math.floor(v));
    }

    private void style(float size, String font, int color, float spacing) {
        text.setTextSize(size);
        text.setTypeface(font(font));
        text.setColor(color);
        text.setLetterSpacing(spacing);
        text.setShader(null);
        text.setShadowLayer(size * 0.12f, 0, size * 0.03f, 0x73000000);
        text.setTextAlign(Paint.Align.LEFT);
    }

    /** x where a group of the given width starts, for the face's alignment. */
    private float alignX(int w, float width) {
        String a = s("ss_align");
        float m = w * 0.07f;
        if ("left".equals(a)) return m;
        if ("right".equals(a)) return w - m - width;
        return (w - width) / 2f;
    }

    private void sunGlyph(Canvas c, float x, float cy, float s, int color) {
        line.setColor(color);
        line.setShader(null);
        line.setStrokeWidth(Math.max(1.2f, s * 0.11f));
        box.set(x + s * 0.2f, cy - s * 0.25f, x + s * 0.8f, cy + s * 0.35f);
        c.drawArc(box, 180, 180, false, line);
        c.drawLine(x, cy + s * 0.32f, x + s, cy + s * 0.32f, line);
        c.drawLine(x + s * 0.5f, cy - s * 0.5f, x + s * 0.5f, cy - s * 0.38f, line);
        c.drawLine(x + s * 0.12f, cy - s * 0.2f, x + s * 0.2f, cy - s * 0.12f, line);
        c.drawLine(x + s * 0.88f, cy - s * 0.2f, x + s * 0.8f, cy - s * 0.12f, line);
    }

    /** Sunrise/sunset (with its glyph) and the temperature on one line; returns its width. Draws when draw. */
    private float sunLine(Canvas c, float x, float baseline, float size, Scene sc, int color, boolean draw) {
        boolean sun = b("ss_sun") && sc.sun != null && !sc.sun.isEmpty();
        boolean wx = b("ss_weather") && sc.weather != null && !sc.weather.isEmpty();
        if (!sun && !wx) return 0;
        style(size, "medium", color, 0.02f);
        float g = size * 1.25f, gap = size * 2.2f;
        float width = 0;
        if (sun) width += g + size * 0.7f + text.measureText(sc.sun);
        if (sun && wx) width += gap;
        if (wx) width += text.measureText(sc.weather);
        if (draw) {
            float cx = x;
            if (sun) {
                sunGlyph(c, cx, baseline - size * 0.38f, g, 0xFFBFE6FF);
                c.drawText(sc.sun, cx + g + size * 0.7f, baseline, text);
                cx += g + size * 0.7f + text.measureText(sc.sun) + gap;
            }
            if (wx) c.drawText(sc.weather, cx, baseline, text);
        }
        return width;
    }

    // ------------------------------------------------------------------ faces

    /** Date small above a big clock, sunrise under it: the original Glass screensaver. */
    private void glass(Canvas c, int w, int h, Scene sc) {
        int col = color(sc, Color.WHITE);
        float top = h * f("ss_clock_top") / 100f;
        float size = h * f("ss_clock_size") / 100f;
        float y = top;
        if (b("ss_date")) {
            style(h * 0.019f, "medium", col, 0.04f);
            String d = fmt("EEE, dd MMM", sc.now);
            float dw = text.measureText(d);
            y += h * 0.019f * 0.75f;
            c.drawText(d, alignX(w, dw), y, text);
            y += h * 0.03f;
        }
        style(size, s("ss_font"), col, -0.01f);
        String tm = time(sc.now);
        String sec = b("ss_seconds") ? fmt("ss", sc.now) : "";
        float tw = text.measureText(tm);
        float sw = 0;
        Paint.FontMetrics fm = text.getFontMetrics();
        float cap = -fm.ascent * 0.72f;
        y += cap;
        if (!sec.isEmpty()) {
            Paint p2 = new Paint(text);
            p2.setTextSize(size * 0.3f);
            sw = p2.measureText(sec) + size * 0.08f;
        }
        float x = alignX(w, tw + sw);
        c.drawText(tm, x, y, text);
        if (!sec.isEmpty()) {
            text.setTextSize(size * 0.3f);
            text.setAlpha(0xCC);
            c.drawText(sec, x + tw + size * 0.08f, y, text);
            text.setAlpha(255);
        }
        float sun = sunLine(null, 0, 0, h * 0.022f, sc, col, false);
        if (sun > 0) sunLine(c, alignX(w, sun), y + h * 0.11f, h * 0.022f, sc, col, true);
    }

    /** Dark and quiet: a big light clock low on the left, seconds rolling up beside it, a thin accent line. */
    private void noir(Canvas c, int w, int h, Scene sc) {
        String a = s("ss_align");
        // shade on the clock's side so it reads on any picture
        Paint sh = new Paint();
        if ("right".equals(a)) {
            sh.setShader(new LinearGradient(w, 0, w * 0.35f, 0, 0xCC000000, 0x00000000, Shader.TileMode.CLAMP));
        } else if ("left".equals(a)) {
            sh.setShader(new LinearGradient(0, 0, w * 0.65f, 0, 0xCC000000, 0x00000000, Shader.TileMode.CLAMP));
        } else {
            sh.setShader(new LinearGradient(0, h, 0, h * 0.35f, 0xCC000000, 0x00000000, Shader.TileMode.CLAMP));
        }
        c.drawRect(0, 0, w, h, sh);
        sh.setShader(new LinearGradient(0, h, 0, h * 0.55f, 0x99000000, 0x00000000, Shader.TileMode.CLAMP));
        c.drawRect(0, h * 0.55f, w, h, sh);

        int col = color(sc, Color.WHITE);
        int acc = accent(sc);
        float size = h * f("ss_clock_size") / 100f;
        float base = h * f("ss_clock_top") / 100f;
        style(size, s("ss_font"), col, -0.02f);
        String tm = time(sc.now);
        float tw = text.measureText(tm);
        float secSize = size * 0.32f;
        boolean secs = b("ss_seconds");
        Paint sp = new Paint(text);
        sp.setTextSize(secSize);
        float digit = sp.measureText("0");
        float sw = secs ? digit * 2.1f + size * 0.06f : 0;
        float x = alignX(w, tw + sw);
        c.drawText(tm, x, base, text);
        if (secs) rollSeconds(c, x + tw + size * 0.06f, base, sp, sc.now, col);

        float cap = size * 0.72f;
        if (b("ss_date")) {
            float ds = h * 0.024f;
            style(ds, "medium", col, 0.28f);
            text.setAlpha(0xD9);
            String d = fmt("EEEE  ·  dd MMMM", sc.now);
            float dy = base - cap - size * 0.16f;
            c.drawText(d, x + size * 0.03f, dy, text);
            text.setAlpha(255);
            line.setShader(null);
            line.setColor(acc);
            line.setStrokeWidth(Math.max(2f, h * 0.0035f));
            c.drawLine(x + size * 0.04f, dy - ds * 1.6f, x + size * 0.04f + h * 0.06f, dy - ds * 1.6f, line);
        }
        float sun = sunLine(null, 0, 0, h * 0.022f, sc, col, false);
        if (sun > 0) sunLine(c, x + size * 0.03f, base + h * 0.075f, h * 0.022f, sc, col, true);
    }

    /** Two digits; when one changes it rolls up out of view as the new one rolls in from below. */
    private void rollSeconds(Canvas c, float x, float base, Paint p, long now, int col) {
        int s = (int) ((now / 1000) % 60);
        int prev = (s + 59) % 60;
        float frac = (now % 1000) / 1000f;
        float k = Math.min(1f, frac / 0.42f);
        k = 1f - (1f - k) * (1f - k);
        String a = String.format(Locale.US, "%02d", prev), bb = String.format(Locale.US, "%02d", s);
        float size = p.getTextSize();
        float rise = size * 0.95f;
        float digit = p.measureText("0");
        c.save();
        c.clipRect(x - size * 0.1f, base - size * 1.0f, x + digit * 2.4f, base + size * 0.25f);
        p.setColor(col);
        for (int i = 0; i < 2; i++) {
            float dx = x + i * digit * 1.04f;
            if (a.charAt(i) == bb.charAt(i) || k >= 1f) {
                p.setAlpha(0xCC);
                c.drawText(String.valueOf(bb.charAt(i)), dx, base, p);
            } else {
                p.setAlpha(Math.round(0xCC * (1 - k)));
                c.drawText(String.valueOf(a.charAt(i)), dx, base - rise * k, p);
                p.setAlpha(Math.round(0xCC * k));
                c.drawText(String.valueOf(bb.charAt(i)), dx, base + rise * (1 - k), p);
            }
        }
        c.restore();
    }

    /** A ring of 60 ticks with a glowing arc sweeping round with the seconds, the time inside. */
    private void neon(Canvas c, int w, int h, Scene sc) {
        int acc = color(sc, 0);
        float d = h * f("ss_clock_size") / 100f;
        float r = d / 2f;
        float cx = alignX(w, d) + r;
        float cy = h * f("ss_clock_top") / 100f;
        // soft light behind the ring
        Paint halo = new Paint(Paint.ANTI_ALIAS_FLAG);
        halo.setShader(new RadialGradient(cx, cy, r * 1.35f, new int[]{withAlpha(acc, 0x2A), withAlpha(acc, 0x10), 0},
                new float[]{0f, 0.6f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, r * 1.35f, halo);

        line.setShader(null);
        for (int i = 0; i < 60; i++) {
            boolean big = i % 5 == 0;
            double ang = Math.toRadians(i * 6 - 90);
            float r1 = r * (big ? 0.9f : 0.93f), r2 = r * 0.97f;
            line.setColor(withAlpha(acc, big ? 0xB0 : 0x55));
            line.setStrokeWidth(Math.max(1f, h * (big ? 0.0035f : 0.002f)));
            c.drawLine(cx + (float) Math.cos(ang) * r1, cy + (float) Math.sin(ang) * r1,
                    cx + (float) Math.cos(ang) * r2, cy + (float) Math.sin(ang) * r2, line);
        }
        float secs = (sc.now % 60000) / 1000f;
        float sweep = b("ss_smooth") ? secs * 6f : (float) Math.floor(secs) * 6f;
        box.set(cx - r * 1.04f, cy - r * 1.04f, cx + r * 1.04f, cy + r * 1.04f);
        float sw = h * 0.008f;
        int[][] passes = {{6, 0x1A}, {3, 0x40}, {1, 0xFF}};
        for (int[] pass : passes) {
            line.setStrokeWidth(sw * pass[0]);
            line.setColor(withAlpha(acc, pass[1]));
            c.drawArc(box, -90, Math.max(0.5f, sweep), false, line);
        }
        double end = Math.toRadians(sweep - 90);
        float hx = cx + (float) Math.cos(end) * r * 1.04f, hy = cy + (float) Math.sin(end) * r * 1.04f;
        fill.setShader(null);
        fill.setColor(withAlpha(acc, 0x55));
        c.drawCircle(hx, hy, sw * 2.6f, fill);
        fill.setColor(Color.WHITE);
        c.drawCircle(hx, hy, sw * 1.1f, fill);

        float ts = d * 0.25f;
        style(ts, s("ss_font"), acc, -0.01f);
        text.setShadowLayer(ts * 0.18f, 0, 0, withAlpha(acc, 0xCC));
        text.setTextAlign(Paint.Align.CENTER);
        c.drawText(time(sc.now), cx, cy + ts * 0.36f, text);
        if (b("ss_date")) {
            style(h * 0.021f, "medium", Color.WHITE, 0.22f);
            text.setTextAlign(Paint.Align.CENTER);
            text.setAlpha(0xCC);
            c.drawText(fmt("EEE  dd  MMM", sc.now), cx, cy + ts * 0.36f + h * 0.06f, text);
            text.setAlpha(255);
        }
        float sun = sunLine(null, 0, 0, h * 0.021f, sc, Color.WHITE, false);
        if (sun > 0) sunLine(c, cx - sun / 2f, cy + r + h * 0.08f, h * 0.021f, sc, Color.WHITE, true);
    }

    /** A watch: glassy dark dial, markers, three hands, a date window at three o'clock. */
    private void analog(Canvas c, int w, int h, Scene sc) {
        int acc = color(sc, 0);
        float d = h * f("ss_clock_size") / 100f;
        float r = d / 2f;
        float cx = alignX(w, d) + r;
        float cy = h * f("ss_clock_top") / 100f;

        Paint dial = new Paint(Paint.ANTI_ALIAS_FLAG);
        dial.setShader(new RadialGradient(cx, cy - r * 0.3f, r * 1.3f, 0xB0161A22, 0xD0050608, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, r, dial);
        line.setShader(null);
        line.setColor(0x66FFFFFF);
        line.setStrokeWidth(Math.max(1.5f, r * 0.012f));
        c.drawCircle(cx, cy, r, line);
        line.setColor(0x26FFFFFF);
        line.setStrokeWidth(Math.max(1f, r * 0.006f));
        c.drawCircle(cx, cy, r * 0.9f, line);

        boolean numerals = b("ss_numerals");
        for (int i = 0; i < 60; i++) {
            double ang = Math.toRadians(i * 6 - 90);
            float cos = (float) Math.cos(ang), sin = (float) Math.sin(ang);
            if (i % 5 == 0) {
                if (numerals && i % 15 == 0) continue;
                float r1 = r * (i == 0 ? 0.74f : 0.78f), r2 = r * 0.88f;
                line.setColor(Color.WHITE);
                line.setStrokeWidth(r * (i == 0 ? 0.035f : 0.026f));
                if (i == 0) {
                    float off = r * 0.03f;
                    c.drawLine(cx - off, cy - r1, cx - off, cy - r2, line);
                    c.drawLine(cx + off, cy - r1, cx + off, cy - r2, line);
                } else {
                    c.drawLine(cx + cos * r1, cy + sin * r1, cx + cos * r2, cy + sin * r2, line);
                }
            } else {
                line.setColor(0x8CFFFFFF);
                line.setStrokeWidth(Math.max(1f, r * 0.007f));
                c.drawLine(cx + cos * r * 0.84f, cy + sin * r * 0.84f, cx + cos * r * 0.88f, cy + sin * r * 0.88f, line);
            }
        }
        if (numerals) {
            style(r * 0.17f, s("ss_font"), Color.WHITE, 0f);
            text.setTextAlign(Paint.Align.CENTER);
            float off = r * 0.17f * 0.36f;
            c.drawText("12", cx, cy - r * 0.68f + off, text);
            c.drawText("3", cx + r * 0.7f, cy + off, text);
            c.drawText("6", cx, cy + r * 0.7f + off, text);
            c.drawText("9", cx - r * 0.7f, cy + off, text);
        }
        if (b("ss_date")) {
            float bw = r * 0.3f, bh = r * 0.13f, bx = cx + r * (numerals ? 0.3f : 0.5f) - bw / 2f;
            box.set(bx, cy - bh / 2f, bx + bw, cy + bh / 2f);
            fill.setShader(null);
            fill.setColor(0xE60B0D12);
            c.drawRoundRect(box, bh * 0.25f, bh * 0.25f, fill);
            line.setColor(0x4DFFFFFF);
            line.setStrokeWidth(Math.max(1f, r * 0.005f));
            c.drawRoundRect(box, bh * 0.25f, bh * 0.25f, line);
            style(bh * 0.58f, "medium", Color.WHITE, 0.06f);
            text.setTextAlign(Paint.Align.CENTER);
            c.drawText(fmt("EEE dd", sc.now), box.centerX(), box.centerY() + bh * 0.2f, text);
        }

        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(sc.now);
        float sec = cal.get(Calendar.SECOND) + (b("ss_smooth") ? cal.get(Calendar.MILLISECOND) / 1000f : 0f);
        float min = cal.get(Calendar.MINUTE) + sec / 60f;
        float hr = (cal.get(Calendar.HOUR) + min / 60f);
        hand(c, cx, cy, hr * 30f, r * 0.5f, r * 0.08f, r * 0.045f, Color.WHITE);
        hand(c, cx, cy, min * 6f, r * 0.8f, r * 0.08f, r * 0.03f, Color.WHITE);
        if (b("ss_seconds")) hand(c, cx, cy, sec * 6f, r * 0.86f, r * 0.2f, r * 0.012f, acc);
        fill.setColor(acc);
        c.drawCircle(cx, cy, r * 0.035f, fill);
        fill.setColor(0xFF0B0D12);
        c.drawCircle(cx, cy, r * 0.014f, fill);
        float sun = sunLine(null, 0, 0, h * 0.021f, sc, Color.WHITE, false);
        if (sun > 0) sunLine(c, cx - sun / 2f, cy + r + h * 0.07f, h * 0.021f, sc, Color.WHITE, true);
    }

    private void hand(Canvas c, float cx, float cy, float deg, float len, float tail, float width, int color) {
        double a = Math.toRadians(deg - 90);
        float dx = (float) Math.cos(a), dy = (float) Math.sin(a);
        line.setShader(null);
        line.setStrokeWidth(width);
        line.setColor(0x66000000);
        float sh = width * 0.6f + 1.5f;
        c.drawLine(cx - dx * tail + sh, cy - dy * tail + sh, cx + dx * len + sh, cy + dy * len + sh, line);
        line.setColor(color);
        c.drawLine(cx - dx * tail, cy - dy * tail, cx + dx * len, cy + dy * len, line);
    }

    // ------------------------------------------------------------------ dot matrix lettering

    /** 5 x 7 dot letters: each row is 5 bits, left dot = 16. */
    private static final java.util.Map<Character, int[]> DOTS = new java.util.HashMap<>();

    static {
        String[] g = {
                "0", "14,17,19,21,25,17,14", "1", "4,12,4,4,4,4,14", "2", "14,17,1,2,4,8,31", "3", "31,2,4,2,1,17,14",
                "4", "2,6,10,18,31,2,2", "5", "31,16,30,1,1,17,14", "6", "6,8,16,30,17,17,14", "7", "31,1,2,4,8,8,8",
                "8", "14,17,17,14,17,17,14", "9", "14,17,17,15,1,2,12",
                "A", "14,17,17,31,17,17,17", "B", "30,17,17,30,17,17,30", "C", "14,17,16,16,16,17,14",
                "D", "28,18,17,17,17,18,28", "E", "31,16,16,30,16,16,31", "F", "31,16,16,30,16,16,16",
                "G", "14,17,16,23,17,17,15", "H", "17,17,17,31,17,17,17", "I", "14,4,4,4,4,4,14",
                "J", "7,2,2,2,2,18,12", "K", "17,18,20,24,20,18,17", "L", "16,16,16,16,16,16,31",
                "M", "17,27,21,21,17,17,17", "N", "17,17,25,21,19,17,17", "O", "14,17,17,17,17,17,14",
                "P", "30,17,17,30,16,16,16", "Q", "14,17,17,17,21,18,13", "R", "30,17,17,30,20,18,17",
                "S", "15,16,16,14,1,1,30", "T", "31,4,4,4,4,4,4", "U", "17,17,17,17,17,17,14",
                "V", "17,17,17,17,17,10,4", "W", "17,17,17,21,21,21,10", "X", "17,17,10,4,10,17,17",
                "Y", "17,17,10,4,4,4,4", "Z", "31,1,2,4,8,16,31",
                ":", "0,12,12,0,12,12,0", ".", "0,0,0,0,0,12,12", "-", "0,0,0,31,0,0,0", "/", "1,1,2,4,8,16,16",
                "%", "24,25,2,4,8,19,3", "°", "12,18,18,12,0,0,0", "+", "0,4,4,31,4,4,0", ",", "0,0,0,0,12,4,8",
                "'", "12,4,8,0,0,0,0", "!", "4,4,4,4,4,0,4", "?", "14,17,1,2,4,0,4", "&", "12,18,20,8,21,18,13",
                "(", "2,4,8,8,8,4,2", ")", "8,4,2,2,2,4,8", "·", "0,0,0,12,12,0,0"};
        for (int i = 0; i + 1 < g.length; i += 2) {
            String[] p = g[i + 1].split(",");
            int[] rows = new int[7];
            for (int k = 0; k < 7; k++) rows[k] = Integer.parseInt(p[k]);
            DOTS.put(g[i].charAt(0), rows);
        }
    }

    /** Width of dot lettering whose dots sit on a grid of 'cell' (letters are 7 cells high). */
    static float dotsWidth(String s, float cell) {
        if (s == null || s.isEmpty()) return 0;
        float w = 0;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            w += (ch == ' ' ? 4 : ch == ':' || ch == '.' || ch == ',' || ch == '\'' || ch == '!' ? 4 : 6) * cell;
        }
        return w - cell;
    }

    /** Draws dot lettering with its top-left at (x, top); off dots faintly when ghost > 0 (0..255). */
    static float dots(Canvas c, String s, float x, float top, float cell, int color, int ghost) {
        if (s == null) return 0;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float r = cell * 0.4f;
        float cx = x;
        String up = s.toUpperCase(Locale.ROOT);
        for (int i = 0; i < up.length(); i++) {
            char ch = up.charAt(i);
            if (ch == ' ') {
                cx += 4 * cell;
                continue;
            }
            int[] rows = DOTS.get(ch);
            boolean narrow = ch == ':' || ch == '.' || ch == ',' || ch == '\'' || ch == '!';
            int cols = narrow ? 3 : 5, shift = narrow ? 1 : 0;
            if (rows != null) {
                for (int row = 0; row < 7; row++) {
                    for (int col = 0; col < cols; col++) {
                        boolean on = (rows[row] & (16 >> (col + shift))) != 0;
                        if (!on && ghost <= 0) continue;
                        p.setColor(on ? color : withAlpha(color, ghost));
                        c.drawCircle(cx + col * cell + cell / 2f, top + row * cell + cell / 2f, r, p);
                    }
                }
            }
            cx += (narrow ? 4 : 6) * cell;
        }
        return cx - x - cell;
    }

    /** Dot matrix: the time in big dots, the date in small ones, a red dot, the seconds as a dotted line. */
    private void dotFace(Canvas c, int w, int h, Scene sc) {
        int col = color(sc, Color.WHITE);
        int red = accent(sc);
        float size = h * f("ss_clock_size") / 100f;
        float cell = size / 7f;
        String tm = time(sc.now);
        float tw = dotsWidth(tm, cell);
        float x = alignX(w, tw);
        float top = h * f("ss_clock_top") / 100f - size / 2f;
        dots(c, tm, x, top, cell, col, 0x16);
        fill.setShader(null);
        fill.setColor(red);
        c.drawCircle(x + tw + cell * 2.2f, top + cell * 0.5f, cell * 0.75f, fill);
        float y = top + size + h * 0.05f;
        if (b("ss_date")) {
            float dc = h * 0.006f;
            String d = fmt("EEE dd MMM", sc.now);
            float dw = dotsWidth(d, dc);
            dots(c, d, "center".equals(s("ss_align")) ? (w - dw) / 2f : x, y, dc, withAlpha(col, 0xCC), 0);
            y += dc * 7 + h * 0.045f;
        }
        if (b("ss_seconds")) {
            int secs = (int) ((sc.now / 1000) % 60);
            float step = Math.min(tw / 60f, h * 0.014f);
            float lw = step * 59;
            float lx = "center".equals(s("ss_align")) ? (w - lw) / 2f : x;
            for (int i = 0; i < 60; i++) {
                fill.setColor(i <= secs ? (i == secs ? red : withAlpha(col, 0xB3)) : withAlpha(col, 0x26));
                c.drawCircle(lx + i * step, y, Math.max(1.2f, step * 0.28f), fill);
            }
            y += h * 0.05f;
        }
        if (b("ss_weather") && sc.weather != null && !sc.weather.isEmpty()) {
            float dc = h * 0.005f;
            String wx = sc.weather.replace("  ", " ");
            float ww = dotsWidth(wx, dc);
            dots(c, wx, "center".equals(s("ss_align")) ? (w - ww) / 2f : x, y, dc, withAlpha(col, 0x99), 0);
        }
    }

    /** Material: hours above minutes, very big and round, in tones of the wallpaper's colour. */
    private void material(Canvas c, int w, int h, Scene sc) {
        int ac = accent(sc);
        int hourCol = Board.mix(ac, Color.WHITE, 0.62f), minCol = Board.mix(ac, Color.WHITE, 0.88f);
        String col = s("ss_color");
        if (!"auto".equals(col) && !col.isEmpty()) {
            hourCol = color(sc, Color.WHITE);
            minCol = Board.mix(hourCol, Color.WHITE, 0.5f);
        }
        float size = h * f("ss_clock_size") / 100f;
        boolean h12 = "12".equals(cfg.optString("clock", "24"));
        String hh = new SimpleDateFormat(h12 ? "h" : "HH", Locale.getDefault()).format(new Date(sc.now));
        String mm = new SimpleDateFormat("mm", Locale.getDefault()).format(new Date(sc.now));
        if (h12 && hh.length() == 1) hh = "0" + hh;
        style(size * 1.32f, s("ss_font"), hourCol, -0.03f);
        text.setShadowLayer(0, 0, 0, 0);
        float hw = text.measureText(hh);
        style(size * 1.32f, s("ss_font"), minCol, -0.03f);
        text.setShadowLayer(0, 0, 0, 0);
        float mw = text.measureText(mm);
        float group = Math.max(hw, mw);
        float x = alignX(w, group);
        float mid = h * f("ss_clock_top") / 100f;
        // a soft tonal disc behind the numbers
        fill.setShader(new RadialGradient(x + group / 2f, mid, size * 1.6f,
                new int[]{withAlpha(Board.mix(ac, Color.BLACK, 0.45f), 0x99), 0}, null, Shader.TileMode.CLAMP));
        c.drawCircle(x + group / 2f, mid, size * 1.6f, fill);
        fill.setShader(null);
        text.setColor(hourCol);
        c.drawText(hh, x + (group - hw) / 2f, mid - size * 0.06f, text);
        text.setColor(minCol);
        c.drawText(mm, x + (group - mw) / 2f, mid + size * 0.95f, text);
        if (b("ss_date")) {
            String d = fmt("EEE, dd MMM", sc.now);
            style(h * 0.026f, "medium", Board.mix(ac, Color.BLACK, 0.78f), 0.04f);
            text.setShadowLayer(0, 0, 0, 0);
            float dw = text.measureText(d);
            float ph = h * 0.06f, px = x + (group - dw) / 2f - h * 0.03f, py = mid + size * 1.25f;
            box.set(px, py, px + dw + h * 0.06f, py + ph);
            fill.setColor(Board.mix(ac, Color.WHITE, 0.7f));
            c.drawRoundRect(box, ph / 2f, ph / 2f, fill);
            c.drawText(d, px + h * 0.03f, py + ph * 0.66f, text);
        }
        if (b("ss_weather") && sc.weather != null && !sc.weather.isEmpty()) {
            style(h * 0.024f, "regular", withAlpha(Color.WHITE, 0xD9), 0.02f);
            float ww = text.measureText(sc.weather);
            c.drawText(sc.weather, x + (group - ww) / 2f, mid + size * 1.25f + h * 0.11f, text);
        }
    }

    /** Minimal and Photo: a small clock in a corner; Photo slides and zooms the pictures slowly. */
    private void corner(Canvas c, int w, int h, Scene sc, boolean photo) {
        Paint sh = new Paint();
        sh.setShader(new LinearGradient(0, h, 0, h * 0.6f, photo ? 0x8C000000 : 0x66000000, 0, Shader.TileMode.CLAMP));
        c.drawRect(0, h * 0.6f, w, h, sh);
        int col = color(sc, Color.WHITE);
        float size = h * f("ss_clock_size") / 100f;
        float base = h * f("ss_clock_top") / 100f;
        style(size, s("ss_font"), col, -0.01f);
        String tm = time(sc.now);
        float tw = text.measureText(tm);
        boolean secs = b("ss_seconds");
        Paint sp = new Paint(text);
        sp.setTextSize(size * 0.34f);
        float sw = secs ? sp.measureText("00") * 1.1f + size * 0.08f : 0;
        String d = b("ss_date") ? fmt("EEEE, dd MMMM", sc.now) : "";
        if (b("ss_weather") && sc.weather != null && !sc.weather.isEmpty()) d = d.isEmpty() ? sc.weather : d + "   ·   " + sc.weather;
        Paint dp = new Paint(text);
        dp.setTypeface(font("regular"));
        dp.setTextSize(h * 0.022f);
        dp.setLetterSpacing(0.08f);
        float dw = d.isEmpty() ? 0 : dp.measureText(d);
        float group = Math.max(tw + sw, dw);
        float x = alignX(w, group);
        boolean rightAlign = "right".equals(s("ss_align"));
        float tx = rightAlign ? x + group - (tw + sw) : x;
        c.drawText(tm, tx, base, text);
        if (secs) rollSeconds(c, tx + tw + size * 0.08f, base, sp, sc.now, col);
        if (!d.isEmpty()) {
            dp.setAlpha(0xD9);
            c.drawText(d, rightAlign ? x + group - dw : x, base + h * 0.05f, dp);
        }
    }
}
