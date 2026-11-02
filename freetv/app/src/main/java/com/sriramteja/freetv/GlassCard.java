package com.sriramteja.freetv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Liquid glass: a rounded card that shows the blurred wallpaper behind it, a frosted tint, a light from
 * the top and a bright rim. It re-reads its spot on screen when anything scrolls.
 */
class GlassCard extends LinearLayout {

    private static final Set<GlassCard> ALL = Collections.newSetFromMap(new WeakHashMap<>());

    static void refreshAll() {
        for (GlassCard g : ALL.toArray(new GlassCard[0])) if (g != null) g.invalidate();
    }

    private float radius;
    private int tint;
    private final Paint pic = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private final RectF box = new RectF();
    private final Rect src = new Rect();
    private final int[] at = new int[2];

    GlassCard(Context ctx, float radius, int tint) {
        super(ctx);
        this.radius = radius;
        this.tint = tint;
        setOrientation(VERTICAL);
        setWillNotDraw(false);
        pic.setColorFilter(Backdrop.TONE);
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(Math.max(1f, Fx.dp));
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), GlassCard.this.radius);
            }
        });
        setClipToOutline(true);
    }

    void setTint(int t) {
        tint = t;
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        ALL.add(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        ALL.remove(this);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        box.set(0, 0, w, h);
        clip.reset();
        clip.addRoundRect(box, radius, radius, Path.Direction.CW);
        c.save();
        c.clipPath(clip);
        if (Backdrop.soft != null) {
            getLocationOnScreen(at);
            View root = getRootView();
            int[] r0 = new int[2];
            root.getLocationOnScreen(r0);
            float left = at[0] - r0[0], top = at[1] - r0[1];
            Backdrop.cover(Backdrop.soft, src, left, top, left + w, top + h);
            c.drawBitmap(Backdrop.soft, src, box, pic);
        }
        fill.setShader(null);
        fill.setColor(tint);
        c.drawRect(box, fill);
        fill.setShader(new LinearGradient(0, 0, 0, Math.min(h, Fx.px(90)), 0x24FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, Math.min(h, Fx.px(90)), fill);
        c.restore();
        float half = rim.getStrokeWidth() / 2f;
        box.inset(half, half);
        rim.setShader(new LinearGradient(0, 0, w * 0.6f, h, 0x8CFFFFFF, 0x14FFFFFF, Shader.TileMode.CLAMP));
        c.drawRoundRect(box, radius, radius, rim);
        super.onDraw(c);
    }

    /** A vertical scroll that keeps the glass in step. */
    static final class Scroll extends ScrollView {
        Scroll(Context ctx) {
            super(ctx);
            setVerticalScrollBarEnabled(false);
            setOverScrollMode(OVER_SCROLL_NEVER);
        }

        @Override
        protected void onScrollChanged(int l, int t, int ol, int ot) {
            super.onScrollChanged(l, t, ol, ot);
            refreshAll();
        }
    }

    /** A sideways scroll that keeps the glass in step (carousels). */
    static class HScroll extends HorizontalScrollView {
        HScroll(Context ctx) {
            super(ctx);
            setHorizontalScrollBarEnabled(false);
            setOverScrollMode(OVER_SCROLL_NEVER);
        }

        @Override
        protected void onScrollChanged(int l, int t, int ol, int ot) {
            super.onScrollChanged(l, t, ol, ot);
            refreshAll();
        }
    }
}
