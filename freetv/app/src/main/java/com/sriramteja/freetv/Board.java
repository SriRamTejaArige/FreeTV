package com.sriramteja.freetv;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * The widgets on the TV's home screen (clock, calendar, weather...), drawn straight onto a canvas so the TV
 * and the phone's preview draw exactly the same thing. Config: "widgets" = [{"type", "pos", options}],
 * "widget_style" (auto, glass, bare, soft, tonal, dot) and "widget_size" (percent).
 */
final class Board {

    static final String[] TYPES = {"clock", "glance", "calendar", "weather", "sun", "system", "world", "countdown",
            "note", "quote", "greeting"};
    static final String[] NAMES = {"Clock", "At a glance", "Calendar", "Weather", "Sunrise & sunset", "System",
            "World clock", "Countdown", "Note", "Quote of the day", "Greeting"};
    static final String[] ABOUT = {"The time, big, with the date", "Today's date and the weather in one line",
            "This month, today marked", "Temperature and the sky now", "Where the sun is between sunrise and sunset",
            "RAM, storage and how warm the TV is", "The time in another city", "Days left until a date you pick",
            "A few words of your own", "A short quote, a new one every day", "Good morning / evening, with your name"};
    static final String[] POS = {"tl", "tr", "ml", "mr", "bl", "bc", "br"};
    static final String[] POS_NAMES = {"Top left", "Top right", "Middle left", "Middle right", "Bottom left",
            "Bottom centre", "Bottom right"};
    static final String[] STYLES = {"auto", "glass", "bare", "soft", "tonal", "dot"};
    static final String[] STYLE_NAMES = {"Theme's own", "Glass card", "No card", "Soft", "Material", "Dot matrix"};
    static final String[][] CITIES = {{"London", "Europe/London"}, {"New York", "America/New_York"},
            {"Dubai", "Asia/Dubai"}, {"Tokyo", "Asia/Tokyo"}, {"Sydney", "Australia/Sydney"},
            {"Singapore", "Asia/Singapore"}, {"Paris", "Europe/Paris"}, {"San Francisco", "America/Los_Angeles"},
            {"Toronto", "America/Toronto"}, {"India", "Asia/Kolkata"}, {"Berlin", "Europe/Berlin"},
            {"Hong Kong", "Asia/Hong_Kong"}};
    private static final String[] QUOTES = {
            "Well begun is half done.|Aristotle", "The journey of a thousand miles begins with one step.|Lao Tzu",
            "Simplicity is the ultimate sophistication.|Leonardo da Vinci", "Fortune favours the bold.|Virgil",
            "Knowing yourself is the beginning of all wisdom.|Aristotle", "What we think, we become.|Buddha",
            "Luck is what happens when preparation meets opportunity.|Seneca", "Be the change you wish to see.|Gandhi",
            "Nothing will come of nothing.|Shakespeare", "Little by little, one travels far.|J. R. R. Tolkien",
            "Act as if what you do makes a difference. It does.|William James", "Dream big and dare to fail.|Norman Vaughan",
            "Stay hungry, stay foolish.|Stewart Brand", "It always seems impossible until it is done.|Nelson Mandela",
            "Where there is love there is life.|Gandhi", "Turn your wounds into wisdom.|Oprah Winfrey",
            "The best time to plant a tree was 20 years ago. The second best time is now.|Proverb",
            "Happiness depends upon ourselves.|Aristotle", "Make each day your masterpiece.|John Wooden",
            "Do what you can, with what you have, where you are.|Theodore Roosevelt"};

    /** What the widgets show besides their settings. */
    static final class Data {
        long now = System.currentTimeMillis();
        String weather;          // "24°  Partly cloudy" or null
        int rise = -1, set = -1; // minutes after midnight, -1 if unknown
        int ram = -1, disk = -1; // percent used
        float temp = Float.NaN;  // the TV's own temperature in °C
        int accent;              // the wallpaper's colour (0 = none)
    }

    private final JSONObject cfg;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final RectF box = new RectF();
    private float u;          // the widget unit (5 % of the screen height, scaled)
    private String style;
    private int fg, fg2, acc, cardFill, softBase;
    private boolean darkText;

    Board(JSONObject cfg) {
        this.cfg = cfg == null ? new JSONObject() : cfg;
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
    }

    static List<JSONObject> list(JSONObject cfg) {
        List<JSONObject> out = new ArrayList<>();
        JSONArray a = cfg.optJSONArray("widgets");
        if (a == null) return out;
        for (int i = 0; i < a.length(); i++) {
            JSONObject w = a.optJSONObject(i);
            if (w != null && !w.optString("type").isEmpty()) out.add(w);
        }
        return out;
    }

    static String name(String type) {
        for (int i = 0; i < TYPES.length; i++) if (TYPES[i].equals(type)) return NAMES[i];
        return type;
    }

    static String defaultPos(String type) {
        switch (type) {
            case "glance": case "clock": case "greeting": return "tr";
            case "calendar": case "weather": case "sun": return "mr";
            default: return "br";
        }
    }

