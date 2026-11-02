package com.sriramteja.glasshome;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * The temperature where the TV is, from Open-Meteo (free, no account). Uses the same spot as sunrise
 * (config "lat"/"lon" or the TV's location), asks at most every 30 minutes and keeps the last answer.
 */
final class Weather {
    private Weather() { }

    private static final long EVERY = 30 * 60_000L;
    private static volatile boolean busy;

    /** "24°  Partly cloudy", or null when not known yet. */
    static String line(Context ctx) {
        SharedPreferences p = ctx.getSharedPreferences("glass", Context.MODE_PRIVATE);
        String w = p.getString("weather", null);
        long at = p.getLong("weather_at", 0);
        if (w == null || System.currentTimeMillis() - at > 6 * 3600_000L) return null;
        return w;
    }

    /** Fetches a fresh reading in the background when the last one is older than 30 minutes. */
    static void refresh(Context ctx) {
        Context app = ctx.getApplicationContext();
        SharedPreferences p = app.getSharedPreferences("glass", Context.MODE_PRIVATE);
        if (busy || System.currentTimeMillis() - p.getLong("weather_tried", 0) < EVERY) return;
        double[] spot = Sky.spot(app);
        if (spot == null) {
            Sky.refresh(app);
            return;
        }
        busy = true;
        p.edit().putLong("weather_tried", System.currentTimeMillis()).apply();
        Thread t = new Thread(() -> {
            HttpURLConnection c = null;
            try {
                URL u = new URL(String.format(Locale.ROOT,
                        "https://api.open-meteo.com/v1/forecast?latitude=%.3f&longitude=%.3f"
                                + "&current=temperature_2m,weather_code&timezone=auto", spot[0], spot[1]));
                c = (HttpURLConnection) u.openConnection();
                c.setConnectTimeout(8000);
                c.setReadTimeout(8000);
                try (InputStream in = c.getInputStream()) {
                    ByteArrayOutputStream b = new ByteArrayOutputStream();
                    byte[] buf = new byte[4096];
                    int n;
                    while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
                    JSONObject cur = new JSONObject(new String(b.toByteArray(), StandardCharsets.UTF_8))
                            .getJSONObject("current");
                    long temp = Math.round(cur.getDouble("temperature_2m"));
                    String line = temp + "°  " + words(cur.optInt("weather_code", -1));
                    p.edit().putString("weather", line.trim()).putLong("weather_at", System.currentTimeMillis()).apply();
                }
            } catch (Exception ignored) {
            } finally {
                if (c != null) c.disconnect();
                busy = false;
            }
        }, "glass-weather");
        t.setDaemon(true);
        t.start();
    }

    static String words(int code) {
        if (code == 0) return "Clear";
        if (code == 1) return "Mostly clear";
        if (code == 2) return "Partly cloudy";
        if (code == 3) return "Cloudy";
        if (code == 45 || code == 48) return "Fog";
        if (code >= 51 && code <= 57) return "Drizzle";
        if (code >= 61 && code <= 67) return "Rain";
        if (code >= 71 && code <= 77) return "Snow";
        if (code >= 80 && code <= 82) return "Showers";
        if (code >= 85 && code <= 86) return "Snow showers";
        if (code >= 95) return "Thunderstorm";
        return "";
    }
}
