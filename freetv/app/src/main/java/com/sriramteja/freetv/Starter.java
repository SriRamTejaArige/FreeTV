package com.sriramteja.freetv;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;

import java.io.ByteArrayOutputStream;
import java.util.Random;

/**
 * Starter wallpapers made by FreeTV itself (no downloads, no one else's pictures): dark aurora skies and
 * soft glass spheres in different colour moods, 1920 x 1080.
 */
final class Starter {
    private Starter() { }

    static final String[] NAMES = {"Violet night", "Ocean", "Sunset", "Emerald", "Crimson", "Gold dust", "Ice", "Neon"};
    private static final int[][] PALETTES = {
            {0xFF7C3AED, 0xFF06B6D4, 0xFFDB2777, 0xFF1E1B4B},
            {0xFF0EA5E9, 0xFF1D4ED8, 0xFF22D3EE, 0xFF020617},
            {0xFFF97316, 0xFFDB2777, 0xFFFACC15, 0xFF1C0A1F},
            {0xFF10B981, 0xFF0D9488, 0xFF84CC16, 0xFF02140F},
            {0xFFDC2626, 0xFF7F1D1D, 0xFFF43F5E, 0xFF0A0303},
            {0xFFF59E0B, 0xFFB45309, 0xFFFDE68A, 0xFF0A0703},
            {0xFFBAE6FD, 0xFF38BDF8, 0xFFE0F2FE, 0xFF050B12},
            {0xFFE11D48, 0xFF7C3AED, 0xFF06B6D4, 0xFF05010A},
    };

    static int count() {
        return PALETTES.length;
    }

    /** One wallpaper as a JPEG. */
    static byte[] make(int index, int w, int h) {
        Bitmap b = draw(index, w, h);
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        b.compress(Bitmap.CompressFormat.JPEG, 88, o);
        b.recycle();
        return o.toByteArray();
    }

    /** One wallpaper as a picture (small ones for the phone's previews). */
    static Bitmap draw(int index, int w, int h) {
        int[] pal = PALETTES[index % PALETTES.length];
        Random rnd = new Random(1000 + index * 77L);
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        p.setShader(new LinearGradient(0, 0, 0, h, pal[3], 0xFF000000, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        // aurora clouds
        for (int i = 0; i < 7; i++) {
            int col = pal[i % 3];
            float x = w * (0.1f + rnd.nextFloat() * 0.8f), y = h * (0.15f + rnd.nextFloat() * 0.7f);
            float r = Math.max(w, h) * (0.18f + rnd.nextFloat() * 0.3f);
            int a = 0x40 + rnd.nextInt(0x50);
            p.setShader(new RadialGradient(x, y, r, (col & 0x00FFFFFF) | (a << 24), col & 0x00FFFFFF, Shader.TileMode.CLAMP));
            c.drawCircle(x, y, r, p);
        }
        // a soft band of light across the sky
        p.setShader(new LinearGradient(0, h * 0.35f, w, h * 0.65f, new int[]{0x00FFFFFF, (pal[1] & 0x00FFFFFF) | 0x33000000,
                0x00FFFFFF}, null, Shader.TileMode.CLAMP));
        c.save();
        c.rotate(-12, w / 2f, h / 2f);
        c.drawRect(-w * 0.2f, h * 0.42f, w * 1.2f, h * 0.58f, p);
        c.restore();
        // glass spheres
        for (int i = 0; i < 3; i++) {
            float r = h * (0.08f + rnd.nextFloat() * 0.14f);
            float x = w * (0.55f + rnd.nextFloat() * 0.4f), y = h * (0.2f + rnd.nextFloat() * 0.6f);
            p.setShader(new RadialGradient(x - r * 0.35f, y - r * 0.4f, r * 1.4f, 0x66FFFFFF,
                    (pal[i % 3] & 0x00FFFFFF) | 0x14000000, Shader.TileMode.CLAMP));
            c.drawCircle(x, y, r, p);
            p.setShader(null);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(2f, r * 0.02f));
            p.setColor(0x59FFFFFF);
            c.drawCircle(x, y, r, p);
            p.setStyle(Paint.Style.FILL);
        }
        // fine grain so gradients don't band
        p.setShader(null);
        int grain = Math.round(9000f * w * h / (1920f * 1080f));
        for (int i = 0; i < grain; i++) {
            p.setColor(rnd.nextBoolean() ? 0x0AFFFFFF : 0x0A000000);
            c.drawPoint(rnd.nextFloat() * w, rnd.nextFloat() * h, p);
        }
        return b;
    }
}
