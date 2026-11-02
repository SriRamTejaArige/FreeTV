package com.sriramteja.freetv;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.TextView;
import java.io.File;
import java.util.Calendar;
import java.util.Random;

/**
 * The "noir" theme's pieces: white logo-only icons made from the apps' own icons, rolling clock seconds,
 * and the watcher eyes (an original design) that follow the selected app, blink, and spin open on start.
 */
final class Noir {
    private Noir() {}


    /** thin, light, regular, medium, bold or condensed. */
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

    /** A soft blurred copy of a white logo, 1.5 times its size, for the neon halo. */
    static Bitmap halo(Bitmap logo) {
        try {
            int s = logo.getWidth(), big = Math.round(s * 1.5f), off = (big - s) / 2;
            Bitmap out = Bitmap.createBitmap(big, big, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(out);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            p.setColor(Color.WHITE);
            p.setMaskFilter(new BlurMaskFilter(s * 0.09f, BlurMaskFilter.Blur.NORMAL));
            Bitmap alpha = logo.extractAlpha();
            c.drawBitmap(alpha, off, off, p);
            c.drawBitmap(alpha, off, off, p);
            alpha.recycle();
            return out;
        } catch (RuntimeException | OutOfMemoryError e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ dark wallpapers

    /** Average brightness 0..255 of a picture, measured on a tiny decode. -1 if unreadable. */
    static int brightness(File f) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = 32;
            Bitmap b = BitmapFactory.decodeFile(f.getPath(), o);
            if (b == null) return -1;
            long sum = 0;
            int w = b.getWidth(), h = b.getHeight();
            int[] px = new int[w * h];
            b.getPixels(px, 0, w, 0, 0, w, h);
            for (int p : px) sum += (299 * ((p >> 16) & 0xff) + 587 * ((p >> 8) & 0xff) + 114 * (p & 0xff)) / 1000;
            b.recycle();
            return (int) (sum / Math.max(1, px.length));
        } catch (OutOfMemoryError | RuntimeException e) {
            return -1;
        }
    }

    /** The wallpaper's most vivid colour (brightened a little), or 0 when the picture is grey. */
    static int accent(Bitmap pic) {
        try {
            Bitmap s = Bitmap.createScaledBitmap(pic, 48, 27, true);
            int[] px = new int[48 * 27];
            s.getPixels(px, 0, 48, 0, 0, 48, 27);
            s.recycle();
            float[] hsv = new float[3];
            float[] score = new float[px.length];
            for (int i = 0; i < px.length; i++) {
                Color.colorToHSV(px[i], hsv);
                score[i] = hsv[2] > 0.3f && hsv[1] > 0.35f ? hsv[1] * hsv[2] : 0f;
            }
            // the most vivid part of the colourful pixels, even when colour covers only a small area
            int vivid = 0;
            for (float v : score) if (v > 0f) vivid++;
            if (vivid < Math.max(6, px.length / 200)) return 0;
            float[] sorted = new float[vivid];
            int k = 0;
            for (float v : score) if (v > 0f) sorted[k++] = v;
            java.util.Arrays.sort(sorted);
            float cut = sorted[(int) (vivid * 0.6f)];
            long r = 0, g = 0, b = 0;
            int n = 0;
            for (int i = 0; i < px.length; i++) {
                if (score[i] < cut || score[i] <= 0f) continue;
                r += (px[i] >> 16) & 0xff;
                g += (px[i] >> 8) & 0xff;
                b += px[i] & 0xff;
                n++;
            }
            if (n == 0) return 0;
            Color.colorToHSV(Color.rgb((int) (r / n), (int) (g / n), (int) (b / n)), hsv);
            hsv[1] = Math.min(hsv[1], 0.75f);
            hsv[2] = 1f;
            return Color.HSVToColor(hsv);
        } catch (RuntimeException | OutOfMemoryError e) {
            return 0;
        }
    }

    /** c with the given alpha (0..255). */
    static int withAlpha(int c, int a) {
        return (a << 24) | (c & 0xFFFFFF);
    }

    // ------------------------------------------------------------------ white logo icons

    /**
     * The app's logo as a white shape on nothing: whatever differs from the icon's background becomes white,
     * then it is trimmed and centred so every logo has about the same visual size.
     */
    static Bitmap whiteLogo(Drawable icon, int size) {
        if (icon == null) return null;
        final int S = 160;
        Bitmap src = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(src);
        if (icon instanceof AdaptiveIconDrawable) {
            AdaptiveIconDrawable a = (AdaptiveIconDrawable) icon;
            int d = S / 4;
            for (Drawable l : new Drawable[]{a.getBackground(), a.getForeground()}) {
                if (l == null) continue;
                l.setBounds(-d, -d, S + d, S + d);
                l.draw(c);
            }
        } else {
            icon.setBounds(0, 0, S, S);
            icon.draw(c);
        }
        int[] px = new int[S * S];
        src.getPixels(px, 0, S, 0, 0, S, S);
        src.recycle();

        // background = most common colour around the border (or "transparent" if the border is see-through)
        int[] ring = new int[4 * S];
        int n = 0, opaque = 0;
        for (int i = 0; i < S; i++) {
            ring[n++] = px[i + 2 * S];
            ring[n++] = px[i + (S - 3) * S];
            ring[n++] = px[2 + i * S];
            ring[n++] = px[S - 3 + i * S];
        }
        for (int p : ring) if ((p >>> 24) > 200) opaque++;
        float[] alpha = new float[S * S];
        int bg = opaque > ring.length / 2 ? dominant(ring, null) : 0;
        float[] hsv = new float[3];
        float[] white = new float[S * S];
        float wsum = 0;
        for (int i = 0; i < px.length; i++) {
            float a = (px[i] >>> 24) / 255f;
            if (a <= 0f) continue;
            Color.colorToHSV(px[i], hsv);
            if (hsv[1] < 0.28f && hsv[2] > 0.72f) {
                white[i] = a;
                wsum += a;
            }
        }
        float wcov = wsum / px.length;
        Color.colorToHSV(bg, hsv);
        boolean bgLight = (bg >>> 24) >= 128 && hsv[2] > 0.7f && hsv[1] < 0.28f;
        float cover = bgLight || wcov <= 0.02f || wcov >= 0.45f ? mask(px, bg, alpha, null) : -1f;
        if (cover < 0f) alpha = white;
        if (cover > 0.55f) {
            // the icon is one big coloured shape: take its main colour as the background instead
            int inner = dominant(px, alpha);
            float[] second = new float[S * S];
            float c2 = mask(px, inner, second, alpha);
            if (c2 > 0.02f) alpha = second;
        }

        // trim to the logo and fit it into ~78% of the square
        int x0 = S, y0 = S, x1 = -1, y1 = -1;
        for (int y = 0; y < S; y++)
            for (int x = 0; x < S; x++)
                if (alpha[y * S + x] > 0.25f) {
                    if (x < x0) x0 = x;
                    if (x > x1) x1 = x;
                    if (y < y0) y0 = y;
                    if (y > y1) y1 = y;
                }
        if (x1 < 0) return null;
        int bw = x1 - x0 + 1, bh = y1 - y0 + 1;
        int[] logo = new int[bw * bh];
        for (int y = 0; y < bh; y++)
            for (int x = 0; x < bw; x++) {
                int a = Math.round(255 * alpha[(y + y0) * S + x + x0]);
                logo[y * bw + x] = (a << 24) | 0xFFFFFF;
            }
        Bitmap cut = Bitmap.createBitmap(logo, bw, bh, Bitmap.Config.ARGB_8888);
        Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        float box = size * 0.78f;
        float k = Math.min(box / bw, box / bh);
        float w = bw * k, h = bh * k;
        RectF dst = new RectF((size - w) / 2f, (size - h) / 2f, (size + w) / 2f, (size + h) / 2f);
        new Canvas(out).drawBitmap(cut, null, dst, new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG));
        cut.recycle();
        return out;
    }

