package com.sriramteja.freetv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LightingColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

import org.json.JSONObject;

/** One TV widget drawn on a piece of the TV's background, for the phone's widget lists. */
final class WidgetSample extends View {
    private final JSONObject cfg, widget;
    private final Bitmap wall;
    private final Board.Data data = new Board.Data();
    private final Paint pic = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private final RectF box = new RectF();

    WidgetSample(Context ctx, JSONObject cfg, JSONObject widget, Bitmap wall) {
        super(ctx);
        this.cfg = cfg;
        this.widget = widget;
        this.wall = wall;
        data.weather = "24°  Partly cloudy";
        data.rise = 374;
        data.set = 1064;
        data.ram = 62;
        data.disk = 54;
        data.temp = 47;
        data.accent = wall != null ? Noir.accent(wall) : 0;
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        if (w == 0) return;
        float r = Math.min(w, h) * 0.14f;
        clip.reset();
        box.set(0, 0, w, h);
        clip.addRoundRect(box, r, r, Path.Direction.CW);
        c.save();
        c.clipPath(clip);
        String bg = cfg.optString("bg", "wallpaper");
        int c1 = Faces.parse(cfg.optString("bg_color", "#0B0D12"), 0xFF0B0D12);
        if ("solid".equals(bg)) {
            c.drawColor(c1);
        } else if ("gradient".equals(bg)) {
            Paint g = new Paint();
            g.setShader(new LinearGradient(0, 0, w, h, c1, Faces.parse(cfg.optString("bg_color2", ""), c1), Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, g);
        } else if (wall != null) {
            float bw = wall.getWidth(), bh = wall.getHeight();
            float scale = Math.max(w / bw, h / bh);
            float cw = w / scale, ch = h / scale;
            Rect src = new Rect(Math.round((bw - cw) / 2), Math.round((bh - ch) / 2), Math.round((bw + cw) / 2), Math.round((bh + ch) / 2));
            pic.setColorFilter(new LightingColorFilter(Color.rgb(150, 150, 150), 0));
            c.drawBitmap(wall, src, box, pic);
        } else {
            Paint g = new Paint();
            g.setShader(new LinearGradient(0, 0, w, h, 0xFF1B2A44, 0xFF3A1F3D, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, g);
        }
        data.now = System.currentTimeMillis();
        Board.sample(c, cfg, widget, w, h, h * 3.2f, data);
        c.restore();
        if (isShown()) postInvalidateDelayed(30_000);
    }
}
