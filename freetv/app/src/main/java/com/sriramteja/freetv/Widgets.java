package com.sriramteja.freetv;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

import java.util.ArrayList;
import java.util.List;

/** FreeTV's own controls: a glass switch, a big round remote pad and a swipe touchpad. */
final class Widgets {
    private Widgets() { }

    interface OnToggle { void changed(boolean on); }

    interface OnKey { void key(String code); }

    // ------------------------------------------------------------------ switch

    /** A pill switch: frosted when off, the brand gradient with a glow when on. */
    static final class Switch extends View {
        private boolean on;
        private float t;
        private OnToggle listener;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        Switch(Context ctx, boolean on) {
            super(ctx);
            this.on = on;
            t = on ? 1f : 0f;
            setClickable(true);
            setOnClickListener(v -> set(!this.on, true));
        }

        void setListener(OnToggle l) {
            listener = l;
        }

        boolean isOn() {
            return on;
        }

        void set(boolean value, boolean user) {
            if (value == on && user) return;
            on = value;
            ValueAnimator a = ValueAnimator.ofFloat(t, on ? 1f : 0f).setDuration(260);
            a.setInterpolator(new OvershootInterpolator(1.6f));
            a.addUpdateListener(x -> {
                t = (float) x.getAnimatedValue();
                invalidate();
            });
            a.start();
            if (user) {
                Fx.tick(this);
                if (listener != null) listener.changed(on);
            }
        }