    /** alpha[i] = how different pixel i is from bg (0..1); returns the share of the square that is "logo". */
    private static float mask(int[] px, int bg, float[] alpha, float[] within) {
        float total = 0;
        boolean clear = (bg >>> 24) < 128;
        for (int i = 0; i < px.length; i++) {
            int p = px[i];
            float a = (p >>> 24) / 255f;
            if (within != null) a = Math.min(a, within[i] > 0.25f ? 1f : 0f);
            if (a <= 0f) { alpha[i] = 0; continue; }
            float v;
            if (clear) {
                v = a;
            } else {
                int dr = ((p >> 16) & 0xff) - ((bg >> 16) & 0xff);
                int dg = ((p >> 8) & 0xff) - ((bg >> 8) & 0xff);
                int db = (p & 0xff) - (bg & 0xff);
                float dist = (float) Math.sqrt(dr * dr + dg * dg + db * db);
                v = Math.max(0f, Math.min(1f, (dist - 45f) / 60f)) * a;
            }
            alpha[i] = v;
            total += v;
        }
        return total / px.length;
    }

    /** Most common colour (4 bits a channel) among opaque pixels, optionally only where weight > 0.25. */
    private static int dominant(int[] px, float[] weight) {
        int[] count = new int[4096];
        long[] r = new long[4096], g = new long[4096], b = new long[4096];
        for (int i = 0; i < px.length; i++) {
            int p = px[i];
            if ((p >>> 24) < 200) continue;
            if (weight != null && weight[i] <= 0.25f) continue;
            int k = (((p >> 20) & 0xf) << 8) | (((p >> 12) & 0xf) << 4) | ((p >> 4) & 0xf);
            count[k]++;
            r[k] += (p >> 16) & 0xff;
            g[k] += (p >> 8) & 0xff;
            b[k] += p & 0xff;
        }
        int best = -1;
        for (int k = 0; k < 4096; k++) if (best < 0 || count[k] > count[best]) best = k;
        if (best < 0 || count[best] == 0) return 0;
        int c = count[best];
        return Color.rgb((int) (r[best] / c), (int) (g[best] / c), (int) (b[best] / c));
    }

