package com.sriramteja.freetv;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Calendar;

/**
 * The themes. Each one is only a set of default settings; anything in config.json overrides it, so every
 * detail of every theme can still be changed (the phone app writes those overrides).
 */
final class Themes {
    private Themes() { }

    static final String[] IDS = {"clean", "glass", "float", "noir", "neon", "minimal", "plush", "limitless",
            "daynight", "soft", "dot", "material", "minimalist"};
    static final String[] NAMES = {"Clean", "Glass", "Float", "Noir", "Neon", "Minimal", "Plush", "Limitless",
            "Day & Night", "Soft UI", "Dot Matrix", "Material", "Minimalist"};

    static String name(String id) {
        for (int i = 0; i < IDS.length; i++) if (IDS[i].equals(id)) return NAMES[i];
        return id;
    }

    private static JSONArray widgets(String... typeAndPos) {
        JSONArray a = new JSONArray();
        try {
            for (int i = 0; i + 1 < typeAndPos.length; i += 2) {
                a.put(new JSONObject().put("type", typeAndPos[i]).put("pos", typeAndPos[i + 1]));
            }
        } catch (JSONException ignored) { }
        return a;
    }

    static JSONObject preset(String id) {
        JSONObject p = new JSONObject();
        try {
            // shared defaults
            p.put("dock_style", "icons").put("dock_side", "left").put("icon_style", "original")
                    .put("icon_size", 10.5).put("icon_gap", 4.2).put("icon_idle", 85).put("focus_scale", 122)
                    .put("labels", true).put("label_size", 24).put("focus_ring", false)
                    .put("glow", false).put("glow_color", "auto").put("glow_strength", 55).put("glow_size", 46)
                    .put("accent", "auto").put("wallpaper_dim", 0).put("dark_only", false).put("dark_level", 45)
                    .put("shade", 0).put("bottom_shade", 0).put("bar_style", "bare").put("bar_clock", true).put("clock", "24")
                    .put("clock_font", "medium").put("clock_scale", 100).put("clock_color", "white")
                    .put("clock_style", "text").put("seconds", false).put("edge", 4.5)
                    .put("buttons", new JSONArray().put("memory").put("inputs").put("settings"))
                    .put("bar_weather", true)
                    .put("bg", "wallpaper").put("bg_color", "#0B0D12").put("bg_color2", "#1B2A44")
                    .put("ink", "light").put("tile_style", "glass")
                    .put("widget_style", "auto").put("widget_size", 100).put("widgets", new JSONArray())
                    .put("day_from", 6).put("night_from", 18)
                    .put("auto_close", new JSONArray().put("com.netflix.ninja").put("com.amazon.amazonvideo.livingroom")
                            .put("com.google.android.youtube.tv").put("in.startv.hotstar").put("com.android.tv"))
                    .put("screensaver", "auto");
            switch (id) {
                case "clean":
                    // the first cleanyourtv look: clock top left, labelled pills, one row of app banners
                    p.put("dock_style", "row").put("icon_style", "banner").put("focus_ring", true).put("focus_scale", 108)
                            .put("wallpaper_dim", 35).put("clock_font", "light").put("row_visible", 4)
                            .put("row_title", true).put("greeting", true);
                    break;
                case "glass":
                    p.put("dock_style", "panel").put("icon_style", "banner").put("bar_style", "pill").put("labels", false);
                    break;
                case "noir":
                    p.put("icon_style", "white").put("icon_idle", 55).put("focus_scale", 118)
                            .put("glow", true).put("glow_strength", 55).put("wallpaper_dim", 36)
                            .put("dark_only", true).put("shade", 75).put("clock_font", "light")
                            .put("clock_scale", 140).put("seconds", true);
                    break;
                case "neon":
                    p.put("icon_style", "accent").put("icon_idle", 60).put("focus_scale", 120)
                            .put("glow", true).put("glow_strength", 75).put("wallpaper_dim", 25)
                            .put("dark_only", true).put("shade", 60).put("clock_font", "thin")
                            .put("clock_scale", 145).put("clock_color", "accent").put("seconds", true);
                    break;
                case "minimal":
                    p.put("icon_style", "white").put("icon_size", 8).put("icon_gap", 5).put("icon_idle", 45)
                            .put("focus_scale", 112).put("labels", false).put("wallpaper_dim", 45)
                            .put("shade", 50).put("clock_font", "thin").put("clock_scale", 130);
                    break;
                case "plush":
                    // big soft pastel cushions in one row, coloured from the wallpaper
                    p.put("dock_style", "row").put("icon_style", "original").put("tile_style", "plush")
                            .put("row_visible", 5).put("row_top", 60).put("row_band", false).put("focus_ring", false)
                            .put("focus_scale", 112).put("wallpaper_dim", 15).put("bottom_shade", 45)
                            .put("clock_font", "medium").put("title_font", "medium").put("greeting", true)
                            .put("row_title", true).put("widgets", widgets("weather", "mr"));
                    break;
                case "limitless":
                    // the wallpaper edge to edge: a huge thin clock, logos floating along the bottom
                    p.put("dock_style", "row").put("icon_style", "white").put("row_visible", 6).put("row_top", 77)
                            .put("row_band", false).put("focus_scale", 128).put("glow", true).put("glow_strength", 60)
                            .put("glow_size", 40).put("wallpaper_dim", 6).put("bottom_shade", 70)
                            .put("row_title", false).put("greeting", false).put("clock_font", "thin")
                            .put("clock_scale", 165).put("buttons", new JSONArray().put("settings"))
                            .put("widgets", widgets("glance", "tr"));
                    break;
                case "daynight":
                    break; // chosen in merge(): the day or the night look by the time
                case "soft":
                    // soft UI: one calm light colour, pillowy tiles that rise when chosen, dark text
                    p.put("bg", "solid").put("bg_color", "#E3E8EF").put("ink", "dark").put("dock_style", "row")
                            .put("tile_style", "soft").put("icon_style", "original").put("row_visible", 5)
                            .put("row_top", 58).put("row_band", false).put("focus_scale", 108).put("focus_ring", false)
                            .put("greeting", true).put("row_title", true).put("title_font", "light")
                            .put("clock_font", "light").put("accent", "#5B6CFF")
                            .put("buttons", new JSONArray().put("inputs").put("settings"))
                            .put("widgets", widgets("calendar", "mr"));
                    break;
                case "dot":
                    // dot matrix: black, white logos, a dotted clock and names, one red dot
                    p.put("bg", "solid").put("bg_color", "#000000").put("dock_style", "row").put("icon_style", "white")
                            .put("row_band", false).put("row_visible", 6).put("row_top", 68).put("focus_scale", 118)
                            .put("icon_idle", 55).put("clock_style", "dot").put("clock_scale", 115)
                            .put("greeting", false).put("row_title", true).put("title_font", "dot")
                            .put("accent", "#D71921").put("widgets", widgets("system", "mr"));
                    break;
                case "material":
                    // material: tonal colours from the wallpaper, pill tiles, a big friendly clock
                    p.put("dock_style", "panel").put("tile_style", "tonal").put("icon_style", "original")
                            .put("bar_style", "pill").put("clock_font", "regular").put("clock_scale", 118)
                            .put("wallpaper_dim", 12).put("widgets", widgets("glance", "br"));
                    break;
                case "minimalist":
                    // only words: the apps as a list of names, nothing else
                    p.put("dock_style", "list").put("wallpaper_dim", 50).put("shade", 70).put("icon_idle", 40)
                            .put("clock_font", "thin").put("clock_scale", 125).put("title_font", "light")
                            .put("buttons", new JSONArray().put("settings"));
                    break;
                default: // float
                    break;
            }
        } catch (JSONException ignored) {
        }
        return p;
    }

