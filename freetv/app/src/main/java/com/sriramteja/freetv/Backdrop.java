package com.sriramteja.freetv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/**
 * The living background: the TV's own wallpaper, softly blurred and darkened, with slow glowing lights
 * drifting over it. Glass cards draw a more blurred copy of the same picture behind themselves.
 */
final class Backdrop extends View {

    static Bitmap soft;                 // very blurred copy for the glass cards
    static int screenW = 1, screenH = 1;
    static final ColorMatrixColorFilter TONE;

    static {
        ColorMatrix m = new ColorMatrix();
        m.setSaturation(1.35f);
        ColorMatrix dark = new ColorMatrix();
        float k = 0.52f;
        dark.setScale(k, k, k, 1f);
        m.postConcat(dark);
        TONE = new ColorMatrixColorFilter(m);
    }

    private Bitmap sharp;
    private int[] glow = {Fx.VIOLET, Fx.CYAN, Fx.PINK};
    private final Paint pic = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint light = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect src = new Rect();
    private final RectF dst = new RectF();
    private final long started = System.currentTimeMillis();
    private Bitmap shown;

    Backdrop(Context ctx) {
        super(ctx);
        pic.setColorFilter(TONE);
        setWallpaper(null, 0);
    }

    /** A new wallpaper (or null for the aurora); accent tints one of the drifting lights. */
    void setWallpaper(Bitmap wall, int accent) {
        if (wall != null && wall == shown) return;
        shown = wall;
        Bitmap base = wall != null ? wall : Fx.aurora(480, 270);
        sharp = Fx.blur(base, 160, 2);
        soft = Fx.blur(base, 48, 3);
        if (accent != 0) glow = new int[]{accent, Fx.VIOLET, Fx.CYAN};
        GlassCard.refreshAll();
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        int[] at = new int[2];
        getLocationOnScreen(at);
        screenW = Math.max(1, w);
        screenH = Math.max(1, h);
    }

    /** Where a bitmap of size bw x bh lands when it covers the backdrop (centre crop). */
    static void cover(Bitmap b, Rect out, float left, float top, float right, float bottom) {
        float bw = b.getWidth(), bh = b.getHeight();
        float scale = Math.max(screenW / bw, screenH / bh);
        float ox = (screenW - bw * scale) / 2f, oy = (screenH - bh * scale) / 2f;
        out.set(Math.round((left - ox) / scale), Math.round((top - oy) / scale),
                Math.round((right - ox) / scale), Math.round((bottom - oy) / scale));
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        if (sharp != null) {
            cover(sharp, src, 0, 0, w, h);
            dst.set(0, 0, w, h);
            c.drawBitmap(sharp, src, dst, pic);
        }
        float t = (System.currentTimeMillis() - started) / 1000f;
        for (int i = 0; i < glow.length; i++) {
            float cx = w * (0.5f + 0.42f * (float) Math.sin(t * 0.07f * (i + 1) + i * 2.1f));
            float cy = h * (0.45f + 0.4f * (float) Math.cos(t * 0.05f * (i + 1.3f) + i));
            float r = Math.max(w, h) * (0.42f + 0.06f * (float) Math.sin(t * 0.2f + i));
            light.setShader(new RadialGradient(cx, cy, r, (glow[i] & 0x00FFFFFF) | 0x38000000,
                    glow[i] & 0x00FFFFFF, Shader.TileMode.CLAMP));
            c.drawCircle(cx, cy, r, light);
        }
        light.setShader(new LinearGradient(0, h * 0.7f, 0, h, 0x00000000, 0x99000000, Shader.TileMode.CLAMP));
        c.drawRect(0, h * 0.7f, w, h, light);
        postInvalidateDelayed(40);
    }
}