    // ------------------------------------------------------------------ rolling seconds

    /** Two digits that roll upward every second, drawn on the clock's baseline. */
    static final class Seconds extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Handler main = new Handler(Looper.getMainLooper());
        private final TextView clock;
        private String now = "", before = "";
        private float t = 1f;
        private ValueAnimator roll;
        private boolean running;

        private final Runnable tick = new Runnable() {
            @Override
            public void run() {
                String s = two(Calendar.getInstance().get(Calendar.SECOND));
                if (!s.equals(now)) {
                    before = now;
                    now = s;
                    if (roll != null) roll.cancel();
                    roll = ValueAnimator.ofFloat(0f, 1f).setDuration(420);
                    roll.setInterpolator(new DecelerateInterpolator(1.8f));
                    roll.addUpdateListener(a -> {
                        t = (float) a.getAnimatedValue();
                        invalidate();
                    });
                    roll.start();
                }
                main.postDelayed(this, 1000 - System.currentTimeMillis() % 1000 + 5);
            }
        };

        Seconds(Context ctx, TextView clock, float textPx) {
            super(ctx);
            this.clock = clock;
            paint.setColor(0xCCFFFFFF);
            paint.setTextSize(textPx);
            paint.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
            now = two(Calendar.getInstance().get(Calendar.SECOND));
        }

        void setColor(int c) {
            paint.setColor(c);
            invalidate();
        }

        void setTypeface(Typeface t) {
            paint.setTypeface(t);
            requestLayout();
        }

        private static String two(int v) {
            return v < 10 ? "0" + v : String.valueOf(v);
        }

