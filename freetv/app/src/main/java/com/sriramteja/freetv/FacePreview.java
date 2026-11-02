package com.sriramteja.freetv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

import org.json.JSONObject;

/** A watch face running live on the phone, drawn by the same Faces code the TV uses. */
final class FacePreview extends View {

    private Faces faces;
    private String face = "glass";
    private JSONObject cfg = new JSONObject();
    private final Faces.Scene scene = new Faces.Scene();
    private final long started = System.currentTimeMillis();
    private final Path clip = new Path();
    private final RectF box = new RectF();
    private Bitmap[] slides;
    private boolean selected;

    FacePreview(Context ctx) {
        super(ctx);
        scene.sun = "06:14     17:44";
        scene.weather = "24°  Partly cloudy";
    }

    void set(String face, JSONObject merged, Bitmap wall, Bitmap[] slideshow) {
        this.face = face;
        this.cfg = merged == null ? new JSONObject() : merged;
        faces = new Faces(face, cfg);
        if (wall != scene.wall) {
            scene.wall = wall;
            scene.accent = wall != null ? Noir.accent(wall) : 0;
        }
        slides = slideshow;
        invalidate();
    }

    void setSelected2(boolean on) {
        selected = on;
        invalidate();
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec);
        setMeasuredDimension(w, Math.round(w * 9f / 16f));
    }

    @Override
    protected void onDraw(Canvas c) {
        if (faces == null || getWidth() == 0) return;
        long now = System.currentTimeMillis();
        scene.now = now;
        scene.t = (now - started) / 1000f;
        if ("photo".equals(face) && slides != null && slides.length > 0) {
            float dur = Math.max(5, cfg.optInt("ss_photo_seconds", Faces.defaults("photo").optInt("ss_photo_seconds", 20)));
            // the phone shows the slideshow faster so it can be seen
            dur = Math.min(dur, 8f);
            long ms = now - started;
            int idx = (int) (ms / (long) (dur * 1000));
            scene.photoIndex = idx;
            scene.photoT = (ms % (long) (dur * 1000)) / 1000f;
            scene.wall = slides[idx % slides.length];
            scene.prev = slides[(idx + slides.length - 1) % slides.length];
            scene.fade = Math.min(1f, scene.photoT / 1.6f);
            if (idx == 0) scene.prev = null;
        } else {
            scene.photoT = scene.t;
            scene.fade = 1f;
            scene.prev = null;
        }
        clip.reset();
        box.set(0, 0, getWidth(), getHeight());
        float r = getHeight() * 0.03f;
        clip.addRoundRect(box, r, r, Path.Direction.CW);
        c.save();
        c.clipPath(clip);
        faces.draw(c, getWidth(), getHeight(), scene);
        c.restore();
        if (selected) {
            android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            p.setStyle(android.graphics.Paint.Style.STROKE);
            p.setStrokeWidth(getResources().getDisplayMetrics().density * 3);
            p.setColor(0xFFFF7A00);
            box.inset(p.getStrokeWidth() / 2, p.getStrokeWidth() / 2);
            c.drawRoundRect(box, r, r, p);
        }
        if (isShown()) postInvalidateDelayed(Math.max(40, Math.min(250, Faces.frameDelay(face, cfg))));
    }
}
