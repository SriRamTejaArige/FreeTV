package com.sriramteja.freetv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LightingColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The TV's home screen drawn on the phone from the same settings Glass Home uses, so every change shows
 * at once. Proportions follow the TV (all sizes are fractions of the screen height).
 */
final class HomePreview extends View {

    static final class Entry {
        String pkg, label;
        Bitmap icon, banner;
    }

    private JSONObject cfg = new JSONObject();
    private Bitmap wall, wallBlur;
    private int wallAccent;
    private final List<Entry> apps = new ArrayList<>();
    private int focus = 0;
    private boolean focusBar;
    private final Map<String, Bitmap[]> logos;
    private final Paint pic = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect src = new Rect();
    private final RectF box = new RectF();
    private final Path clip = new Path();

    HomePreview(Context ctx, Map<String, Bitmap[]> sharedLogos) {
        super(ctx);
        logos = sharedLogos != null ? sharedLogos : new HashMap<>();
    }

    void set(JSONObject merged, Bitmap wallpaper, List<Entry> dock) {
        cfg = merged == null ? new JSONObject() : merged;
        if (wallpaper != wall) {
            wall = wallpaper;
            wallBlur = null;
            wallAccent = 0;
            if (wall != null) {
                wallAccent = Noir.accent(wall);
                int bw = Math.max(1, wall.getWidth() / 14), bh = Math.max(1, wall.getHeight() / 14);
                Bitmap s = Bitmap.createScaledBitmap(wall, bw, bh, true);
                wallBlur = Bitmap.createScaledBitmap(s, Math.max(1, bw / 2), Math.max(1, bh / 2), true);
            }
        }
        apps.clear();
        if (dock != null) apps.addAll(dock);
        invalidate();
    }

