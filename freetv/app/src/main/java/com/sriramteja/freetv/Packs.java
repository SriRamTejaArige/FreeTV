package com.sriramteja.freetv;

import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

/**
 * Icon packs. Every pack is made on the TV from each app's own logo (the white logo shape from
 * Noir.whiteLogo), so they work for any app: recoloured, outlined, gilded, put in bubbles or badges.
 * The same code draws the phone app's previews.
 */
final class Packs {
    private Packs() { }

    static final String[] IDS = {"banner", "original", "white", "accent", "rainbow", "gold", "sunset", "ice",
            "outline", "bubble", "badge", "chrome"};
    static final String[] NAMES = {"Banners", "App icons", "White logos", "Wallpaper colour + neon", "Rainbow neon",
            "Gold", "Sunset", "Ice", "Neon outline", "Glass bubbles", "White badges", "Chrome"};

    /** Packs drawn from the logo shape (everything except banners and the apps' own icons). */
    static boolean usesLogo(String id) {
        return !"banner".equals(id) && !"original".equals(id) && indexOf(id) >= 0;
    }

    /** Packs coloured with the wallpaper's colour at show time (the bitmap is white, tinted on screen). */
    static boolean tinted(String id) {
        return "accent".equals(id) || "outline".equals(id);
    }

    /** Packs with a glow around the selected app. */
    static boolean haloed(String id) {
        return "accent".equals(id) || "rainbow".equals(id);
    }

    static int indexOf(String id) {
        for (int i = 0; i < IDS.length; i++) if (IDS[i].equals(id)) return i;
        return -1;
    }

    /** Rainbow: each app gets its own colour, spread round the colour wheel. */
    static int hue(int index, int count) {
        float h = (200f + index * 360f / Math.max(1, count)) % 360f;
        return Color.HSVToColor(new float[]{h, 0.72f, 1f});
    }

    /** The pack's picture for one app, from its white logo (same size). index/count only matter for Rainbow. */
    static Bitmap render(String id, Bitmap logo, int index, int count) {
        if (logo == null) return null;
        int s = logo.getWidth();
        try {
            switch (id) {
                case "white":
                case "accent":
                    return logo;
                case "rainbow": {
                    int c = hue(index, count);
                    return fill(logo, new LinearGradient(0, 0, 0, s, lighter(c, 0.35f), c, Shader.TileMode.CLAMP));
                }
                case "gold":
                    return fill(logo, new LinearGradient(0, 0, s * 0.3f, s, new int[]{0xFFFFF6C8, 0xFFF2C14E, 0xFFB8860B,
                            0xFFFFE08A, 0xFFC8961E}, new float[]{0f, 0.3f, 0.55f, 0.75f, 1f}, Shader.TileMode.CLAMP));
                case "sunset":
                    return fill(logo, new LinearGradient(0, 0, s, s, new int[]{0xFFFF3D8B, 0xFFFF7A45, 0xFFFFC24B},
                            null, Shader.TileMode.CLAMP));
                case "ice":
                    return fill(logo, new LinearGradient(0, 0, 0, s, new int[]{0xFFFFFFFF, 0xFFA8F0FF, 0xFF4FC3F7},
                            null, Shader.TileMode.CLAMP));
                case "chrome":
                    return fill(logo, new LinearGradient(0, 0, 0, s, new int[]{0xFFFFFFFF, 0xFF9AA3AD, 0xFFF4F6F8,
                            0xFF5E6670, 0xFFD9DEE3}, new float[]{0f, 0.42f, 0.5f, 0.8f, 1f}, Shader.TileMode.CLAMP));
                case "outline":
                    return outline(logo);
                case "bubble":
                    return bubble(logo);
                case "badge":
                    return badge(logo);
                default:
                    return logo;
            }
        } catch (RuntimeException | OutOfMemoryError e) {
            return logo;
        }
    }

