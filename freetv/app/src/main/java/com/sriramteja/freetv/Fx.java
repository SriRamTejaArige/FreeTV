package com.sriramteja.freetv;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.TextView;

/** FreeTV's look: the Outfit font, the violet-to-cyan brand gradient, press springs, haptics, small blurs. */
final class Fx {
    private Fx() { }

    static final int VIOLET = 0xFF8B5CF6, CYAN = 0xFF22D3EE, PINK = 0xFFF472B6;
    static final int TEXT = 0xFFFFFFFF, DIM = 0xA6FFFFFF, FAINT = 0x66FFFFFF, RIM = 0x33FFFFFF;
    static final int GOOD = 0xFF34D399, BAD = 0xFFFB7185;

    private static Typeface[] weights;
    static float dp = 1f;

    static void init(Context ctx) {
        dp = ctx.getResources().getDisplayMetrics().density;
        if (weights != null) return;
        int[] w = {300, 400, 500, 600, 700, 800};
        weights = new Typeface[w.length];
        for (int i = 0; i < w.length; i++) {
            try {
                weights[i] = new Typeface.Builder(ctx.getAssets(), "fonts/outfit.ttf")
                        .setFontVariationSettings("'wght' " + w[i]).build();
            } catch (RuntimeException e) {
                weights[i] = Typeface.create("sans-serif", w[i] >= 600 ? Typeface.BOLD : Typeface.NORMAL);
            }
        }
    }

    /** 300 light, 400 regular, 500 medium, 600 semibold, 700 bold, 800 extra bold. */
    static Typeface font(int weight) {
        if (weights == null) return Typeface.DEFAULT;
        int i = Math.max(0, Math.min(weights.length - 1, (weight - 300) / 100));
        return weights[i];
    }

    static int px(float v) {
        return Math.round(v * dp);
    }

    /** Brand gradient across a text's width. */
    static void gradientText(TextView t, int a, int b) {
        t.addOnLayoutChangeListener((v, l, top, r, bot, ol, ot, or, ob) -> {
            float w = Math.max(1, t.getPaint().measureText(String.valueOf(t.getText())));
            t.getPaint().setShader(new LinearGradient(0, 0, w, 0, a, b, Shader.TileMode.CLAMP));
            t.invalidate();
        });
    }

    static GradientDrawable pill(int fill, float radius, int stroke, float strokeW) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(radius);
        if (strokeW > 0) g.setStroke(Math.max(1, Math.round(strokeW)), stroke);
        return g;
    }

    static GradientDrawable brand(float radius) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{VIOLET, CYAN});
        g.setCornerRadius(radius);
        return g;
    }

    /** Shrinks a little under the finger and springs back; a light tick on release. */
    @SuppressLint("ClickableViewAccessibility")
    static void press(View v) {
        v.setOnTouchListener((view, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    view.animate().scaleX(0.94f).scaleY(0.94f).setDuration(90)
                            .setInterpolator(new DecelerateInterpolator()).start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    view.animate().scaleX(1f).scaleY(1f).setDuration(320)
                            .setInterpolator(new OvershootInterpolator(3f)).start();
                    if (e.getActionMasked() == MotionEvent.ACTION_UP) tick(view);
                    break;
                default:
                    break;
            }
            return false;
        });
    }

    static void tick(View v) {
        v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
    }

    /** Fast box blur of a small picture (a few passes look like a gaussian). */
    static Bitmap blur(Bitmap src, int width, int radius) {
        if (src == null) return null;
        int w = Math.max(2, width), h = Math.max(2, Math.round(width * src.getHeight() / (float) src.getWidth()));
        Bitmap small = Bitmap.createScaledBitmap(src, w, h, true).copy(Bitmap.Config.ARGB_8888, true);
        int[] a = new int[w * h], b = new int[w * h];
        small.getPixels(a, 0, w, 0, 0, w, h);
        for (int pass = 0; pass < 3; pass++) {
            box(a, b, w, h, radius, true);
            box(b, a, w, h, radius, false);
        }
        small.setPixels(a, 0, w, 0, 0, w, h);
        return small;
    }

    private static void box(int[] in, int[] out, int w, int h, int r, boolean horizontal) {
        int lines = horizontal ? h : w, len = horizontal ? w : h;
        for (int line = 0; line < lines; line++) {
            for (int i = 0; i < len; i++) {
                int rr = 0, g = 0, bb = 0, n = 0;
                for (int k = -r; k <= r; k++) {
                    int j = Math.max(0, Math.min(len - 1, i + k));
                    int p = horizontal ? in[line * w + j] : in[j * w + line];
                    rr += (p >> 16) & 0xff;
                    g += (p >> 8) & 0xff;
                    bb += p & 0xff;
                    n++;
                }
                int v = 0xFF000000 | ((rr / n) << 16) | ((g / n) << 8) | (bb / n);
                if (horizontal) out[line * w + i] = v;
                else out[i * w + line] = v;
            }
        }
    }

    /** A gentle aurora picture for when there is no TV wallpaper yet. */
    static Bitmap aurora(int w, int h) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint();
        p.setShader(new LinearGradient(0, 0, w, h, new int[]{0xFF0B0620, 0xFF120A35, 0xFF04161F},
                null, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        int[] cols = {0xFF7C3AED, 0xFF06B6D4, 0xFFDB2777, 0xFF2563EB};
        float[][] at = {{0.2f, 0.25f, 0.55f}, {0.85f, 0.35f, 0.5f}, {0.55f, 0.85f, 0.45f}, {0.1f, 0.85f, 0.4f}};
        for (int i = 0; i < cols.length; i++) {
            Paint g = new Paint(Paint.ANTI_ALIAS_FLAG);
            float r = Math.max(w, h) * at[i][2];
            g.setShader(new android.graphics.RadialGradient(w * at[i][0], h * at[i][1], r,
                    (cols[i] & 0x00FFFFFF) | 0x8C000000, cols[i] & 0x00FFFFFF, Shader.TileMode.CLAMP));
            c.drawCircle(w * at[i][0], h * at[i][1], r, g);
        }
        return b;
    }
}