        @Override
        protected void onMeasure(int w, int h) {
            setMeasuredDimension(Fx.px(52), Fx.px(32));
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), r = h / 2f;
            float k = Math.max(0f, Math.min(1f, t));
            RectF track = new RectF(0, 0, w, h);
            p.setShader(null);
            p.setColor(0x26FFFFFF);
            c.drawRoundRect(track, r, r, p);
            if (k > 0f) {
                p.setShader(new LinearGradient(0, 0, w, 0, Fx.VIOLET, Fx.CYAN, Shader.TileMode.CLAMP));
                p.setAlpha(Math.round(255 * k));
                c.drawRoundRect(track, r, r, p);
                p.setAlpha(255);
            }
            p.setShader(null);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1f, Fx.dp));
            p.setColor(0x40FFFFFF);
            c.drawRoundRect(new RectF(0.5f, 0.5f, w - 0.5f, h - 0.5f), r, r, p);
            p.setStyle(Paint.Style.FILL);
            float knob = r - Fx.px(4);
            float cx = r + (w - 2 * r) * t;
            p.setColor(0x40000000);
            c.drawCircle(cx, r + Fx.px(1.5f), knob, p);
            p.setColor(0xFFFFFFFF);
            c.drawCircle(cx, r, knob, p);
        }
    }

    // ------------------------------------------------------------------ round pad

    /** A round glass pad: four arrows and OK in the middle; holding an arrow repeats it. */
    @SuppressLint("ViewConstructor")
    static final class Pad extends View {
        private OnKey keys;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Handler main = new Handler(Looper.getMainLooper());
        private int pressed = -1;      // 0 up 1 right 2 down 3 left 4 OK
        private boolean longDone;
        private float glow;
        private final String[] codes = {"KEYCODE_DPAD_UP", "KEYCODE_DPAD_RIGHT", "KEYCODE_DPAD_DOWN", "KEYCODE_DPAD_LEFT",
                "KEYCODE_DPAD_CENTER"};
        private final Runnable repeat = new Runnable() {
            @Override
            public void run() {
                if (pressed >= 0 && pressed < 4) {
                    keys.key(codes[pressed]);
                    Fx.tick(Pad.this);
                    main.postDelayed(this, 130);
                }
            }
        };
        private final Runnable hold = () -> {
            if (pressed == 4) {
                longDone = true;
                keys.key("LONG_DPAD_CENTER");
                Fx.tick(this);
            }
        };

        Pad(Context ctx, OnKey keys) {
            super(ctx);
            this.keys = keys;
        }

        @Override
        protected void onMeasure(int w, int h) {
            int s = Math.min(MeasureSpec.getSize(w), Fx.px(300));
            setMeasuredDimension(MeasureSpec.getSize(w), s);
        }

        private int region(float x, float y) {
            float cx = getWidth() / 2f, cy = getHeight() / 2f, r = getHeight() / 2f;
            float dx = x - cx, dy = y - cy;
            double d = Math.sqrt(dx * dx + dy * dy);
            if (d > r) return -1;
            if (d < r * 0.36f) return 4;
            double a = Math.toDegrees(Math.atan2(dy, dx));
            if (a > -135 && a <= -45) return 0;
            if (a > -45 && a <= 45) return 1;
            if (a > 45 && a <= 135) return 2;
            return 3;
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public boolean onTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    pressed = region(e.getX(), e.getY());
                    if (pressed < 0) return false;
                    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                    longDone = false;
                    Fx.tick(this);
                    flash();
                    if (pressed < 4) {
                        keys.key(codes[pressed]);
                        main.postDelayed(repeat, 450);
                    } else {
                        main.postDelayed(hold, 600);
                    }
                    invalidate();
                    return true;
                case MotionEvent.ACTION_UP:
                    if (pressed == 4 && !longDone) keys.key(codes[4]);
                    // fall through
                case MotionEvent.ACTION_CANCEL:
                    main.removeCallbacks(repeat);
                    main.removeCallbacks(hold);
                    pressed = -1;
                    invalidate();
                    return true;
                default:
                    return true;
            }
        }

        private void flash() {
            ValueAnimator a = ValueAnimator.ofFloat(1f, 0f).setDuration(500);
            a.setInterpolator(new DecelerateInterpolator());
            a.addUpdateListener(x -> {
                glow = (float) x.getAnimatedValue();
                invalidate();
            });
            a.start();
        }

        @Override
        protected void onDraw(Canvas c) {
            float cx = getWidth() / 2f, cy = getHeight() / 2f, r = getHeight() / 2f - Fx.px(4);
            p.setStyle(Paint.Style.FILL);
            p.setShader(new RadialGradient(cx, cy - r * 0.4f, r * 1.4f, 0x30FFFFFF, 0x0DFFFFFF, Shader.TileMode.CLAMP));
            c.drawCircle(cx, cy, r, p);
            p.setShader(null);
            if (pressed >= 0 && pressed < 4) {
                p.setShader(new RadialGradient(cx, cy, r, new int[]{0x00000000, 0x00000000, 0x668B5CF6},
                        new float[]{0f, 0.35f, 1f}, Shader.TileMode.CLAMP));
                RectF o = new RectF(cx - r, cy - r, cx + r, cy + r);
                float start = new float[]{-135, -45, 45, 135}[pressed];
                c.drawArc(o, start, 90, true, p);
                p.setShader(null);
            }
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1f, Fx.dp));
            p.setShader(new LinearGradient(cx - r, cy - r, cx + r, cy + r, 0x99FFFFFF, 0x1AFFFFFF, Shader.TileMode.CLAMP));
            c.drawCircle(cx, cy, r, p);
            p.setShader(null);
            // arrows
            p.setStyle(Paint.Style.FILL);
            float d = r * 0.7f, s = r * 0.1f;
            for (int i = 0; i < 4; i++) {
                double a = Math.toRadians(-90 + i * 90);
                float ax = cx + (float) Math.cos(a) * d, ay = cy + (float) Math.sin(a) * d;
                Path tri = new Path();
                double a1 = a, a2 = a + Math.toRadians(130), a3 = a - Math.toRadians(130);
                tri.moveTo(ax + (float) Math.cos(a1) * s, ay + (float) Math.sin(a1) * s);
                tri.lineTo(ax + (float) Math.cos(a2) * s, ay + (float) Math.sin(a2) * s);
                tri.lineTo(ax + (float) Math.cos(a3) * s, ay + (float) Math.sin(a3) * s);
                tri.close();
                p.setColor(pressed == i ? 0xFFFFFFFF : 0xB3FFFFFF);
                c.drawPath(tri, p);
            }
            // OK
            float ok = r * 0.34f * (pressed == 4 ? 0.94f : 1f);
            if (glow > 0f) {
                p.setShader(new RadialGradient(cx, cy, ok * 2.2f, (Fx.VIOLET & 0x00FFFFFF) | (Math.round(0x99 * glow) << 24),
                        Fx.VIOLET & 0x00FFFFFF, Shader.TileMode.CLAMP));
                c.drawCircle(cx, cy, ok * 2.2f, p);
            }
            p.setShader(new LinearGradient(cx - ok, cy - ok, cx + ok, cy + ok, Fx.VIOLET, Fx.CYAN, Shader.TileMode.CLAMP));
            c.drawCircle(cx, cy, ok, p);
            p.setShader(null);
            p.setColor(0xFFFFFFFF);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(Fx.font(700));
            p.setTextSize(ok * 0.55f);
            c.drawText("OK", cx, cy + ok * 0.2f, p);
        }
    }

    // ------------------------------------------------------------------ touchpad

    /** Swipe to move, tap for OK, hold for the long press; your finger leaves a glowing trail. */
    @SuppressLint("ViewConstructor")
    static final class Touchpad extends View {
        private OnKey keys;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Handler main = new Handler(Looper.getMainLooper());
        private final List<float[]> trail = new ArrayList<>();   // x, y, time
        private float startX, startY, lastX, lastY;
        private boolean moved, held;
        private float ripple = 0f, rx, ry;
        private final Runnable hold = () -> {
            if (!moved) {
                held = true;
                keys.key("LONG_DPAD_CENTER");
                Fx.tick(this);
            }
        };

        Touchpad(Context ctx, OnKey keys) {
            super(ctx);
            this.keys = keys;
        }

        @Override
        protected void onMeasure(int w, int h) {
            setMeasuredDimension(MeasureSpec.getSize(w), Fx.px(260));
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public boolean onTouchEvent(MotionEvent e) {
            float x = e.getX(), y = e.getY();
            long now = System.currentTimeMillis();
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                    startX = lastX = x;
                    startY = lastY = y;
                    moved = false;
                    held = false;
                    trail.clear();
                    trail.add(new float[]{x, y, now});
                    main.postDelayed(hold, 550);
                    invalidate();
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    trail.add(new float[]{x, y, now});
                    float step = Fx.px(46);
                    float dx = x - lastX, dy = y - lastY;
                    if (Math.abs(dx) >= step || Math.abs(dy) >= step) {
                        moved = true;
                        main.removeCallbacks(hold);
                        if (Math.abs(dx) > Math.abs(dy)) {
                            keys.key(dx > 0 ? "KEYCODE_DPAD_RIGHT" : "KEYCODE_DPAD_LEFT");
                            lastX += Math.signum(dx) * step;
                            lastY = y;
                        } else {
                            keys.key(dy > 0 ? "KEYCODE_DPAD_DOWN" : "KEYCODE_DPAD_UP");
                            lastY += Math.signum(dy) * step;
                            lastX = x;
                        }
                        Fx.tick(this);
                    }
                    invalidate();
                    return true;
                }
                case MotionEvent.ACTION_UP:
                    main.removeCallbacks(hold);
                    if (!moved && !held && Math.hypot(x - startX, y - startY) < Fx.px(12)) {
                        keys.key("KEYCODE_DPAD_CENTER");
                        Fx.tick(this);
                        rx = x;
                        ry = y;
                        ValueAnimator a = ValueAnimator.ofFloat(0f, 1f).setDuration(450);
                        a.addUpdateListener(v -> {
                            ripple = (float) v.getAnimatedValue();
                            invalidate();
                        });
                        a.start();
                    }
                    invalidate();
                    return true;
                default:
                    main.removeCallbacks(hold);
                    return true;
            }
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), r = Fx.px(24);
            RectF box = new RectF(0, 0, w, h);
            p.setStyle(Paint.Style.FILL);
            p.setShader(new LinearGradient(0, 0, w, h, 0x26FFFFFF, 0x0AFFFFFF, Shader.TileMode.CLAMP));
            c.drawRoundRect(box, r, r, p);
            p.setShader(null);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1f, Fx.dp));
            p.setColor(0x40FFFFFF);
            c.drawRoundRect(new RectF(0.5f, 0.5f, w - 0.5f, h - 0.5f), r, r, p);
            p.setStyle(Paint.Style.FILL);
            p.setColor(0x59FFFFFF);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(Fx.font(500));
            p.setTextSize(Fx.px(13));
            c.drawText("Swipe to move  ·  tap for OK  ·  hold for menu", w / 2f, h - Fx.px(18), p);
            long now = System.currentTimeMillis();
            boolean live = false;
            for (int i = 1; i < trail.size(); i++) {
                float[] a = trail.get(i - 1), b = trail.get(i);
                float age = (now - b[2]) / 600f;
                if (age >= 1f) continue;
                live = true;
                p.setStrokeCap(Paint.Cap.ROUND);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(Fx.px(10) * (1f - age));
                p.setShader(new LinearGradient(a[0], a[1], b[0], b[1], Fx.VIOLET, Fx.CYAN, Shader.TileMode.CLAMP));
                p.setAlpha(Math.round(200 * (1f - age)));
                c.drawLine(a[0], a[1], b[0], b[1], p);
                p.setShader(null);
                p.setAlpha(255);
            }
            p.setStyle(Paint.Style.FILL);
            if (ripple > 0f && ripple < 1f) {
                p.setColor(Fx.CYAN);
                p.setAlpha(Math.round(140 * (1f - ripple)));
                c.drawCircle(rx, ry, Fx.px(70) * ripple, p);
                p.setAlpha(255);
            }
            if (live) postInvalidateOnAnimation();
        }
    }
}