    /** Day & Night: a bright look by day and the dark Noir look by night. */
    private static void dayOrNight(JSONObject p, boolean day) throws JSONException {
        if (day) {
            p.put("bg", "gradient").put("bg_color", "#D9E9F8").put("bg_color2", "#FCEFE2").put("ink", "dark")
                    .put("icon_style", "original").put("icon_size", 10).put("labels", true).put("bar_style", "pill")
                    .put("clock_font", "light").put("clock_scale", 125).put("accent", "#F08A3C").put("glow", false)
                    .put("widget_style", "soft").put("widgets", widgets("sun", "mr"));
        } else {
            JSONObject n = preset("noir");
            for (java.util.Iterator<String> it = n.keys(); it.hasNext(); ) {
                String k = it.next();
                p.put(k, n.get(k));
            }
            p.put("widgets", widgets("sun", "mr"));
        }
    }

    /** True in the day hours (config "day_from" to "night_from", 6 to 18 by default). */
    static boolean isDay(JSONObject cfg) {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        int from = cfg.optInt("day_from", 6), to = cfg.optInt("night_from", 18);
        return from <= to ? h >= from && h < to : h >= from || h < to;
    }

    /** The screensaver a theme uses when the setting is "auto". */
    static String face(String theme) {
        switch (theme) {
            case "noir": return "noir";
            case "neon": return "neon";
            case "minimal":
            case "minimalist":
            case "soft": return "minimal";
            case "limitless": return "photo";
            case "daynight": return "analog";
            case "dot": return "dot";
            case "material": return "material";
            default: return "glass";
        }
    }

    /** Theme defaults with the user's settings laid over them. */
    static JSONObject merge(JSONObject user) {
        String id = user.optString("theme", "glass");
        JSONObject out = preset(id);
        try {
            out.put("theme", id);
            if ("daynight".equals(id)) {
                // the hours can be set by the user; the look follows them
                JSONObject hours = new JSONObject().put("day_from", user.optInt("day_from", 6))
                        .put("night_from", user.optInt("night_from", 18));
                boolean day = isDay(hours);
                dayOrNight(out, day);
                out.put("period", day ? "day" : "night");
            }
            JSONArray names = user.names();
            if (names != null) {
                for (int i = 0; i < names.length(); i++) {
                    String k = names.getString(i);
                    out.put(k, user.get(k));
                }
            }
            // "auto" details that follow the app look: banners sit in boxes on a band; logos float, with a glow
            boolean banners = "banner".equals(out.optString("icon_style", "original"));
            if (!user.has("tile_box")) out.put("tile_box", banners);
            if (!user.has("row_band") && !out.has("row_band")) out.put("row_band", banners);
            if ("clean".equals(id) && !user.has("glow")) out.put("glow", !banners);
            if ("auto".equals(out.optString("screensaver", "auto"))) out.put("face", face(id));
            else out.put("face", out.optString("screensaver"));
        } catch (JSONException ignored) {
        }
        return out;
    }
}