    /** Whether anything shown changes every second (then the TV redraws each second, else each minute). */
    static boolean everySecond(JSONObject cfg) {
        for (JSONObject w : list(cfg)) if ("clock".equals(w.optString("type")) && w.optBoolean("seconds", false)) return true;
        return false;
    }

    // ------------------------------------------------------------------ colours

    static int mix(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        return Color.argb(Math.round(Color.alpha(a) + (Color.alpha(b) - Color.alpha(a)) * t),
                Math.round(Color.red(a) + (Color.red(b) - Color.red(a)) * t),
                Math.round(Color.green(a) + (Color.green(b) - Color.green(a)) * t),
                Math.round(Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t));
    }

    static int alpha(int c, int a) {
        return (Math.max(0, Math.min(255, a)) << 24) | (c & 0xFFFFFF);
    }

    /** Material tones from one colour: [surface, container, on-container, primary-container, on-primary]. */
    static int[] tones(int accent) {
        int a = accent == 0 ? 0xFF7C8CFF : accent | 0xFF000000;
        return new int[]{mix(a, Color.BLACK, 0.82f), mix(a, Color.BLACK, 0.66f), mix(a, Color.WHITE, 0.82f),
                mix(a, Color.WHITE, 0.62f), mix(a, Color.BLACK, 0.8f)};
    }

    static int parse(String s, int def) {
        try {
            return s == null || s.isEmpty() ? def : Color.parseColor(s.trim());
        } catch (RuntimeException e) {
            return def;
        }
    }

    /** The accent in force: a fixed one from the settings, else the wallpaper's, else a default. */
    static int accent(JSONObject cfg, int wallAccent) {
        String fixed = cfg.optString("accent", "auto");
        int a = "auto".equals(fixed) ? wallAccent : parse(fixed, wallAccent);
        return a != 0 ? a : 0xFF39E6FF;
    }

    String styleNow() {
        String s = cfg.optString("widget_style", "auto");
        if (!"auto".equals(s)) return s;
        switch (cfg.optString("theme", "glass")) {
            case "soft": return "soft";
            case "dot": return "dot";
            case "material": return "tonal";
            case "limitless":
            case "minimalist": return "bare";
            case "daynight": return "day".equals(cfg.optString("period")) ? "soft" : "glass";
            default: return "glass";
        }
    }

    private void prepare(float H, Data d) {
        u = H * 0.05f * (float) cfg.optDouble("widget_size", 100) / 100f;
        style = styleNow();
        acc = accent(cfg, d.accent);
        boolean inkDark = "dark".equals(cfg.optString("ink", "light"));
        softBase = "solid".equals(cfg.optString("bg")) || "gradient".equals(cfg.optString("bg"))
                ? parse(cfg.optString("bg_color"), 0xFFE3E8EF) : 0xFFE3E8EF;
        if (Color.red(softBase) + Color.green(softBase) + Color.blue(softBase) < 300) softBase = 0xFFE3E8EF;
        switch (style) {
            case "soft":
                darkText = true;
                fg = 0xFF2B3240;
                fg2 = 0x992B3240;
                cardFill = softBase;
                break;
            case "tonal": {
                int[] t = tones(acc);
                darkText = false;
                fg = t[2];
                fg2 = alpha(t[2], 0xB3);
                cardFill = alpha(t[1], 0xEB);
                break;
            }
            case "dot":
                darkText = false;
                fg = Color.WHITE;
                fg2 = 0xA6FFFFFF;
                cardFill = 0xE6050505;
                acc = "auto".equals(cfg.optString("accent", "auto")) ? 0xFFD71921 : acc;
                break;
            case "bare":
                darkText = inkDark;
                fg = inkDark ? 0xFF1F2430 : Color.WHITE;
                fg2 = inkDark ? 0x991F2430 : 0xBFFFFFFF;
                cardFill = 0;
                break;
            default: // glass
                darkText = false;
                fg = Color.WHITE;
                fg2 = 0xB3FFFFFF;
                cardFill = 0x73101218;
                break;
        }
    }

    // ------------------------------------------------------------------ placing

