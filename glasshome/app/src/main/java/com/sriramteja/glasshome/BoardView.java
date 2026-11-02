package com.sriramteja.glasshome;

import android.content.Context;
import android.graphics.Canvas;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.view.View;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.time.LocalDate;
import java.time.ZoneId;

/** The home screen's widgets (Board) over the wallpaper; refreshes itself each minute (or second). */
final class BoardView extends View {
    private final Board board;
    private final JSONObject cfg;
    private final Board.Data data = new Board.Data();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final boolean fast;
    private long readAt;
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            long now = System.currentTimeMillis();
            if (now - readAt > 55_000) refresh();
            invalidate();
            main.postDelayed(this, fast ? 1000 - now % 1000 + 5 : 60_000 - now % 60_000 + 5);
        }
    };

    BoardView(Context ctx, JSONObject cfg) {
        super(ctx);
        this.cfg = cfg;
        this.board = new Board(cfg);
        this.fast = Board.everySecond(cfg);
        setFocusable(false);
        setClickable(false);
        refresh();
    }

    void setAccent(int a) {
        data.accent = a;
        invalidate();
    }

    /** Weather, sun, RAM, storage and temperature now. */
    void refresh() {
        readAt = System.currentTimeMillis();
        Context ctx = getContext();
        data.weather = Weather.line(ctx);
        double[] spot = Sky.spot(ctx);
        int[] rs = spot == null ? null : Sky.riseAndSet(LocalDate.now(), spot[0], spot[1], ZoneId.systemDefault());
        data.rise = rs == null ? -1 : rs[0];
        data.set = rs == null ? -1 : rs[1];
        long total = -1, avail = -1;
        try (BufferedReader r = new BufferedReader(new FileReader("/proc/meminfo"))) {
            String line;
            while ((line = r.readLine()) != null) {
                String[] t = line.split("\\s+");
                if (t.length < 2) continue;
                if (line.startsWith("MemTotal:")) total = Long.parseLong(t[1]);
                else if (line.startsWith("MemAvailable:")) avail = Long.parseLong(t[1]);
            }
        } catch (Exception ignored) { }
        data.ram = total > 0 && avail >= 0 ? (int) Math.round(100.0 * (total - avail) / total) : -1;
        try {
            StatFs fs = new StatFs(Environment.getDataDirectory().getPath());
            data.disk = (int) Math.round(100.0 * (fs.getTotalBytes() - fs.getAvailableBytes()) / fs.getTotalBytes());
        } catch (RuntimeException e) {
            data.disk = -1;
        }
        data.temp = temperature();
    }

    /** The warmest thermal zone the TV lets apps read, in °C (NaN if none). */
    private static float temperature() {
        float best = Float.NaN;
        File[] zones = new File("/sys/class/thermal").listFiles((d, n) -> n.startsWith("thermal_zone"));
        if (zones == null) return best;
        for (File z : zones) {
            try (BufferedReader r = new BufferedReader(new FileReader(new File(z, "temp")))) {
                String s = r.readLine();
                if (s == null) continue;
                float v = Float.parseFloat(s.trim());
                if (v > 1000) v /= 1000f;
                if (v > 5 && v < 130 && (Float.isNaN(best) || v > best)) best = v;
            } catch (Exception ignored) { }
        }
        return best;
    }

    @Override
    protected void onDraw(Canvas c) {
        data.now = System.currentTimeMillis();
        board.draw(c, getWidth(), getHeight(), data);
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