        @Override
        protected void onMeasure(int w, int h) {
            setMeasuredDimension(Math.round(paint.measureText("00") * 1.08f), MeasureSpec.getSize(h));
        }

        @Override
        protected void onWindowVisibilityChanged(int v) {
            super.onWindowVisibilityChanged(v);
            boolean show = v == VISIBLE;
            if (show && !running) {
                running = true;
                now = two(Calendar.getInstance().get(Calendar.SECOND));
                before = now;
                t = 1f;
                main.post(tick);
            } else if (!show && running) {
                running = false;
                main.removeCallbacks(tick);
                if (roll != null) roll.cancel();
            }
        }

        @Override
        protected void onDraw(Canvas c) {
            float base = clock.getTop() + clock.getBaseline() - getTop();
            float rise = paint.getTextSize() * 0.9f;
            float digit = paint.measureText("0");
            c.save();
            c.clipRect(0, base - paint.getTextSize() * 1.0f, getWidth(), base + paint.getTextSize() * 0.25f);
            for (int i = 0; i < 2; i++) {
                char a = before.length() == 2 ? before.charAt(i) : ' ';
                char b = now.charAt(i);
                float x = i * digit * 1.04f;
                if (a == b || t >= 1f) {
                    paint.setAlpha(0xCC);
                    c.drawText(String.valueOf(b), x, base, paint);
                } else {
                    paint.setAlpha(Math.round(0xCC * (1 - t)));
                    c.drawText(String.valueOf(a), x, base - rise * t, paint);
                    paint.setAlpha(Math.round(0xCC * t));
                    c.drawText(String.valueOf(b), x, base + rise * (1 - t), paint);
                }
            }
            c.restore();
        }
    }

    // ------------------------------------------------------------------ watcher eyes

    /**
     * Two glowing almond eyes (original design). The pupils glance toward the selected app, the eyes blink
     * every few seconds, and when Home starts a ring of light spins once while the eyes open.
     */
    static final class Watcher extends View {
        private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint iris = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint pupil = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path eye = new Path();
        private final Handler main = new Handler(Looper.getMainLooper());
        private final Random random = new Random();
        private float lookX, lookY;      // -1..1, where the pupils point
        private float open = 1f;         // eyelid: 1 open, 0 shut
        private float intro = 1f;        // 0..1 start-up spin
        private ValueAnimator look, blink, start;
        private boolean running;

        private final Runnable blinker = new Runnable() {
            @Override
            public void run() {
                blink();
                main.postDelayed(this, 3500 + random.nextInt(4000));
            }
        };

        Watcher(Context ctx) {
            super(ctx);
            setLayerType(LAYER_TYPE_SOFTWARE, null);  // the blur glow needs it; the view is small
            glow.setColor(0x8078C8FF);
            fill.setColor(0xF00E121A);
            rim.setStyle(Paint.Style.STROKE);
            rim.setColor(0xFFC8E8FF);
            iris.setColor(0xFFAAE1FF);
            pupil.setColor(0xFF0A0C12);
            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeCap(Paint.Cap.ROUND);
            ring.setColor(0xFFBFE6FF);
        }

        /** Colour the glow and iris (0 = the default ice blue). */
        void setAccent(int c) {
            if (c == 0) c = 0xFFAAE1FF;
            glow.setColor(withAlpha(c, 0x80));
            iris.setColor(0xFF000000 | c);
            ring.setColor(0xFF000000 | c);
            invalidate();
        }

        /** Point the pupils at a spot on screen (screen pixels). */
        void lookAt(float sx, float sy) {
            int[] at = new int[2];
            getLocationOnScreen(at);
            float cx = at[0] + getWidth() / 2f, cy = at[1] + getHeight() / 2f;
            float dx = sx - cx, dy = sy - cy;
            float len = (float) Math.max(1, Math.sqrt(dx * dx + dy * dy));
            float toX = dx / len, toY = Math.max(-1f, Math.min(1f, dy / len));
            float fromX = lookX, fromY = lookY;
            if (look != null) look.cancel();
            look = ValueAnimator.ofFloat(0f, 1f).setDuration(320);
            look.setInterpolator(new DecelerateInterpolator(2f));
            look.addUpdateListener(a -> {
                float k = (float) a.getAnimatedValue();
                lookX = fromX + (toX - fromX) * k;
                lookY = fromY + (toY - fromY) * k;
                invalidate();
            });
            look.start();
        }

        void blink() {
            if (blink != null && blink.isRunning()) return;
            blink = ValueAnimator.ofFloat(1f, 0.06f, 1f).setDuration(230);
            blink.addUpdateListener(a -> {
                open = (float) a.getAnimatedValue();
                invalidate();
            });
            blink.start();
        }

        /** Once, when Home first shows: a ring of light spins while the eyes open. */
        void spinIn() {
            if (start != null) start.cancel();
            start = ValueAnimator.ofFloat(0f, 1f).setDuration(1700);
            start.setInterpolator(new LinearInterpolator());
            start.addUpdateListener(a -> {
                intro = (float) a.getAnimatedValue();
                open = Math.max(0.04f, Math.min(1f, (intro - 0.45f) / 0.4f));
                invalidate();
            });
            start.start();
        }

        @Override
        protected void onWindowVisibilityChanged(int v) {
            super.onWindowVisibilityChanged(v);
            if (v == VISIBLE && !running) {
                running = true;
                main.postDelayed(blinker, 2500);
            } else if (v != VISIBLE && running) {
                running = false;
                main.removeCallbacks(blinker);
            }
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            float eyeW = w * 0.36f, eyeH = h * 0.42f;
            float gapX = w * 0.07f;
            float cy = h / 2f;
            rim.setStrokeWidth(Math.max(1.5f, h * 0.035f));
            glow.setMaskFilter(new BlurMaskFilter(h * 0.22f, BlurMaskFilter.Blur.NORMAL));

            if (intro < 1f) {
                // spinning ring of light around both eyes, fading out as they open
                float r = Math.min(w, h * 2.2f) * 0.48f;
                float ang = intro * 540f;
                ring.setStrokeWidth(h * 0.05f);
                ring.setAlpha(Math.round(255 * Math.min(1f, (1f - intro) * 2.2f)));
                RectF o = new RectF(w / 2f - r, cy - r * 0.45f, w / 2f + r, cy + r * 0.45f);
                for (int k = 0; k < 3; k++) c.drawArc(o, ang + k * 120f, 70f, false, ring);
            }

            for (int side = -1; side <= 1; side += 2) {
                float ex = w / 2f + side * (gapX / 2f + eyeW / 2f);
                float hh = eyeH / 2f * open;
                eye.reset();
                // almond with a sharper outer corner, tilted slightly upward outward
                float inner = ex - side * eyeW / 2f, outer = ex + side * eyeW / 2f;
                eye.moveTo(inner, cy + eyeH * 0.06f);
                eye.cubicTo(inner + side * eyeW * 0.25f, cy - hh * 1.25f, outer - side * eyeW * 0.3f, cy - hh * 1.35f,
                        outer, cy - eyeH * 0.16f);
                eye.cubicTo(outer - side * eyeW * 0.25f, cy + hh * 1.15f, inner + side * eyeW * 0.25f, cy + hh * 1.05f,
                        inner, cy + eyeH * 0.06f);
                eye.close();
                c.drawPath(eye, glow);
                c.drawPath(eye, fill);
                c.save();
                c.clipPath(eye);
                float px = ex + lookX * eyeW * 0.26f, py = cy + lookY * eyeH * 0.18f;
                float ir = eyeH * 0.36f;
                c.drawCircle(px, py, ir, iris);
                c.drawCircle(px, py, ir * 0.45f, pupil);
                c.restore();
                c.drawPath(eye, rim);
            }
        }
    }
}
