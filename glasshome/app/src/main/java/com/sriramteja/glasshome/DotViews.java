package com.sriramteja.glasshome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Dot matrix lettering as views: the clock (Dot Matrix theme) and the big app name above the row. */
final class DotViews {
    private DotViews() { }

    /** The time in dots; small dotted seconds beside it when asked. */
    static final class Clock extends View {
        private final float cell;
        private final boolean h12, seconds;
        private int color = Color.WHITE;
        private final Handler main = new Handler(Looper.getMainLooper());
        private final Runnable tick = new Runnable() {
            @Override
            public void run() {
                invalidate();
                long now = System.currentTimeMillis();
                main.postDelayed(this, seconds ? 1000 - now % 1000 + 5 : 60_000 - now % 60_000 + 5);
            }
        };

        /** textPx: the size the clock would have as text (its digits are drawn about cap height). */
        Clock(Context ctx, float textPx, boolean h12, boolean seconds) {
            super(ctx);
            this.cell = textPx * 0.72f / 7f;
            this.h12 = h12;
            this.seconds = seconds;
        }

        void setColor(int c) {
            color = c;
            invalidate();
        }

        private String time() {
            return new SimpleDateFormat(h12 ? "h:mm" : "HH:mm", Locale.getDefault()).format(new Date());
        }

        @Override
        protected void onMeasure(int w, int h) {
            float width = Faces.dotsWidth(h12 ? "12:00" : "00:00", cell);
            if (seconds) width += cell * 2.5f + Faces.dotsWidth("00", cell * 0.42f);
            setMeasuredDimension(Math.round(width + cell), Math.round(cell * 7 + cell * 0.5f));
        }

        @Override
        protected void onDraw(Canvas c) {
            String t = time();
            float w = Faces.dots(c, t, 0, 0, cell, color, 0x14);
            if (seconds) {
                String s = new SimpleDateFormat("ss", Locale.US).format(new Date());
                float sc = cell * 0.42f;
                Faces.dots(c, s, w + cell * 2.5f, cell * 7 - sc * 7, sc, Faces.withAlpha(color, 0xB3), 0);
            }
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            main.post(tick);
        }

        @Override
        protected void onDetachedFromWindow() {
            main.removeCallbacks(tick);
            super.onDetachedFromWindow();
        }
    }

    /** A line of dot lettering (the selected app's name in the Dot Matrix theme). */
    static final class Text extends View {
        private final float cell;
        private String text = "";
        private int color = Color.WHITE;

        Text(Context ctx, float cell) {
            super(ctx);
            this.cell = cell;
        }

        void setText(CharSequence s) {
            text = s == null ? "" : s.toString();
            requestLayout();
            invalidate();
        }

        void setColor(int c) {
            color = c;
            invalidate();
        }

        @Override
        protected void onMeasure(int w, int h) {
            setMeasuredDimension(Math.round(Faces.dotsWidth(text, cell) + cell), Math.round(cell * 7.5f));
        }

        @Override
        protected void onDraw(Canvas c) {
            Faces.dots(c, text, 0, 0, cell, color, 0);
        }
    }
}
