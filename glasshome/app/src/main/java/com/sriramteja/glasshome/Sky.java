package com.sriramteja.glasshome;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;

import org.json.JSONObject;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Sunrise and sunset for the TV's spot: config "lat"/"lon", else the TV's own network location (kept). */
final class Sky {
    private Sky() { }

    /** "↑ 06:18    ↓ 18:09" for today, or null when no spot is known yet. */
    static String line(Context ctx) {
        double[] s = spot(ctx);
        if (s == null) return null;
        int[] rs = riseAndSet(LocalDate.now(), s[0], s[1], ZoneId.systemDefault());
        if (rs == null) return null;
        return String.format(Locale.ROOT, "↑ %02d:%02d     ↓ %02d:%02d", rs[0] / 60, rs[0] % 60, rs[1] / 60, rs[1] % 60);
    }

    static double[] spot(Context ctx) {
        JSONObject c = Store.config();
        if (c.has("lat") && c.has("lon")) {
            double la = c.optDouble("lat"), lo = c.optDouble("lon");
            if (!Double.isNaN(la) && !Double.isNaN(lo)) return new double[]{la, lo};
        }
        SharedPreferences p = ctx.getSharedPreferences("glass", Context.MODE_PRIVATE);
        try {
            String la = p.getString("lat", null), lo = p.getString("lon", null);
            return la == null || lo == null ? null : new double[]{Double.parseDouble(la), Double.parseDouble(lo)};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Asks the TV where it is (Wi-Fi location) in the background and keeps the answer for next time. */
    static void refresh(Context ctx) {
        Context app = ctx.getApplicationContext();
        Thread t = new Thread(() -> {
            Location l = locate(app);
            if (l != null) app.getSharedPreferences("glass", Context.MODE_PRIVATE).edit()
                    .putString("lat", String.valueOf(l.getLatitude()))
                    .putString("lon", String.valueOf(l.getLongitude())).apply();
        }, "glass-sky");
        t.setDaemon(true);
        t.start();
    }

    @SuppressLint("MissingPermission")
    @SuppressWarnings("deprecation")
    private static Location locate(Context ctx) {
        if (ctx.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ctx.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return null;
        }
        LocationManager lm = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) return null;
        try {
            Location best = null;
            for (String p : lm.getProviders(true)) {
                Location l = lm.getLastKnownLocation(p);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            }
            if (best != null) return best;
            if (!lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) return null;
            final Location[] got = new Location[1];
            final CountDownLatch done = new CountDownLatch(1);
            LocationListener once = new LocationListener() {
                @Override public void onLocationChanged(Location location) { got[0] = location; done.countDown(); }
                @Override public void onStatusChanged(String provider, int status, Bundle extras) { }
                @Override public void onProviderEnabled(String provider) { }
                @Override public void onProviderDisabled(String provider) { done.countDown(); }
            };
            lm.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, once, Looper.getMainLooper());
            done.await(20, TimeUnit.SECONDS);
            lm.removeUpdates(once);
            return got[0];
        } catch (RuntimeException | InterruptedException e) {
            return null;
        }
    }

    /** NOAA approximation: local minutes of the day of sunrise and sunset, or null (polar day or night). */
    static int[] riseAndSet(LocalDate date, double latitude, double longitude, ZoneId zone) {
        double g = 2 * Math.PI / 365 * (date.getDayOfYear() - 1);
        double eqTime = 229.18 * (0.000075 + 0.001868 * Math.cos(g) - 0.032077 * Math.sin(g)
                - 0.014615 * Math.cos(2 * g) - 0.040849 * Math.sin(2 * g));
        double decl = 0.006918 - 0.399912 * Math.cos(g) + 0.070257 * Math.sin(g)
                - 0.006758 * Math.cos(2 * g) + 0.000907 * Math.sin(2 * g)
                - 0.002697 * Math.cos(3 * g) + 0.00148 * Math.sin(3 * g);
        double lat = Math.toRadians(latitude);
        double cosHa = Math.cos(Math.toRadians(90.833)) / (Math.cos(lat) * Math.cos(decl)) - Math.tan(lat) * Math.tan(decl);
        if (cosHa < -1 || cosHa > 1) return null;
        double ha = Math.toDegrees(Math.acos(cosHa));
        int offset = ZonedDateTime.of(date.atTime(12, 0), zone).getOffset().getTotalSeconds() / 60;
        int rise = (int) Math.round(720 - 4 * (longitude + ha) - eqTime + offset);
        int set = (int) Math.round(720 - 4 * (longitude - ha) - eqTime + offset);
        return new int[]{((rise % 1440) + 1440) % 1440, ((set % 1440) + 1440) % 1440};
    }
}
