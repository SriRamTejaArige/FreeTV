package com.sriramteja.glasshome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Shows one watch face (Faces) full screen with the wallpapers, sunrise line and slideshow behind it. */
final class FaceView extends View {

    private final Handler main = new Handler(Looper.getMainLooper());
    private final String override;
    private final Faces.Scene scene = new Faces.Scene();
    private Faces faces;
    private String face = "glass";
    private JSONObject cfg = new JSONObject();
    private long started, shownAt, fadeFrom;
    private boolean running;
    private Thread loader;
    private List<File> slides = new ArrayList<>();

    private final Runnable minute = new Runnable() {
        @Override
        public void run() {
            scene.sun = Sky.line(getContext());
            if (scene.sun != null) scene.sun = scene.sun.replace("↑ ", "").replace("↓ ", "");
            Weather.refresh(getContext());
            scene.weather = Weather.line(getContext());
            invalidate();
            if (!"photo".equals(face)) {
                long next = Store.nextChangeIn(getContext());
                if (next > 0 && next <= 60_000L) main.postDelayed(() -> loadWall(null), next);
            }
            main.postDelayed(this, 60_000L);
        }
    };

    private final Runnable slide = new Runnable() {
        @Override
        public void run() {
            if (slides.isEmpty()) return;
            scene.photoIndex++;
            loadWall(slides.get(Math.floorMod(scene.photoIndex, slides.size())));
            main.postDelayed(this, Math.max(5, photoSeconds()) * 1000L);
        }
    };

    private int photoSeconds() {
        Object v = Faces.value("photo", cfg, "ss_photo_seconds");
        try {
            return Integer.parseInt(String.valueOf(v).replaceAll("\\..*", ""));
        } catch (RuntimeException e) {
            return 20;
        }
    }

    FaceView(Context ctx, String faceOverride) {
        super(ctx);
        override = faceOverride;
    }

    void start() {
        if (running) return;
        running = true;
        cfg = Store.config();
        face = override != null && !override.isEmpty() ? override : cfg.optString("face", "glass");
        faces = new Faces(face, cfg);
        started = System.currentTimeMillis();
        Sky.refresh(getContext());
        if ("photo".equals(face)) {
            slides = Store.wallpapers();
            if (cfg.optBoolean("dark_only", false)) slides = Store.darkOnly(getContext(), slides);
            java.util.Collections.shuffle(slides);
            scene.photoIndex = 0;
            if (!slides.isEmpty()) loadWall(slides.get(0));
            main.postDelayed(slide, Math.max(5, photoSeconds()) * 1000L);
        } else {
            loadWall(null);
        }
        minute.run();
        // the temperature may arrive a few seconds after start
        main.postDelayed(() -> {
            scene.weather = Weather.line(getContext());
            invalidate();
        }, 10_000L);
        invalidate();
    }

    void stop() {
        running = false;
        main.removeCallbacksAndMessages(null);
    }

    @Override
    protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }

    /** null = the home screen's current wallpaper. */
    private void loadWall(File which) {
        if (loader != null && loader.isAlive()) return;
        int w = Math.max(1, getWidth() > 0 ? getWidth() : getResources().getDisplayMetrics().widthPixels);
        int h = Math.max(1, getHeight() > 0 ? getHeight() : getResources().getDisplayMetrics().heightPixels);
        int W = Math.max(w, h), H = Math.min(w, h);
        loader = new Thread(() -> {
            File f = which != null ? which : Store.currentWallpaper(getContext());
            // a little larger than the screen so the slideshow can zoom without going soft
            boolean photo = "photo".equals(face);
            Bitmap pic = Store.decode(f, photo ? Math.round(W * 1.15f) : W, photo ? Math.round(H * 1.15f) : H);
            int accent = pic != null ? Noir.accent(pic) : 0;
            main.post(() -> {
                if (pic == null) return;
                Bitmap old = scene.prev;
                scene.prev = scene.wall;
                scene.wall = pic;
                scene.accent = accent;
                shownAt = System.currentTimeMillis();
                fadeFrom = scene.prev == null ? 0 : shownAt;
                if (old != null && old != scene.prev && old != scene.wall) old.recycle();
                invalidate();
            });
        }, "face-wall");
        loader.start();
    }

    @Override
    protected void onDraw(Canvas c) {
        if (faces == null) return;
        long now = System.currentTimeMillis();
        scene.now = now;
        scene.t = (now - started) / 1000f;
        scene.photoT = (now - shownAt) / 1000f;
        float fadeMs = "photo".equals(face) ? 1600f : 1200f;
        scene.fade = fadeFrom == 0 ? 1f : Math.min(1f, (now - fadeFrom) / fadeMs);
        if (!"photo".equals(face) && scene.fade < 1f && scene.prev != null) {
            // non-photo faces: simple cross-fade by drawing the old picture first
            Faces.Scene back = new Faces.Scene();
            back.wall = scene.prev;
            back.now = now;
            back.t = scene.t;
            back.accent = scene.accent;
            back.sun = scene.sun;
            faces.draw(c, getWidth(), getHeight(), back);
            c.saveLayerAlpha(0, 0, getWidth(), getHeight(), Math.round(255 * scene.fade));
            faces.draw(c, getWidth(), getHeight(), scene);
            c.restore();
        } else {
            faces.draw(c, getWidth(), getHeight(), scene);
        }
        if (running) {
            long delay = scene.fade < 1f ? 33 : Faces.frameDelay(face, cfg);
            postInvalidateDelayed(delay);
        }
    }
}