    /** Draws every widget in its corner; W and H are the screen's size. */
    void draw(Canvas c, float W, float H, Data d) {
        List<JSONObject> all = list(cfg);
        if (all.isEmpty()) return;
        prepare(H, d);
        boolean row = "row".equals(cfg.optString("dock_style", "icons"));
        boolean dockRight = !row && "right".equals(cfg.optString("dock_side", "left"));
        float edge = H * (float) cfg.optDouble("edge", 4.5) / 100f;
        String ds = cfg.optString("dock_style", "icons");
        float dockW = "panel".equals(ds) ? W * 0.19f + edge : "list".equals(ds) ? W * 0.36f
                : edge + H * (float) cfg.optDouble("icon_size", 10.5) / 100f * ("banner".equals(cfg.optString("icon_style")) ? 1.78f : 1f) + W * 0.12f;
        float leftX = row ? W * 0.06f + H * 0.022f : !dockRight ? dockW + H * 0.05f : edge + H * 0.02f;
        float rightX = row ? W - W * 0.06f - H * 0.022f : dockRight ? W - dockW - H * 0.05f : W - edge - H * 0.02f;
        float clockBottom = H * 0.06f + (cfg.optBoolean("greeting", true) ? H * 0.028f : 0)
                + H * 0.105f * (float) cfg.optDouble("clock_scale", 100) / 100f + H * 0.07f;
        float bandTop = H * (float) cfg.optDouble("row_top", 64.5) / 100f;
        float topL = row ? clockBottom : edge + H * 0.11f, topR = row ? H * 0.21f : edge + H * 0.11f;
        float botL = row ? bandTop - (cfg.optBoolean("row_title", true) ? H * 0.16f : H * 0.045f) : H - edge;
        float botR = row ? bandTop - H * 0.045f : H - edge;
        float gap = u * 0.38f;
        float floorL = 0, floorR = 0; // bottom of what is already placed on each side

        for (String pos : POS) {
            List<JSONObject> here = new ArrayList<>();
            for (JSONObject w : all) if (pos.equals(w.optString("pos", defaultPos(w.optString("type"))))) here.add(w);
            if (here.isEmpty()) continue;
            List<float[]> sizes = new ArrayList<>();
            float total = 0;
            for (JSONObject w : here) {
                float[] s = one(null, w, 0, 0, d);
                sizes.add(s);
                total += s[1];
            }
            total += gap * (here.size() - 1);
            boolean left = pos.endsWith("l"), centre = "bc".equals(pos);
            float top = left ? topL : topR, bottom = left ? botL : botR;
            if (centre) {
                top = Math.max(topL, topR);
                bottom = Math.min(botL, botR);
            }
            float floor = centre ? Math.max(floorL, floorR) : left ? floorL : floorR;
            float y;
            if (pos.startsWith("t")) y = top;
            else if (pos.startsWith("m")) y = Math.max(top, (top + bottom) / 2f - total / 2f);
            else y = bottom - total;
            if (floor > 0) y = Math.max(y, floor + gap);
            // too tall for the space left: shrink this corner's widgets a little rather than cover the apps
            float k = 1f;
            if (y + total > bottom && bottom - y > 0) k = Math.max(0.6f, (bottom - y) / total);
            float end = y + total * k;
            if (!centre) {
                if (left) floorL = end;
                else floorR = end;
            }
            for (int i = 0; i < here.size(); i++) {
                float[] s = sizes.get(i);
                float x = centre ? (W - s[0] * k) / 2f : left ? leftX : rightX - s[0] * k;
                if (k < 1f) {
                    c.save();
                    c.translate(x, y);
                    c.scale(k, k);
                    one(c, here.get(i), 0, 0, d);
                    c.restore();
                } else {
                    one(c, here.get(i), x, y, d);
                }
                y += (s[1] + gap) * k;
            }
        }
    }

    /** One widget alone, for the phone's samples: centred in w x h, unit taken from the height H. */
    static void sample(Canvas c, JSONObject cfg, JSONObject w, float cw, float ch, float H, Data d) {
        Board b = new Board(cfg);
        b.prepare(H, d);
        float[] s = b.one(null, w, 0, 0, d);
        float k = Math.min(1f, Math.min(cw * 0.92f / s[0], ch * 0.88f / s[1]));
        c.save();
        c.translate((cw - s[0] * k) / 2f, (ch - s[1] * k) / 2f);
        c.scale(k, k);
        b.one(c, w, 0, 0, d);
        c.restore();
    }

    // ------------------------------------------------------------------ the card behind a widget

    private void card(Canvas c, float x, float y, float w, float h) {
        if (c == null) return;
        float r = "tonal".equals(style) ? u * 0.95f : "dot".equals(style) ? u * 0.45f : "soft".equals(style) ? u * 0.75f : u * 0.6f;
        box.set(x, y, x + w, y + h);
        fill.setShader(null);
        fill.setMaskFilter(null);
        switch (style) {
            case "bare":
                return;
            case "soft": {
                // a soft shadow below right and a light one above left, built from a few faint layers
                int dark = mix(softBase, Color.BLACK, 0.22f);
                for (int i = 4; i >= 1; i--) {
                    float s = u * 0.07f * i;
                    fill.setColor(alpha(dark, 0x12));
                    c.drawRoundRect(x + s * 0.6f, y + s * 0.6f, x + w + s, y + h + s, r + s, r + s, fill);
                    fill.setColor(alpha(Color.WHITE, 0x26));
                    c.drawRoundRect(x - s, y - s, x + w - s * 0.6f, y + h - s * 0.6f, r + s, r + s, fill);
                }
                fill.setShader(new LinearGradient(x, y, x + w, y + h, mix(softBase, Color.WHITE, 0.35f),
                        mix(softBase, Color.BLACK, 0.04f), Shader.TileMode.CLAMP));
                c.drawRoundRect(box, r, r, fill);
                fill.setShader(null);
                return;
            }
            default:
                fill.setColor(cardFill);
                c.drawRoundRect(box, r, r, fill);
                if ("glass".equals(style) || "dot".equals(style)) {
                    line.setShader(null);
                    line.setStrokeWidth(Math.max(1f, u * 0.03f));
                    line.setColor("dot".equals(style) ? 0x2EFFFFFF : 0x40FFFFFF);
                    c.drawRoundRect(box, r, r, line);
                }
        }
    }