    private static int lighter(int c, float k) {
        return Color.rgb(Math.round(Color.red(c) + (255 - Color.red(c)) * k),
                Math.round(Color.green(c) + (255 - Color.green(c)) * k),
                Math.round(Color.blue(c) + (255 - Color.blue(c)) * k));
    }

    /** The logo's shape painted with a shader (gradients). */
    private static Bitmap fill(Bitmap logo, Shader shader) {
        Bitmap out = Bitmap.createBitmap(logo.getWidth(), logo.getHeight(), Bitmap.Config.ARGB_8888);
        Bitmap alpha = logo.extractAlpha();
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        p.setShader(shader);
        new Canvas(out).drawBitmap(alpha, 0, 0, p);
        alpha.recycle();
        return out;
    }

    /** Only the edge of the logo, glowing like a neon tube (white; tinted with the wallpaper colour). */
    private static Bitmap outline(Bitmap logo) {
        int s = logo.getWidth();
        Bitmap out = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Bitmap alpha = logo.extractAlpha();
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        p.setColor(Color.WHITE);
        // a soft glow round the shape, then a sharp bright edge on top of it
        p.setMaskFilter(new BlurMaskFilter(Math.max(2f, s * 0.05f), BlurMaskFilter.Blur.OUTER));
        p.setAlpha(0xB3);
        c.drawBitmap(alpha, 0, 0, p);
        p.setAlpha(0xFF);
        p.setMaskFilter(new BlurMaskFilter(Math.max(1f, s * 0.011f), BlurMaskFilter.Blur.OUTER));
        for (int i = 0; i < 5; i++) c.drawBitmap(alpha, 0, 0, p);
        p.setMaskFilter(null);
        p.setAlpha(0x1A);
        c.drawBitmap(alpha, 0, 0, p);
        alpha.recycle();
        return out;
    }

    /** A frosted glass bubble with the white logo inside. */
    private static Bitmap bubble(Bitmap logo) {
        int s = logo.getWidth();
        Bitmap out = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        float r = s * 0.48f, cx = s / 2f, cy = s / 2f;
        Paint f = new Paint(Paint.ANTI_ALIAS_FLAG);
        f.setShader(new RadialGradient(cx, cy - r * 0.35f, r * 1.3f, 0x40FFFFFF, 0x14FFFFFF, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, r, f);
        Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(Math.max(1.5f, s * 0.014f));
        rim.setShader(new LinearGradient(0, 0, 0, s, 0xCCFFFFFF, 0x33FFFFFF, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, r - rim.getStrokeWidth() / 2, rim);
        Paint shine = new Paint(Paint.ANTI_ALIAS_FLAG);
        shine.setShader(new LinearGradient(0, cy - r, 0, cy, 0x59FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawOval(new RectF(cx - r * 0.7f, cy - r * 0.92f, cx + r * 0.7f, cy - r * 0.1f), shine);
        float k = 0.56f;
        Paint lp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        c.drawBitmap(logo, null, new RectF(cx - s * k / 2, cy - s * k / 2, cx + s * k / 2, cy + s * k / 2), lp);
        return out;
    }

    /** A white rounded badge with the logo cut in dark. */
    private static Bitmap badge(Bitmap logo) {
        int s = logo.getWidth();
        Bitmap out = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint f = new Paint(Paint.ANTI_ALIAS_FLAG);
        f.setShader(new LinearGradient(0, 0, 0, s, 0xFFFFFFFF, 0xFFE4E7EC, Shader.TileMode.CLAMP));
        float m = s * 0.04f;
        c.drawRoundRect(new RectF(m, m, s - m, s - m), s * 0.24f, s * 0.24f, f);
        Bitmap alpha = logo.extractAlpha();
        Paint lp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        lp.setColor(0xFF16181D);
        float k = 0.66f;
        c.drawBitmap(alpha, null, new RectF(s * (1 - k) / 2, s * (1 - k) / 2, s * (1 + k) / 2, s * (1 + k) / 2), lp);
        alpha.recycle();
        return out;
    }
}
