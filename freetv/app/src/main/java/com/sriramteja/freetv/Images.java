package com.sriramteja.freetv;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.media.ExifInterface;
import android.net.Uri;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Turns phone photos (including Google Photos picks) into 1920x1080 JPEGs under 300 KB for the TV. */
final class Images {
    private Images() { }

    static final int W = 1920, H = 1080, MAX_BYTES = 300_000, MAX_PICTURES = 100;

    static List<File> list(File dir) {
        File[] f = dir.listFiles((d, n) -> n.toLowerCase().endsWith(".jpg"));
        List<File> out = new ArrayList<>();
        if (f != null) {
            Arrays.sort(f);
            out.addAll(Arrays.asList(f));
        }
        return out;
    }

    /** Next free name: 001.jpg, 002.jpg ... */
    static File nextFile(File dir) {
        int max = 0;
        for (File f : list(dir)) {
            try {
                max = Math.max(max, Integer.parseInt(f.getName().replace(".jpg", "")));
            } catch (NumberFormatException ignored) { }
        }
        return new File(dir, String.format(java.util.Locale.ROOT, "%03d.jpg", max + 1));
    }

    /** Saves one picked photo as a TV wallpaper. Returns false when it could not be read. */
    static boolean add(ContentResolver cr, Uri uri, File dir) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = cr.openInputStream(uri)) {
                BitmapFactory.decodeStream(in, null, bounds);
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false;
            int rotation = rotation(cr, uri);
            int w = bounds.outWidth, h = bounds.outHeight;
            if (rotation == 90 || rotation == 270) { int t = w; w = h; h = t; }
            int sample = 1;
            while (w / (sample * 2) >= W && h / (sample * 2) >= H) sample *= 2;
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            Bitmap src;
            try (InputStream in = cr.openInputStream(uri)) {
                src = BitmapFactory.decodeStream(in, null, opts);
            }
            if (src == null) return false;
            if (rotation != 0) {
                Matrix m = new Matrix();
                m.postRotate(rotation);
                Bitmap r = Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), m, true);
                if (r != src) src.recycle();
                src = r;
            }
            Bitmap out = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
            float scale = Math.max((float) W / src.getWidth(), (float) H / src.getHeight());
            int cw = Math.round(W / scale), ch = Math.round(H / scale);
            int cx = (src.getWidth() - cw) / 2, cy = (src.getHeight() - ch) / 2;
            new Canvas(out).drawBitmap(src, new Rect(cx, cy, cx + cw, cy + ch), new Rect(0, 0, W, H),
                    new Paint(Paint.FILTER_BITMAP_FLAG));
            src.recycle();
            byte[] jpg = null;
            for (int q : new int[]{85, 78, 70, 62, 55}) {
                ByteArrayOutputStream b = new ByteArrayOutputStream();
                out.compress(Bitmap.CompressFormat.JPEG, q, b);
                jpg = b.toByteArray();
                if (jpg.length <= MAX_BYTES) break;
            }
            out.recycle();
            try (OutputStream o = new FileOutputStream(nextFile(dir))) {
                o.write(jpg);
            }
            return true;
        } catch (IOException | RuntimeException | OutOfMemoryError e) {
            return false;
        }
    }

    private static int rotation(ContentResolver cr, Uri uri) {
        try (InputStream in = cr.openInputStream(uri)) {
            if (in == null) return 0;
            int o = new ExifInterface(in).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            return o == ExifInterface.ORIENTATION_ROTATE_90 ? 90 : o == ExifInterface.ORIENTATION_ROTATE_180 ? 180
                    : o == ExifInterface.ORIENTATION_ROTATE_270 ? 270 : 0;
        } catch (IOException | RuntimeException e) {
            return 0;
        }
    }

    static Bitmap thumb(File f) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inSampleSize = 8;
        return BitmapFactory.decodeFile(f.getPath(), o);
    }
}