    private void font(float size, String f, int color, float spacing) {
        text.setTextSize(size);
        text.setTypeface(Faces.font(f));
        text.setColor(color);
        text.setLetterSpacing(spacing);
        text.setTextAlign(Paint.Align.LEFT);
        text.setShader(null);
        if ("bare".equals(style) && !darkText) text.setShadowLayer(size * 0.15f, 0, size * 0.04f, 0x99000000);
        else text.clearShadowLayer();
    }

    /** Big numbers: dots in the Dot matrix style, else the clock's own font. Returns the width. */
    private float big(Canvas c, String s, float x, float top, float size, int color, String f) {
        if ("dot".equals(style)) {
            float cell = size * 0.72f / 7f;
            if (c != null) Faces.dots(c, s, x, top + size * 0.04f, cell, color, 0x14);
            return Faces.dotsWidth(s, cell);
        }
        font(size, f, color, -0.02f);
        Paint.FontMetrics fm = text.getFontMetrics();
        float cap = -fm.ascent * 0.74f;
        if (c != null) c.drawText(s, x, top + cap, text);
        return text.measureText(s);
    }

    private float bigH(float size) {
        return "dot".equals(style) ? size * 0.76f : size * 0.76f;
    }

    private List<String> wrap(String s, float width) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String word : s.trim().split("\\s+")) {
            String next = cur.length() == 0 ? word : cur + " " + word;
            if (text.measureText(next) > width && cur.length() > 0) {
                out.add(cur.toString());
                cur = new StringBuilder(word);
            } else {
                cur = new StringBuilder(next);
            }
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }

    private static String fmt(String p, long now) {
        return new SimpleDateFormat(p, Locale.getDefault()).format(new Date(now));
    }

    private String clock(long now, TimeZone tz) {
        SimpleDateFormat f = new SimpleDateFormat("12".equals(cfg.optString("clock", "24")) ? "h:mm" : "HH:mm",
                Locale.getDefault());
        if (tz != null) f.setTimeZone(tz);
        return f.format(new Date(now));
    }

    // ------------------------------------------------------------------ the widgets

    /** Measures (c == null) or draws one widget with its top-left at (x, y); returns {width, height}. */
    float[] one(Canvas c, JSONObject w, float x, float y, Data d) {
        float p = u * 0.6f;
        String type = w.optString("type");
        float[] in;
        switch (type) {
            case "clock": in = clockW(null, w, 0, 0, d); break;
            case "glance": in = glance(null, 0, 0, d); break;
            case "calendar": in = calendar(null, 0, 0, d); break;
            case "weather": in = weather(null, 0, 0, d); break;
            case "sun": in = sun(null, 0, 0, d); break;
            case "system": in = system(null, 0, 0, d); break;
            case "world": in = world(null, w, 0, 0, d); break;
            case "countdown": in = countdown(null, w, 0, 0, d); break;
            case "note": in = note(null, w, 0, 0); break;
            case "quote": in = quote(null, 0, 0, d); break;
            case "greeting": in = greeting(null, w, 0, 0, d); break;
            default: return new float[]{0, 0};
        }
        float bare = "bare".equals(style) ? 0f : 1f;
        float cw = in[0] + 2 * p * bare, ch = in[1] + 2 * p * bare;
        if (c != null) {
            card(c, x, y, cw, ch);
            float ix = x + p * bare, iy = y + p * bare;
            switch (type) {
                case "clock": clockW(c, w, ix, iy, d); break;
                case "glance": glance(c, ix, iy, d); break;
                case "calendar": calendar(c, ix, iy, d); break;
                case "weather": weather(c, ix, iy, d); break;
                case "sun": sun(c, ix, iy, d); break;
                case "system": system(c, ix, iy, d); break;
                case "world": world(c, w, ix, iy, d); break;
                case "countdown": countdown(c, w, ix, iy, d); break;
                case "note": note(c, w, ix, iy); break;
                case "quote": quote(c, ix, iy, d); break;
                default: greeting(c, w, ix, iy, d); break;
            }
        }
        return new float[]{cw, ch};
    }

    private float[] clockW(Canvas c, JSONObject w, float x, float y, Data d) {
        String tm = clock(d.now, null);
        float size = u * 2.4f;
        String f = cfg.optString("clock_font", "light");
        float tw = big(c, tm, x, y, size, fg, f);
        float h = bigH(size);
        if (w.optBoolean("seconds", false)) {
            String s = fmt("ss", d.now);
            float sw = big(c, s, x + tw + u * 0.2f, y + h - bigH(size * 0.36f), size * 0.36f, fg2, f);
            tw += u * 0.2f + sw;
        }
        float dy = y + h + u * 0.35f;
        font(u * 0.5f, "medium", fg2, 0.14f);
        String date = fmt("EEEE  dd MMMM", d.now).toUpperCase(Locale.getDefault());
        if (c != null) c.drawText(date, x, dy + u * 0.4f, text);
        return new float[]{Math.max(tw, text.measureText(date)), h + u * 0.35f + u * 0.5f};
    }

    private float[] glance(Canvas c, float x, float y, Data d) {
        font(u * 0.95f, "regular", fg, -0.01f);
        String date = fmt("EEEE, d MMMM", d.now);
        float w1 = text.measureText(date);
        if (c != null) c.drawText(date, x, y + u * 0.78f, text);
        String wx = d.weather == null ? "Weather appears once the TV knows where it is" : d.weather.replace("  ", "  ·  ");
        font(u * 0.62f, "medium", fg2, 0.01f);
        float gs = u * 0.62f;
        float w2 = gs * 1.8f + text.measureText(wx);
        if (c != null) {
            sky(c, x + gs * 0.55f, y + u * 1.62f - gs * 0.32f, gs * 0.55f, d);
            c.drawText(wx, x + gs * 1.8f, y + u * 1.62f, text);
        }
        return new float[]{Math.max(w1, w2), u * 1.8f};
    }

    /** A small sun or moon (and a cloud when the words say so), drawn with lines. */
    private void sky(Canvas c, float cx, float cy, float r, Data d) {
        int m = minutes(d.now);
        boolean day = d.rise < 0 ? m >= 360 && m < 1080 : m >= d.rise && m < d.set;
        fill.setShader(null);
        if (day) {
            fill.setColor(0xFFFFC53D);
            c.drawCircle(cx, cy, r * 0.62f, fill);
            line.setColor(0xFFFFC53D);
            line.setStrokeWidth(Math.max(1f, r * 0.16f));
            for (int i = 0; i < 8; i++) {
                double a = Math.PI / 4 * i;
                c.drawLine(cx + (float) Math.cos(a) * r * 0.9f, cy + (float) Math.sin(a) * r * 0.9f,
                        cx + (float) Math.cos(a) * r * 1.2f, cy + (float) Math.sin(a) * r * 1.2f, line);
            }
        } else {
            fill.setColor(0xFFE6E9F5);
            c.drawCircle(cx, cy, r * 0.8f, fill);
            fill.setColor(cardFill != 0 && !"soft".equals(style) ? cardFill | 0xFF000000 : darkText ? softBase : 0xFF0B0D12);
            c.drawCircle(cx + r * 0.38f, cy - r * 0.3f, r * 0.68f, fill);
        }
        String w = d.weather == null ? "" : d.weather.toLowerCase(Locale.ROOT);
        if (w.contains("cloud") || w.contains("overcast") || w.contains("rain") || w.contains("fog") || w.contains("drizzle")) {
            fill.setColor(darkText ? 0xFF8A94A6 : 0xF2FFFFFF);
            c.drawCircle(cx + r * 0.2f, cy + r * 0.45f, r * 0.5f, fill);
            c.drawCircle(cx + r * 0.75f, cy + r * 0.35f, r * 0.62f, fill);
            c.drawRoundRect(cx - r * 0.25f, cy + r * 0.45f, cx + r * 1.4f, cy + r * 0.98f, r * 0.27f, r * 0.27f, fill);
        }
    }

    private static int minutes(long now) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(now);
        return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);
    }

    private float[] calendar(Canvas c, float x, float y, Data d) {
        float cell = u * 0.64f;
        float w = cell * 7;
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(d.now);
        int today = cal.get(Calendar.DAY_OF_MONTH);
        String month = fmt("MMMM yyyy", d.now);
        font(u * 0.62f, "medium", fg, 0.02f);
        if (c != null) c.drawText(month, x + cell * 0.12f, y + u * 0.55f, text);
        float top = y + u * 0.95f;
        String[] days = {"M", "T", "W", "T", "F", "S", "S"};
        font(u * 0.4f, "medium", fg2, 0.05f);
        text.setTextAlign(Paint.Align.CENTER);
        for (int i = 0; i < 7 && c != null; i++) c.drawText(days[i], x + cell * (i + 0.5f), top + u * 0.38f, text);
        top += u * 0.62f;
        cal.set(Calendar.DAY_OF_MONTH, 1);
        int first = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7; // Monday = 0
        int count = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
        int rows = (first + count + 6) / 7;
        if (c != null) {
            for (int dnum = 1; dnum <= count; dnum++) {
                int idx = first + dnum - 1;
                float cx = x + cell * (idx % 7 + 0.5f), cy = top + cell * (idx / 7 + 0.5f);
                boolean now = dnum == today;
                if (now) {
                    fill.setShader(null);
                    fill.setColor("tonal".equals(style) ? tones(acc)[3] : acc);
                    c.drawCircle(cx, cy, cell * 0.44f, fill);
                }
                font(u * 0.42f, now ? "bold" : "regular", now ? ("tonal".equals(style) ? tones(acc)[4] : Color.WHITE)
                        : idx % 7 >= 5 ? fg2 : fg, 0f);
                text.setTextAlign(Paint.Align.CENTER);
                c.drawText(String.valueOf(dnum), cx, cy + u * 0.15f, text);
            }
        }
        text.setTextAlign(Paint.Align.LEFT);
        return new float[]{w, (top - y) + rows * cell};
    }

    private float[] weather(Canvas c, float x, float y, Data d) {
        String temp = "--°", words = "Waiting for the weather";
        if (d.weather != null) {
            String[] p = d.weather.split("\\s{2,}", 2);
            temp = p[0].trim();
            words = p.length > 1 ? p[1].trim() : "";
        }
        float gs = u * 0.95f;
        if (c != null) sky(c, x + gs * 0.6f, y + gs * 0.75f, gs * 0.6f, d);
        float tx = x + gs * 1.75f;
        float tw = big(c, temp, tx, y, u * 2.0f, fg, "light");
        float h = bigH(u * 2.0f);
        font(u * 0.55f, "medium", fg2, 0.03f);
        float ww = text.measureText(words);
        if (c != null) c.drawText(words, x, y + h + u * 0.75f, text);
        float lineH = h + u * 0.85f;
        float w = Math.max(gs * 1.75f + tw, ww);
        if (d.rise >= 0) {
            String s = String.format(Locale.ROOT, "↑ %02d:%02d    ↓ %02d:%02d", d.rise / 60, d.rise % 60, d.set / 60, d.set % 60);
            font(u * 0.45f, "medium", fg2, 0.04f);
            if (c != null) c.drawText(s, x, y + lineH + u * 0.55f, text);
            w = Math.max(w, text.measureText(s));
            lineH += u * 0.7f;
        }
        return new float[]{w, lineH};
    }

    private float[] sun(Canvas c, float x, float y, Data d) {
        int rise = d.rise >= 0 ? d.rise : 360, set = d.set >= 0 ? d.set : 1080;
        float w = u * 6.2f, r = u * 2.5f;
        float cx = x + w / 2f, base = y + r + u * 0.35f;
        int m = minutes(d.now);
        float k = (m - rise) / (float) Math.max(1, set - rise);
        boolean up = k >= 0f && k <= 1f;
        if (c != null) {
            line.setShader(null);
            line.setStrokeWidth(Math.max(1f, u * 0.05f));
            line.setColor(alpha(fg, 0x40));
            box.set(cx - r, base - r, cx + r, base + r);
            c.drawArc(box, 180, 180, false, line);
            line.setColor(alpha(fg, 0x33));
            c.drawLine(x, base, x + w, base, line);
            if (up) {
                line.setStrokeWidth(Math.max(1.5f, u * 0.09f));
                line.setColor("soft".equals(style) ? 0xFFF08A3C : 0xFFFFC53D);
                c.drawArc(box, 180, 180 * k, false, line);
                double a = Math.PI * (1 - k);
                float sx = cx + (float) Math.cos(a) * r, sy = base - (float) Math.sin(a) * r;
                fill.setShader(null);
                fill.setColor(alpha(0xFFFFC53D, 0x55));
                c.drawCircle(sx, sy, u * 0.42f, fill);
                fill.setColor(0xFFFFC53D);
                c.drawCircle(sx, sy, u * 0.24f, fill);
            } else {
                fill.setShader(null);
                fill.setColor(0xFFE6E9F5);
                c.drawCircle(cx, base - r * 0.5f, u * 0.3f, fill);
            }
            font(u * 0.46f, "medium", fg2, 0.04f);
            c.drawText(String.format(Locale.ROOT, "%02d:%02d", rise / 60, rise % 60), x, base + u * 0.75f, text);
            text.setTextAlign(Paint.Align.RIGHT);
            c.drawText(String.format(Locale.ROOT, "%02d:%02d", set / 60, set % 60), x + w, base + u * 0.75f, text);
            font(u * 0.46f, "medium", fg, 0.08f);
            text.setTextAlign(Paint.Align.CENTER);
            int left = up ? set - m : (m < rise ? rise - m : 1440 - m + rise);
            String what = (up ? "SUNSET IN " : "SUNRISE IN ") + left / 60 + "H " + left % 60 + "M";
            c.drawText(what, cx, base + u * 1.45f, text);
            text.setTextAlign(Paint.Align.LEFT);
        }
        return new float[]{w, r + u * 0.35f + u * 1.6f};
    }

    private float[] system(Canvas c, float x, float y, Data d) {
        float w = u * 5.6f;
        String[] names = {"RAM", "Storage", "Temperature"};
        float[] vals = {d.ram, d.disk, Float.isNaN(d.temp) ? -1 : d.temp};
        float yy = y;
        for (int i = 0; i < 3; i++) {
            if (vals[i] < 0) continue;
            boolean temp = i == 2;
            font(u * 0.46f, "medium", fg2, 0.1f);
            if (c != null) c.drawText(names[i].toUpperCase(Locale.ROOT), x, yy + u * 0.42f, text);
            String v = temp ? Math.round(vals[i]) + "°C" : Math.round(vals[i]) + "%";
            if ("dot".equals(style)) {
                float cell = u * 0.075f;
                if (c != null) Faces.dots(c, v, x + w - Faces.dotsWidth(v, cell), yy, cell, fg, 0);
            } else {
                font(u * 0.5f, "bold", fg, 0f);
                text.setTextAlign(Paint.Align.RIGHT);
                if (c != null) c.drawText(v, x + w, yy + u * 0.45f, text);
                text.setTextAlign(Paint.Align.LEFT);
            }
            float by = yy + u * 0.72f;
            float k = temp ? Math.min(1f, Math.max(0f, (vals[i] - 20f) / 60f)) : vals[i] / 100f;
            if (c != null) {
                if ("dot".equals(style)) {
                    int n = 20;
                    float step = w / n;
                    for (int j = 0; j < n; j++) {
                        fill.setShader(null);
                        fill.setColor(j < Math.round(k * n) ? (j == Math.round(k * n) - 1 ? acc : fg) : alpha(fg, 0x26));
                        c.drawCircle(x + step * (j + 0.5f), by + u * 0.1f, u * 0.08f, fill);
                    }
                } else {
                    float bh = u * 0.18f;
                    fill.setShader(null);
                    fill.setColor(alpha(fg, 0x26));
                    c.drawRoundRect(x, by, x + w, by + bh, bh / 2, bh / 2, fill);
                    int barCol = temp && vals[i] > 60 ? 0xFFFF5A5A : "tonal".equals(style) ? tones(acc)[3] : acc;
                    fill.setColor(barCol);
                    c.drawRoundRect(x, by, x + Math.max(bh, w * k), by + bh, bh / 2, bh / 2, fill);
                }
            }
            yy += u * 1.25f;
        }
        if (yy == y) {
            font(u * 0.46f, "medium", fg2, 0.02f);
            if (c != null) c.drawText("Reading the TV...", x, y + u * 0.5f, text);
            yy += u * 0.7f;
        }
        return new float[]{w, yy - y - u * 0.25f};
    }

    private float[] world(Canvas c, JSONObject w, float x, float y, Data d) {
        String tzId = w.optString("tz", "Europe/London");
        String city = w.optString("city", "");
        if (city.isEmpty()) {
            for (String[] ct : CITIES) if (ct[1].equals(tzId)) city = ct[0];
            if (city.isEmpty()) city = tzId.substring(tzId.lastIndexOf('/') + 1).replace('_', ' ');
        }
        TimeZone tz = TimeZone.getTimeZone(tzId);
        font(u * 0.5f, "medium", fg2, 0.14f);
        String cityUp = city.toUpperCase(Locale.getDefault());
        float cw = text.measureText(cityUp);
        if (c != null) c.drawText(cityUp, x, y + u * 0.42f, text);
        float tw = big(c, clock(d.now, tz), x, y + u * 0.75f, u * 1.8f, fg, cfg.optString("clock_font", "light"));
        int diff = (tz.getOffset(d.now) - TimeZone.getDefault().getOffset(d.now)) / 60000;
        String rel = diff == 0 ? "Same time" : (diff > 0 ? "+" : "−") + Math.abs(diff) / 60 + "h" + (Math.abs(diff) % 60 > 0 ? " " + Math.abs(diff) % 60 + "m" : "");
        Calendar here = Calendar.getInstance(), there = Calendar.getInstance(tz);
        here.setTimeInMillis(d.now);
        there.setTimeInMillis(d.now);
        int dd = there.get(Calendar.DAY_OF_YEAR) - here.get(Calendar.DAY_OF_YEAR);
        if (dd != 0) rel += dd > 0 ? " · Tomorrow" : " · Yesterday";
        font(u * 0.44f, "medium", fg2, 0.03f);
        float hh = u * 0.75f + bigH(u * 1.8f) + u * 0.25f;
        if (c != null) c.drawText(rel, x, y + hh + u * 0.38f, text);
        return new float[]{Math.max(Math.max(cw, tw), text.measureText(rel)), hh + u * 0.48f};
    }

    private float[] countdown(Canvas c, JSONObject w, float x, float y, Data d) {
        String title = w.optString("title", "New Year");
        long days = 0;
        try {
            String ds = w.optString("date", "");
            Date target;
            if (ds.isEmpty()) {
                Calendar nx = Calendar.getInstance();
                nx.setTimeInMillis(d.now);
                nx.set(nx.get(Calendar.YEAR) + 1, Calendar.JANUARY, 1, 0, 0, 0);
                target = nx.getTime();
            } else {
                target = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).parse(ds);
            }
            Calendar t0 = Calendar.getInstance();
            t0.setTimeInMillis(d.now);
            t0.set(Calendar.HOUR_OF_DAY, 0);
            t0.set(Calendar.MINUTE, 0);
            t0.set(Calendar.SECOND, 0);
            t0.set(Calendar.MILLISECOND, 0);
            days = Math.round((target.getTime() - t0.getTimeInMillis()) / 86_400_000.0);
        } catch (Exception ignored) { }
        font(u * 0.5f, "medium", fg2, 0.14f);
        String head = title.toUpperCase(Locale.getDefault());
        float hw = text.measureText(head);
        if (c != null) c.drawText(head, x, y + u * 0.42f, text);
        String num = days < 0 ? "0" : String.valueOf(days);
        float nw = big(c, num, x, y + u * 0.75f, u * 2.2f, "tonal".equals(style) ? tones(acc)[3] : fg, "light");
        font(u * 0.62f, "regular", fg2, 0.02f);
        String unit = days == 0 ? "Today!" : days == 1 ? "day to go" : days < 0 ? "Passed" : "days to go";
        float uw = text.measureText(unit);
        float bh = bigH(u * 2.2f);
        if (c != null) c.drawText(unit, x + nw + u * 0.3f, y + u * 0.75f + bh, text);
        return new float[]{Math.max(hw, nw + u * 0.3f + uw), u * 0.75f + bh + u * 0.15f};
    }

    private float[] note(Canvas c, JSONObject w, float x, float y) {
        String s = w.optString("text", "Movie night on Friday");
        if (s.trim().isEmpty()) s = "Your note";
        font(u * 0.66f, "medium", fg, 0f);
        List<String> lines = wrap(s, u * 7.5f);
        if (lines.size() > 4) lines = lines.subList(0, 4);
        float wmax = 0;
        for (String l : lines) wmax = Math.max(wmax, text.measureText(l));
        float lh = u * 0.86f;
        if (c != null) {
            fill.setShader(null);
            fill.setColor("tonal".equals(style) ? tones(acc)[3] : acc);
            c.drawRoundRect(x, y + u * 0.08f, x + u * 0.12f, y + lines.size() * lh - u * 0.18f, u * 0.06f, u * 0.06f, fill);
            for (int i = 0; i < lines.size(); i++) c.drawText(lines.get(i), x + u * 0.45f, y + u * 0.6f + i * lh, text);
        }
        return new float[]{wmax + u * 0.45f, lines.size() * lh - u * 0.12f};
    }

    private float[] quote(Canvas c, float x, float y, Data d) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(d.now);
        String[] q = QUOTES[(cal.get(Calendar.DAY_OF_YEAR) + cal.get(Calendar.YEAR)) % QUOTES.length].split("\\|");
        font(u * 0.66f, "light", fg, 0f);
        text.setTypeface(Typeface.create(Faces.font("light"), Typeface.ITALIC));
        List<String> lines = wrap("“" + q[0] + "”", u * 8.5f);
        float wmax = 0;
        for (String l : lines) wmax = Math.max(wmax, text.measureText(l));
        float lh = u * 0.88f;
        if (c != null) for (int i = 0; i < lines.size(); i++) c.drawText(lines.get(i), x, y + u * 0.6f + i * lh, text);
        font(u * 0.46f, "medium", fg2, 0.12f);
        String by = "— " + q[1].toUpperCase(Locale.getDefault());
        if (c != null) c.drawText(by, x, y + lines.size() * lh + u * 0.5f, text);
        return new float[]{Math.max(wmax, text.measureText(by)), lines.size() * lh + u * 0.62f};
    }

    private float[] greeting(Canvas c, JSONObject w, float x, float y, Data d) {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(d.now);
        h = cal.get(Calendar.HOUR_OF_DAY);
        String g = h < 5 ? "Good night" : h < 12 ? "Good morning" : h < 17 ? "Good afternoon" : h < 22 ? "Good evening" : "Good night";
        String name = w.optString("name", "").trim();
        float gw;
        float gh;
        if ("dot".equals(style)) {
            float cell = u * 0.16f;
            gw = Faces.dotsWidth(g, cell);
            if (c != null) Faces.dots(c, g, x, y, cell, fg, 0);
            gh = cell * 7;
        } else {
            font(u * 1.15f, "light", fg, -0.01f);
            gw = text.measureText(g);
            if (c != null) c.drawText(g, x, y + u * 0.9f, text);
            gh = u * 1.1f;
        }
        float nw = 0;
        if (!name.isEmpty()) {
            font(u * 0.72f, "medium", "tonal".equals(style) ? tones(acc)[3] : acc, 0.01f);
            nw = text.measureText(name);
            if (c != null) c.drawText(name, x, y + gh + u * 0.78f, text);
            gh += u * 0.95f;
        }
        return new float[]{Math.max(gw, nw), gh};
    }
}
