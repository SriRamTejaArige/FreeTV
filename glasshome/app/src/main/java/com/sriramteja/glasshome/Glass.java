package com.sriramteja.glasshome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * Frosted glass. The wallpaper is shrunk to 1/12 and box-blurred once; every glass panel then draws the part
 * of that blurred picture that sits behind it, lightened, with a fine bright rim. Android 11 has no live blur,
 * and this looks the same for a still wallpaper at a fraction of the cost.
 */
final class Glass {
    private Glass() { }

    private static Bitmap backdrop;
    private static int screenW, screenH;
    private static final List<Frame> FRAMES = new ArrayList<>();

    /** The blurred, shrunk copy of a wallpaper. Slow-ish (a few ms): call off the main thread. */
    static Bitmap blurred(Bitmap wallpaper) {
        int w = Math.max(8, wallpaper.getWidth() / 12), h = Math.max(8, wallpaper.getHeight() / 12);
        Bitmap small = Bitmap.createScaledBitmap(wallpaper, w, h, true);
        if (small == wallpaper || !small.isMutable()) small = small.copy(Bitmap.Config.ARGB_8888, true);
        boxBlur(small, 3);
        boxBlur(small, 3);
        boxBlur(small, 2);
        return small;
    }

    /** Main thread: every glass panel now shows this blurred picture (made from a screenW x screenH wallpaper). */
    static void useBackdrop(Bitmap blurredSmall, int wallpaperW, int wallpaperH) {
        backdrop = blurredSmall;
        screenW = wallpaperW;
        screenH = wallpaperH;
        for (Frame f : FRAMES) f.invalidate();
    }

    /** In-place box blur on a small bitmap (horizontal then vertical pass). */
    static void boxBlur(Bitmap b, int r) {
        int w = b.getWidth(), h = b.getHeight();
        int[] px = new int[w * h], out = new int[w * h];
        b.getPixels(px, 0, w, 0, 0, w, h);
        pass(px, out, w, h, r, true);
        pass(out, px, w, h, r, false);
        b.setPixels(px, 0, w, 0, 0, w, h);
    }

    private static void pass(int[] in, int[] out, int w, int h, int r, boolean horizontal) {
        int lines = horizontal ? h : w, len = horizontal ? w : h;
        for (int line = 0; line < lines; line++) {
            for (int i = 0; i < len; i++) {
                int a = 0, rr = 0, g = 0, bb = 0, n = 0;
                for (int k = -r; k <= r; k++) {
                    int j = Math.max(0, Math.min(len - 1, i + k));
                    int p = horizontal ? in[line * w + j] : in[j * w + line];
                    a += p >>> 24; rr += (p >> 16) & 0xff; g += (p >> 8) & 0xff; bb += p & 0xff;
                    n++;
                }
                int v = ((a / n) << 24) | ((rr / n) << 16) | ((g / n) << 8) | (bb / n);
                if (horizontal) out[line * w + i] = v; else out[i * w + line] = v;
            }
        }
    }

    /** A rounded glass panel; its children draw on top and are clipped to its shape. */
    static final class Frame extends FrameLayout {
        private final Paint picture = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        private final Paint tint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path clip = new Path();
        private final RectF box = new RectF();
        private final int[] where = new int[2];
        private float radius;
        private boolean bare;

        Frame(Context ctx, float radius, int tintColor) {
            super(ctx);
            this.radius = radius;
            setWillNotDraw(false);
            tint.setColor(tintColor);
            rim.setStyle(Paint.Style.STROKE);
            rim.setStrokeWidth(Math.max(1f, ctx.getResources().getDisplayMetrics().density));
            rim.setColor(0x47FFFFFF);
            setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View v, Outline o) {
                    o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), Frame.this.radius);
                }
            });
            setClipToOutline(true);
        }

        /** Bare: no glass, no rim, no clipping. Only the children show. */
        void setBare(boolean b) {
            bare = b;
            setClipToOutline(!b);
            setClipChildren(!b);
            setOutlineProvider(b ? null : getOutlineProvider());
            invalidate();
        }

        void setRadius(float r) {
            radius = r;
            invalidateOutline();
            invalidate();
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            FRAMES.add(this);
        }

        @Override
        protected void onDetachedFromWindow() {
            FRAMES.remove(this);
            super.onDetachedFromWindow();
        }

        @Override
        protected void onDraw(Canvas c) {
            if (bare) {
                super.onDraw(c);
                return;
            }
            box.set(0, 0, getWidth(), getHeight());
            clip.reset();
            clip.addRoundRect(box, radius, radius, Path.Direction.CW);
            c.save();
            c.clipPath(clip);
            if (backdrop != null && screenW > 0) {
                getLocationOnScreen(where);
                float sx = backdrop.getWidth() / (float) screenW, sy = backdrop.getHeight() / (float) screenH;
                Rect src = new Rect(Math.round(where[0] * sx), Math.round(where[1] * sy),
                        Math.round((where[0] + getWidth()) * sx), Math.round((where[1] + getHeight()) * sy));
                c.drawBitmap(backdrop, src, box, picture);
            }
            c.drawRect(box, tint);
            c.restore();
            float half = rim.getStrokeWidth() / 2f;
            box.inset(half, half);
            c.drawRoundRect(box, radius, radius, rim);
            super.onDraw(c);
        }
    }
}
