package com.sriramteja.glasshome;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * The themes. Each one is only a set of default settings; anything in config.json overrides it, so every
 * detail of every theme can still be changed (the phone app writes those overrides).
 */
final class Themes {
    private Themes() { }

    static final String[] IDS = {"clean", "glass", "float", "noir", "neon", "minimal"};

    static JSONObject preset(String id) {
        JSONObject p = new JSONObject();
        try {
            // shared defaults
            p.put("dock_style", "icons").put("dock_side", "left").put("icon_style", "original")
                    .put("icon_size", 10.5).put("icon_gap", 4.2).put("icon_idle", 85).put("focus_scale", 122)
                    .put("labels", true).put("label_size", 24).put("focus_ring", false)
                    .put("glow", false).put("glow_color", "auto").put("glow_strength", 55).put("glow_size", 46)
                    .put("accent", "auto").put("wallpaper_dim", 0).put("dark_only", false).put("dark_level", 45)
                    .put("shade", 0).put("bar_style", "bare").put("bar_clock", true).put("clock", "24")
                    .put("clock_font", "medium").put("clock_scale", 100).put("clock_color", "white")
                    .put("seconds", false).put("edge", 4.5)
                    .put("buttons", new JSONArray().put("memory").put("inputs").put("settings"))
                    .put("bar_weather", true)
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
                default: // float
                    break;
            }
        } catch (JSONException ignored) {
        }
        return p;
    }

    /** The screensaver a theme uses when the setting is "auto". */
    static String face(String theme) {
        switch (theme) {
            case "noir": return "noir";
            case "neon": return "neon";
            case "minimal": return "minimal";
            default: return "glass";
        }
    }

    /** Theme defaults with the user's settings laid over them. */
    static JSONObject merge(JSONObject user) {
        String id = user.optString("theme", "glass");
        JSONObject out = preset(id);
        try {
            out.put("theme", id);
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
            if (!user.has("row_band")) out.put("row_band", banners);
            if ("clean".equals(id) && !user.has("glow")) out.put("glow", !banners);
            if ("auto".equals(out.optString("screensaver", "auto"))) out.put("face", face(id));
            else out.put("face", out.optString("screensaver"));
        } catch (JSONException ignored) {
        }
        return out;
    }
}