    void setFocus(int index, boolean onBar) {
        focus = index;
        focusBar = onBar;
        invalidate();
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec);
        setMeasuredDimension(w, Math.round(w * 9f / 16f));
    }

    private float pct(String k, float def) {
        return (float) cfg.optDouble(k, def) / 100f;
    }

    private int accent() {
        String fixed = cfg.optString("accent", "auto");
        int a = "auto".equals(fixed) ? wallAccent : Faces.parse(fixed, wallAccent);
        return a != 0 ? a : 0xFF39E6FF;
    }

    private int rawAccent() {
        String fixed = cfg.optString("accent", "auto");
        return "auto".equals(fixed) ? wallAccent : Faces.parse(fixed, wallAccent);
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        if (w == 0) return;
        float H = h, W = w;
        clip.reset();
        box.set(0, 0, w, h);
        clip.addRoundRect(box, H * 0.025f, H * 0.025f, Path.Direction.CW);
        c.save();
        c.clipPath(clip);
        c.drawColor(0xFF0B0D12);
        float dim = Math.max(0f, Math.min(0.9f, pct("wallpaper_dim", 0)));
        drawCover(c, wall, 0, 0, w, h, dim);

        boolean right = "right".equals(cfg.optString("dock_side", "left"));
        float shade = Math.max(0f, Math.min(1f, pct("shade", 0)));
        if (shade > 0) {
            int a = Math.round(0xE0 * shade);
            Paint p = new Paint();
            float sw = W * 0.42f;
            p.setShader(right
                    ? new LinearGradient(W, 0, W - sw, 0, new int[]{a << 24, (a / 2) << 24, 0}, null, Shader.TileMode.CLAMP)
                    : new LinearGradient(0, 0, sw, 0, new int[]{a << 24, (a / 2) << 24, 0}, null, Shader.TileMode.CLAMP));
            c.drawRect(right ? W - sw : 0, 0, right ? W : sw, H, p);
        }
        float edge = H * pct("edge", 4.5f);
        float pad = H * 0.022f;
        String ds = cfg.optString("dock_style", "icons");
        if ("row".equals(ds)) {
            drawRow(c, W, H, dim);
            drawPills(c, W, H, dim);
            c.restore();
            return;
        }
        if ("panel".equals(ds)) drawPanel(c, W, H, edge, pad, right, dim);
        else drawIcons(c, W, H, edge, pad, right);
        drawBar(c, W, H, edge, right, dim);
        c.restore();
    }

    private void drawCover(Canvas c, Bitmap b, float x, float y, float w, float h, float dim) {
        if (b == null) return;
        float bw = b.getWidth(), bh = b.getHeight();
        float scale = Math.max(w / bw, h / bh);
        float cw = w / scale, ch = h / scale;
        src.set(Math.round((bw - cw) / 2), Math.round((bh - ch) / 2), Math.round((bw + cw) / 2), Math.round((bh + ch) / 2));
        box.set(x, y, x + w, y + h);
        int v = Math.round(255 * (1f - dim));
        pic.setColorFilter(dim > 0 ? new LightingColorFilter(Color.rgb(v, v, v), 0) : null);
        c.drawBitmap(b, src, box, pic);
        pic.setColorFilter(null);
    }

    /** Draws the part of the (blurred) wallpaper that sits behind a glass shape. */
    private void glass(Canvas c, RectF r, float radius, float W, float H, int tint, float dim) {
        c.save();
        Path p = new Path();
        p.addRoundRect(r, radius, radius, Path.Direction.CW);
        c.clipPath(p);
        if (wallBlur != null) {
            float bw = wallBlur.getWidth(), bh = wallBlur.getHeight();
            float scale = Math.max(W / bw, H / bh);
            float ox = (W - bw * scale) / 2f, oy = (H - bh * scale) / 2f;
            src.set(Math.round((r.left - ox) / scale), Math.round((r.top - oy) / scale),
                    Math.round((r.right - ox) / scale), Math.round((r.bottom - oy) / scale));
            int v = Math.round(255 * (1f - dim));
            pic.setColorFilter(dim > 0 ? new LightingColorFilter(Color.rgb(v, v, v), 0) : null);
            c.drawBitmap(wallBlur, src, r, pic);
            pic.setColorFilter(null);
        }
        fill.setShader(null);
        fill.setColor(tint);
        c.drawRect(r, fill);
        c.restore();
        Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(Math.max(1f, H * 0.0015f));
        rim.setColor(0x47FFFFFF);
        c.drawRoundRect(r, radius, radius, rim);
    }

    private void glow(Canvas c, float cx, float cy, float H) {
        if (!cfg.optBoolean("glow", false)) return;
        float size = H * pct("glow_size", 46);
        String gc = cfg.optString("glow_color", "auto");
        int ra = rawAccent();
        int col = "auto".equals(gc) ? (ra == 0 ? 0xFFEEF4FF : ra) : "white".equals(gc) ? 0xFFFFFFFF : Faces.parse(gc, 0xFFEEF4FF);
        int soft = Color.rgb((Color.red(col) + 255) / 2, (Color.green(col) + 255) / 2, (Color.blue(col) + 255) / 2);
        float k = Math.max(0f, Math.min(1f, pct("glow_strength", 55)));
        int centre = Math.round(255 * Math.min(1f, k * 0.92f));
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new RadialGradient(cx, cy, size / 2f, new int[]{Faces.withAlpha(soft, centre),
                Faces.withAlpha(col, Math.round(centre * 0.38f)), Faces.withAlpha(col, 0)}, null, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, size / 2f, p);
    }

    /** {pack picture, halo or null} for one app, made the same way as on the TV. */
    private Bitmap[] logo(Entry e, String style, int index, int count) {
        String key = e.pkg + ":" + style + ("rainbow".equals(style) ? ":" + index + "/" + count : "");
        Bitmap[] got = logos.get(key);
        if (got == null && e.icon != null) {
            Bitmap[] base = logos.get(e.pkg + ":mask");
            Bitmap mask = base == null ? null : base[0];
            if (mask == null) {
                mask = Noir.whiteLogo(new BitmapDrawable(getResources(), e.icon), 144);
                logos.put(e.pkg + ":mask", new Bitmap[]{mask});
            }
            got = new Bitmap[]{Packs.render(style, mask, index, count),
                    mask != null && Packs.haloed(style) ? Noir.halo(mask) : null};
            logos.put(key, got);
        }
        return got;
    }

    /** Paint for a pack picture: wallpaper colour for tinted packs. */
    private Paint packPaint(String style, int alpha) {
        Paint p = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        if (Packs.tinted(style)) p.setColorFilter(new PorterDuffColorFilter(accent(), PorterDuff.Mode.SRC_IN));
        p.setAlpha(alpha);
        return p;
    }

    private void halo(Canvas c, Bitmap halo, String style, int index, int count, float cx, float cy, float size) {
        if (halo == null) return;
        Paint p = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        int col = "rainbow".equals(style) ? Packs.hue(index, count) : accent();
        p.setColorFilter(new PorterDuffColorFilter(col, PorterDuff.Mode.SRC_IN));
        float hs = size * 1.5f;
        c.drawBitmap(halo, null, new RectF(cx - hs / 2, cy - hs / 2, cx + hs / 2, cy + hs / 2), p);
    }

    private void drawIcons(Canvas c, float W, float H, float edge, float pad, boolean right) {
        int n = apps.size();
        if (n == 0) return;
        float s = H * pct("icon_size", 10.5f), gap = H * pct("icon_gap", 4.2f);
        float colH = H - 2 * edge, room = colH - 2 * pad;
        if (n * s + (n - 1) * gap > room) s = Math.max(H * 0.06f, (room - (n - 1) * gap) / n);
        float used = n * s + (n - 1) * gap;
        float top = edge + Math.max(pad, (colH - used) / 2f);
        float x = right ? W - edge - pad - s : edge + pad;
        String style = cfg.optString("icon_style", "original");
        float idle = pct("icon_idle", 85), grow = pct("focus_scale", 122);
        int ac = accent();
        float gw = "banner".equals(style) ? s * 16f / 9f : s;
        if (!focusBar && focus >= 0 && focus < n) glow(c, right ? x + s - gw / 2f : x + gw / 2f, top + focus * (s + gap) + s / 2f, H);
        for (int i = 0; i < n; i++) {
            Entry e = apps.get(i);
            boolean f = !focusBar && i == focus;
            float k = f ? grow : 1f;
            boolean wide = "banner".equals(style);
            float iw = wide ? s * 16f / 9f : s;
            float ss = s * k, sw = iw * k;
            float cx = right ? x + s - iw / 2f : x + iw / 2f, cy = top + i * (s + gap) + s / 2f;
            box.set(cx - sw / 2f, cy - ss / 2f, cx + sw / 2f, cy + ss / 2f);
            int alpha = Math.round(255 * (f ? 1f : idle));
            Bitmap[] lg = Packs.usesLogo(style) ? logo(e, style, i, n) : null;
            if (lg != null && lg[0] != null) {
                if (f) halo(c, lg[1], style, i, n, cx, cy, ss);
                c.drawBitmap(lg[0], null, box, packPaint(style, alpha));
            } else if (wide && (e.banner != null || e.icon != null)) {
                float r = ss * 0.18f;
                Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
                shadow.setColor(Faces.withAlpha(0xFF000000, Math.round(alpha * 0.35f)));
                c.drawRoundRect(box.left, box.top + H * 0.006f, box.right, box.bottom + H * 0.006f, r, r, shadow);
                c.save();
                Path p = new Path();
                p.addRoundRect(box, r, r, Path.Direction.CW);
                c.clipPath(p);
                if (e.banner != null) {
                    int a0 = pic.getAlpha();
                    pic.setAlpha(alpha);
                    drawCover(c, e.banner, box.left, box.top, box.width(), box.height(), 0);
                    pic.setAlpha(a0);
                } else {
                    c.drawColor(Faces.withAlpha(0xFF1C1C1F, alpha));
                    float q = box.height() * 0.7f;
                    c.drawBitmap(e.icon, null, new RectF(box.centerX() - q / 2, box.centerY() - q / 2,
                            box.centerX() + q / 2, box.centerY() + q / 2), pic);
                }
                c.restore();
            } else if (e.icon != null) {
                float r = ss * 0.23f;
                Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
                shadow.setColor(Faces.withAlpha(0xFF000000, Math.round(alpha * 0.35f)));
                c.drawRoundRect(box.left, box.top + H * 0.006f, box.right, box.bottom + H * 0.006f, r, r, shadow);
                c.save();
                Path p = new Path();
                p.addRoundRect(box, r, r, Path.Direction.CW);
                c.clipPath(p);
                Paint ip = new Paint(Paint.FILTER_BITMAP_FLAG);
                ip.setAlpha(alpha);
                c.drawBitmap(e.icon, null, box, ip);
                c.restore();
            } else {
                fill.setShader(null);
                fill.setColor(Faces.withAlpha(0xFF2A2F3A, alpha));
                c.drawRoundRect(box, ss * 0.23f, ss * 0.23f, fill);
            }
            if (f && cfg.optBoolean("labels", true)) {
                text.setTypeface(Faces.font("bold"));
                text.setTextSize(s * pct("label_size", 24));
                text.setColor(Color.WHITE);
                text.setShadowLayer(H * 0.012f, 0, H * 0.002f, 0xB3000000);
                text.setLetterSpacing(0);
                float tw = text.measureText(e.label == null ? "" : e.label);
                float tx = right ? x + s - (iw + s * 0.42f) - tw : x + iw + s * 0.42f;
                c.drawText(e.label == null ? "" : e.label, tx, cy + text.getTextSize() * 0.36f, text);
                text.clearShadowLayer();
            }
        }
    }

    private void drawPanel(Canvas c, float W, float H, float edge, float pad, boolean right, float dim) {
        int n = apps.size();
        float gap = pad;
        float panelH = H - 2 * edge;
        float std = (W * 0.19f - 2 * pad) / 2f;
        float fit = n > 0 ? (panelH - 2 * pad - (n - 1) * gap) / n : std;
        float tileH = n > 0 && n <= 7 ? Math.min(fit, H * 0.2f) : std;
        float tileW = tileH * 2;
        float panelW = tileW + 2 * pad;
        float px = right ? W - edge - panelW : edge;
        RectF panel = new RectF(px, edge, px + panelW, edge + panelH);
        glass(c, panel, tileH * 0.30f + pad, W, H, 0x40C8CDD8, dim);
        c.save();
        c.clipRect(panel);
        for (int i = 0; i < n; i++) {
            Entry e = apps.get(i);
            boolean f = !focusBar && i == focus;
            float k = f ? 1.045f : 1f;
            float cx = px + pad + tileW / 2f, cy = edge + pad + i * (tileH + gap) + tileH / 2f;
            RectF r = new RectF(cx - tileW * k / 2, cy - tileH * k / 2, cx + tileW * k / 2, cy + tileH * k / 2);
            float rad = tileH * 0.30f;
            if (styledTile(c, e, r, rad, false, i, n, f)) {
                if (f && cfg.optBoolean("focus_ring", false) && cfg.optBoolean("tile_box", true)) {
                    Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
                    ring.setStyle(Paint.Style.STROKE);
                    ring.setStrokeWidth(Math.max(2f, H * 0.004f));
                    ring.setColor(0xE6FFFFFF);
                    c.drawRoundRect(r, rad, rad, ring);
                }
                continue;
            }
            c.save();
            Path p = new Path();
            p.addRoundRect(r, rad, rad, Path.Direction.CW);
            c.clipPath(p);
            if (e.banner != null) {
                drawCover(c, e.banner, r.left, r.top, r.width(), r.height(), 0);
            } else {
                c.drawColor(0xFF1C1C1F);
                if (e.icon != null) {
                    float s = r.height() * 0.46f;
                    c.drawBitmap(e.icon, null, new RectF(r.left + r.width() * 0.12f, r.centerY() - s / 2,
                            r.left + r.width() * 0.12f + s, r.centerY() + s / 2), pic);
                }
                text.setTypeface(Faces.font("bold"));
                text.setTextSize(r.height() * 0.17f);
                text.setColor(Color.WHITE);
                c.drawText(e.label == null ? "" : e.label, r.left + r.width() * 0.16f + r.height() * 0.46f,
                        r.centerY() + r.height() * 0.06f, text);
            }
            c.restore();
            if (f && cfg.optBoolean("focus_ring", false)) {
                Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
                ring.setStyle(Paint.Style.STROKE);
                ring.setStrokeWidth(Math.max(2f, H * 0.004f));
                ring.setColor(0xE6FFFFFF);
                c.drawRoundRect(r, rad, rad, ring);
            }
        }
        c.restore();
    }

    /** One tile of the row or panel in the chosen app look; returns false to fall back to the banner. */
    private boolean styledTile(Canvas c, Entry e, RectF r, float rad, boolean rowMode, int index, int count, boolean on) {
        String style = cfg.optString("icon_style", "original");
        boolean boxed = cfg.optBoolean("tile_box", true);
        if (Packs.usesLogo(style)) {
            Bitmap[] lg = logo(e, style, index, count);
            if (lg == null || lg[0] == null) return false;
            if (boxed) {
                fill.setShader(null);
                fill.setColor(0x26FFFFFF);
                c.drawRoundRect(r, rad, rad, fill);
            }
            boolean round = "bubble".equals(style) || "badge".equals(style);
            float s = r.height() * (boxed ? (round ? 0.86f : 0.66f) : (round ? 0.92f : 0.88f));
            if (on) halo(c, lg[1], style, index, count, r.centerX(), r.centerY(), s);
            c.drawBitmap(lg[0], null, new RectF(r.centerX() - s / 2, r.centerY() - s / 2, r.centerX() + s / 2,
                    r.centerY() + s / 2), packPaint(style, 255));
            return true;
        }
        if (rowMode && "original".equals(style) && e.icon != null) {
            if (boxed) {
                fill.setShader(null);
                fill.setColor(0x33FFFFFF);
                c.drawRoundRect(r, rad, rad, fill);
            }
            float s = r.height() * (boxed ? 0.6f : 0.72f);
            RectF ir = new RectF(r.centerX() - s / 2, r.centerY() - s / 2, r.centerX() + s / 2, r.centerY() + s / 2);
            c.save();
            Path p = new Path();
            p.addRoundRect(ir, s * 0.23f, s * 0.23f, Path.Direction.CW);
            c.clipPath(p);
            c.drawBitmap(e.icon, null, ir, pic);
            c.restore();
            return true;
        }
        if ("original".equals(style)) {
            // panel: icon and name on a dark tile
            c.save();
            Path p = new Path();
            p.addRoundRect(r, rad, rad, Path.Direction.CW);
            c.clipPath(p);
            c.drawColor(0xFF1C1C1F);
            if (e.icon != null) {
                float s = r.height() * 0.46f;
                c.drawBitmap(e.icon, null, new RectF(r.left + r.width() * 0.12f, r.centerY() - s / 2,
                        r.left + r.width() * 0.12f + s, r.centerY() + s / 2), pic);
            }
            text.setTypeface(Faces.font("bold"));
            text.setTextSize(r.height() * 0.17f);
            text.setLetterSpacing(0);
            text.setColor(Color.WHITE);
            c.drawText(e.label == null ? "" : e.label, r.left + r.width() * 0.16f + r.height() * 0.46f,
                    r.centerY() + r.height() * 0.06f, text);
            c.restore();
            return true;
        }
        return false;
    }

    /** The Clean theme: one row of app banners in a band, the selected app's name big above it. */
    private void drawRow(Canvas c, float W, float H, float dim) {
        float side = W * 0.06f, bandW = W - 2 * side, pad = H * 0.022f, gap = H * 0.033f;
        int visible = Math.max(2, Math.min(7, cfg.optInt("row_visible", 4)));
        float tileW = (bandW - 2 * pad - (visible - 1) * gap) / visible, tileH = tileW * 0.5625f;
        float bandTop = H * pct("row_top", 64.5f), bandH = tileH + 2 * pad;
        RectF band = new RectF(side, bandTop, side + bandW, bandTop + bandH);
        if (cfg.optBoolean("row_band", true)) glass(c, band, tileH * 0.22f + pad, W, H, 0x99121318, dim);
        int n = apps.size();
        int f = focusBar ? -1 : Math.max(0, Math.min(n - 1, focus));
        int first = f >= visible ? f - visible + 1 : 0;
        float grow = pct("focus_scale", 108);
        if (f >= 0) glow(c, side + pad + (f - first) * (tileW + gap) + tileW / 2f, bandTop + bandH / 2f, H);
        c.save();
        c.clipRect(side, bandTop - H * 0.02f, side + bandW, bandTop + bandH + H * 0.02f);
        for (int i = first; i < n; i++) {
            float x = side + pad + (i - first) * (tileW + gap);
            if (x > side + bandW) break;
            Entry e = apps.get(i);
            boolean on = i == f;
            float k = on ? grow : 1f;
            float cx = x + tileW / 2f, cy = bandTop + bandH / 2f;
            RectF r = new RectF(cx - tileW * k / 2, cy - tileH * k / 2, cx + tileW * k / 2, cy + tileH * k / 2);
            float rad = tileH * 0.14f;
            if (!styledTile(c, e, r, rad, true, i, n, on)) {
                c.save();
                Path p = new Path();
                p.addRoundRect(r, rad, rad, Path.Direction.CW);
                c.clipPath(p);
                if (e.banner != null) {
                    drawCover(c, e.banner, r.left, r.top, r.width(), r.height(), 0);
                } else {
                    c.drawColor(0xFF1C1C1F);
                    if (e.icon != null) {
                        float s = r.height() * 0.5f;
                        c.drawBitmap(e.icon, null, new RectF(r.centerX() - s / 2, r.centerY() - s / 2,
                                r.centerX() + s / 2, r.centerY() + s / 2), pic);
                    }
                }
                c.restore();
            }
            boolean floats = !cfg.optBoolean("tile_box", true) && !"banner".equals(cfg.optString("icon_style", "banner"));
            if (on && cfg.optBoolean("focus_ring", true) && !floats) {
                Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
                ring.setStyle(Paint.Style.STROKE);
                ring.setStrokeWidth(Math.max(2f, H * 0.004f));
                ring.setColor(0xE6FFFFFF);
                c.drawRoundRect(r, rad, rad, ring);
            }
        }
        c.restore();
        if (cfg.optBoolean("row_title", true) && f >= 0 && f < n) {
            text.setTypeface(Faces.font(cfg.optString("title_font", "light")));
            text.setTextSize(H * 0.075f);
            text.setLetterSpacing(0);
            text.setColor(Color.WHITE);
            text.setShadowLayer(H * 0.015f, 0, H * 0.003f, 0x80000000);
            String name = apps.get(f).label == null ? "" : apps.get(f).label;
            c.drawText(name, side + pad, bandTop - H * 0.135f + H * 0.075f * 0.95f, text);
            text.clearShadowLayer();
        }
    }

    /** Clean theme top: greeting, big clock, date (and temperature) left; labelled pills right. */
    private void drawPills(Canvas c, float W, float H, float dim) {
        float side = W * 0.06f + H * 0.022f, top = H * 0.06f;
        boolean h12 = "12".equals(cfg.optString("clock", "24"));
        Date now = new Date();
        float y = top;
        if (cfg.optBoolean("greeting", true)) {
            int hr = Integer.parseInt(new SimpleDateFormat("H", Locale.US).format(now));
            String g = hr < 5 ? "GOOD NIGHT" : hr < 12 ? "GOOD MORNING" : hr < 17 ? "GOOD AFTERNOON" : hr < 22 ? "GOOD EVENING" : "GOOD NIGHT";
            text.setTypeface(Faces.font("bold"));
            text.setTextSize(H * 0.022f);
            text.setLetterSpacing(0.25f);
            text.setColor(0xBFFFFFFF);
            c.drawText(g, side, y + H * 0.022f * 0.95f, text);
            y += H * 0.022f * 1.25f;
        }
        float x = side;
        float ts = H * 0.105f * pct("clock_scale", 100);
        float base = y + ts * 0.93f;
        if (cfg.optBoolean("bar_clock", true)) {
            String cc = cfg.optString("clock_color", "white");
            int col = "accent".equals(cc) ? accent() : "white".equals(cc) ? Color.WHITE : Faces.parse(cc, Color.WHITE);
            text.setTypeface(Faces.font(cfg.optString("clock_font", "light")));
            text.setTextSize(ts);
            text.setLetterSpacing(0);
            text.setColor(col);
            String tm = new SimpleDateFormat(h12 ? "h:mm" : "HH:mm", Locale.getDefault()).format(now);
            c.drawText(tm, x, base, text);
            x += text.measureText(tm);
            if (cfg.optBoolean("seconds", false)) {
                text.setTextSize(H * 0.035f * pct("clock_scale", 100));
                x += H * 0.008f;
                c.drawText(new SimpleDateFormat("ss", Locale.US).format(now), x, base, text);
                x += text.measureText("00");
            }
            if (h12) {
                text.setTypeface(Faces.font("medium"));
                text.setTextSize(H * 0.03f);
                text.setColor(0xD9FFFFFF);
                x += H * 0.012f;
                String ap = new SimpleDateFormat("a", Locale.getDefault()).format(now).toUpperCase(Locale.getDefault());
                c.drawText(ap, x, base - H * 0.012f, text);
                x += text.measureText(ap);
            }
            x += H * 0.03f;
            fill.setShader(null);
            fill.setColor(0x4DFFFFFF);
            c.drawRect(x, base - H * 0.061f, x + Math.max(1f, H * 0.0015f), base - H * 0.006f, fill);
            x += H * 0.03f;
        }
        text.setTypeface(Faces.font("medium"));
        text.setTextSize(H * 0.026f);
        text.setLetterSpacing(0.15f);
        text.setColor(0xD9FFFFFF);
        boolean wx = cfg.optBoolean("bar_weather", true);
        float dateBase = wx ? base - H * 0.04f : base - H * 0.008f;
        c.drawText(new SimpleDateFormat("EEEE  dd MMMM", Locale.getDefault()).format(now).toUpperCase(Locale.getDefault()),
                x, dateBase, text);
        if (wx) {
            text.setTextSize(H * 0.024f);
            text.setLetterSpacing(0.08f);
            text.setColor(0xBFFFFFFF);
            c.drawText("24°  Partly cloudy", x, base - H * 0.006f, text);
        }
        text.setLetterSpacing(0);

        // pills, right to left
        List<String> buttons = new ArrayList<>();
        JSONArray a = cfg.optJSONArray("buttons");
        if (a != null) for (int i = 0; i < a.length(); i++) buttons.add(a.optString(i));
        float h = H * 0.08f, py = top + H * 0.045f;
        Paint pt = new Paint(Paint.ANTI_ALIAS_FLAG);
        pt.setTypeface(Faces.font("bold"));
        pt.setTextSize(h * 0.3f);
        float[] widths = new float[buttons.size()];
        String[] labels = new String[buttons.size()];
        float total = 0;
        for (int i = 0; i < buttons.size(); i++) {
            String w = buttons.get(i);
            labels[i] = "memory".equals(w) ? "RAM 68%  ·  54%" : "inputs".equals(w) ? "Inputs" : "settings".equals(w) ? "Settings"
                    : "wifi".equals(w) ? "Wi-Fi" : "bluetooth".equals(w) ? "Bluetooth" : "music".equals(w) ? "Music" : "Accounts";
            widths[i] = h * 0.36f + h * 0.42f + h * 0.2f + pt.measureText(labels[i]) + h * 0.42f;
            total += widths[i] + (i > 0 ? h * 0.25f : 0);
        }
        float px = W - W * 0.06f - H * 0.022f - total;
        for (int i = 0; i < buttons.size(); i++) {
            boolean on = focusBar && i == focus;
            RectF r = new RectF(px, py, px + widths[i], py + h);
            Paint bgp = new Paint(Paint.ANTI_ALIAS_FLAG);
            bgp.setColor(on ? 0xFFF5F5F7 : 0x59202226);
            c.drawRoundRect(r, h / 2, h / 2, bgp);
            if (!on) {
                Paint st = new Paint(Paint.ANTI_ALIAS_FLAG);
                st.setStyle(Paint.Style.STROKE);
                st.setStrokeWidth(Math.max(1f, H * 0.0015f));
                st.setColor(0x4DFFFFFF);
                c.drawRoundRect(r, h / 2, h / 2, st);
            }
            Drawable ic = getContext().getDrawable(buttonIcon(buttons.get(i)));
            float s = h * 0.42f, ix = px + h * 0.36f;
            if (ic != null) {
                ic = ic.mutate();
                ic.setTint(on ? 0xFF1C1C1E : Color.WHITE);
                ic.setBounds(Math.round(ix), Math.round(r.centerY() - s / 2), Math.round(ix + s), Math.round(r.centerY() + s / 2));
                ic.draw(c);
            }
            pt.setColor(on ? 0xFF1C1C1E : Color.WHITE);
            Paint.FontMetrics fm = pt.getFontMetrics();
            c.drawText(labels[i], ix + s + h * 0.2f, r.centerY() - (fm.ascent + fm.descent) / 2f, pt);
            px += widths[i] + h * 0.25f;
        }
    }

    private static int buttonIcon(String which) {
        switch (which) {
            case "wifi": return R.drawable.g_wifi;
            case "bluetooth": return R.drawable.g_bluetooth;
            case "profile": return R.drawable.g_person;
            case "music": return R.drawable.g_music;
            case "inputs": return R.drawable.g_inputs;
            case "settings": return R.drawable.g_gear;
            default: return R.drawable.g_chip;
        }
    }

    private void drawBar(Canvas c, float W, float H, float edge, boolean right, float dim) {
        float hb = H * 0.076f;
        boolean bare = !"pill".equals(cfg.optString("bar_style", "bare"));
        List<String> buttons = new ArrayList<>();
        JSONArray a = cfg.optJSONArray("buttons");
        if (a != null) for (int i = 0; i < a.length(); i++) buttons.add(a.optString(i));
        float d = hb * 0.66f;
        float padL = hb * (bare ? 0.1f : 0.42f), padR = hb * 0.16f;
        boolean clock = cfg.optBoolean("bar_clock", true);
        boolean secs = clock && cfg.optBoolean("seconds", false);
        float scale = pct("clock_scale", 100);
        String tm = new SimpleDateFormat("12".equals(cfg.optString("clock", "24")) ? "h:mm" : "HH:mm", Locale.getDefault())
                .format(new Date());
        text.setTypeface(Faces.font(cfg.optString("clock_font", "medium")));
        text.setTextSize(hb * 0.46f * scale);
        text.setLetterSpacing(0);
        float tw = clock ? text.measureText(tm) : 0;
        Paint sp = new Paint(text);
        sp.setTextSize(hb * 0.32f * Math.max(0.8f, scale * 0.7f));
        float sw = secs ? sp.measureText("00") * 1.08f : 0;
        boolean wx = cfg.optBoolean("bar_weather", true);
        String wtxt = "24°  Partly cloudy";
        Paint wp = new Paint(Paint.ANTI_ALIAS_FLAG);
        wp.setTypeface(Faces.font("medium"));
        wp.setTextSize(hb * 0.3f);
        wp.setColor(0xE6FFFFFF);
        float ww = wx ? wp.measureText(wtxt) + hb * 0.3f : 0;
        float width = padL + (clock ? tw + (secs ? hb * 0.08f + sw + hb * 0.3f : hb * 0.26f) : 0)
                + ww + buttons.size() * d * 1.2f + padR;
        float x0 = right ? edge : W - edge - width;
        RectF bar = new RectF(x0, edge, x0 + width, edge + hb);
        if (!bare) glass(c, bar, hb / 2f, W, H, 0x4A9AA3B5, dim);
        float cy = bar.centerY();
        float x = x0 + padL;
        if (clock) {
            String cc = cfg.optString("clock_color", "white");
            int col = "accent".equals(cc) ? accent() : "white".equals(cc) ? Color.WHITE : Faces.parse(cc, Color.WHITE);
            text.setColor(col);
            if (bare) text.setShadowLayer(hb * 0.12f, 0, hb * 0.02f, 0x99000000);
            Paint.FontMetrics fm = text.getFontMetrics();
            float base = cy - (fm.ascent + fm.descent) / 2f;
            c.drawText(tm, x, base, text);
            text.clearShadowLayer();
            x += tw;
            if (secs) {
                x += hb * 0.08f;
                sp.setColor(col);
                sp.setAlpha(0xCC);
                c.drawText(new SimpleDateFormat("ss", Locale.US).format(new Date()), x, base, sp);
                x += sw + hb * 0.3f;
            } else {
                x += hb * 0.26f;
            }
        }
        if (wx) {
            Paint.FontMetrics fm = wp.getFontMetrics();
            if (bare) wp.setShadowLayer(hb * 0.12f, 0, hb * 0.02f, 0x99000000);
            c.drawText(wtxt, x, cy - (fm.ascent + fm.descent) / 2f, wp);
            x += ww;
        }
        for (int i = 0; i < buttons.size(); i++) {
            String which = buttons.get(i);
            x += d * 0.2f;
            float bx = x + d / 2f;
            boolean f = focusBar && i == focus;
            float k = f ? 1.12f : 1f;
            fill.setShader(null);
            int bg = f ? 0xFFF5F5F7 : "music".equals(which) ? 0xFFFA2D48 : bare ? 0 : 0x2EFFFFFF;
            if (bg != 0) {
                fill.setColor(bg);
                c.drawCircle(bx, cy, d * k / 2f, fill);
            }
            Drawable ic = getContext().getDrawable(buttonIcon(which));
            if (ic != null) {
                ic = ic.mutate();
                ic.setTint(f ? 0xFF1C1C1E : Color.WHITE);
                float s = d * 0.58f * k;
                ic.setBounds(Math.round(bx - s / 2), Math.round(cy - s / 2), Math.round(bx + s / 2), Math.round(cy + s / 2));
                ic.draw(c);
            }
            x += d;
        }
    }
}
