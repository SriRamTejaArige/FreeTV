package com.sriramteja.glasshome;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.StatFs;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Glass Home: the wallpaper full screen, the apps as a column of rounded banners in a frosted panel on the
 * left, and a frosted pill top right with the time and six round buttons (Wi-Fi, Bluetooth, profile, memory,
 * music, settings). Hold OK (or Menu) on an app to move or hide it.
 */
public class HomeActivity extends Activity {

    private static final int FOCUS_RING = 0xE6FFFFFF;
    private static final String[] NEVER_LISTED = {
            "com.sriramteja.glasshome", "com.android.tv.settings", "com.google.android.tvlauncher",
            "com.gathin.tvhome", "com.mitv.tvhome.atv", "com.google.android.apps.tv.launcherx",
    };

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService bg = Executors.newSingleThreadExecutor();
    private SharedPreferences prefs;
    private int W, H;

    private FrameLayout root;
    private ImageView wallA, wallB;
    private boolean showingA = true;
    private String wallName = "";
    private Glass.Frame dock, pill;
    private ScrollView dockScroll;
    private LinearLayout dockList, pillRow;
    private final List<View> tiles = new ArrayList<>();
    private final List<View> buttons = new ArrayList<>();
    private View lastTile, lastButton;
    private View overlay;
    private View overlayReturn;

    private int tileW, tileH, gap, pad;
    private boolean bare;

    private JSONObject cfg = new JSONObject();
    private String iconStyle = "original";
    private boolean right, glowOn, barePill;
    private int accentNow;
    private final List<ImageView> tinted = new ArrayList<>();
    private final List<TextView> accentText = new ArrayList<>();
    private Noir.Seconds secondsView;
    private Noir.Watcher watcher;
    /** New looks: dark text (light themes), a list of names instead of icons, tile styles, widgets. */
    private boolean darkInk, list, usesWall = true;
    private String tileStyle = "glass";
    private BoardView board;
    private final List<DotViews.Clock> dotClocks = new ArrayList<>();
    private DotViews.Text dotTitle;
    /** Things coloured from the accent that are redone when it changes (tiles: cleared with the dock). */
    private final List<Runnable> recolor = new ArrayList<>(), tileRecolor = new ArrayList<>();
    private View spot;
    private static final Map<String, Bitmap[]> LOGOS = new HashMap<>();

    private static boolean bareStyle() {
        return !"panel".equals(Store.config().optString("dock_style", "icons"));
    }

    static Typeface font(String f) {
        return Noir.font(f);
    }

    /** The text colour of the theme (white, or dark on light themes) with the given alpha. */
    private int ink(int alpha) {
        return ((alpha & 0xFF) << 24) | (darkInk ? 0x1F2430 : 0xFFFFFF);
    }

    /** A setting given in percent, as a fraction. */
    private float pct(String key, float def) {
        return (float) cfg.optDouble(key, def) / 100f;
    }

    static int parseColor(String s, int def) {
        try {
            return s == null || s.isEmpty() ? def : Color.parseColor(s.trim());
        } catch (IllegalArgumentException e) {
            return def;
        }
    }

    private TextView weatherView;
    /** The Clean theme: apps in one horizontal row of banners, the big name of the selected app above it. */
    private boolean row;
    private HorizontalScrollView rowScroll;
    private TextView appTitle, memoryPill;
    private int iconW;
    private final Set<View> floating = new HashSet<>();
    private final Map<View, View> halos = new HashMap<>();

    private final Runnable weatherTick = new Runnable() {
        @Override
        public void run() {
            if (overlay == null && !Store.config().toString().equals(cfg.toString())) {
                // new settings, or Day & Night turned over: rebuild with them
                recreate();
                return;
            }
            Weather.refresh(HomeActivity.this);
            showWeather();
            if (board != null) board.refresh();
            main.postDelayed(this, 60_000L);
        }
    };

    private void showWeather() {
        if (memoryPill != null) memoryPill.setText(memoryShort());
        if (weatherView == null) return;
        String w = Weather.line(this);
        weatherView.setText(w == null ? "" : w);
        weatherView.setVisibility(w == null ? View.GONE : View.VISIBLE);
    }

    /**
     * Apps in "auto_close" are closed a few seconds after Home shows (they stop using RAM in the
     * background): background kill always, a real force-stop through the TV's own adb when allowed.
     */
    private final Runnable closer = new Runnable() {
        @Override
        public void run() {
            List<String> pkgs = Store.strings(Store.config(), "auto_close");
            if (pkgs.isEmpty()) return;
            // the TV's adb was set up for Glass Home before (RAM manager) when its key exists
            boolean adb = prefs.getBoolean("adb_ok", false) || new File(getFilesDir(), "adbkey.pk8").isFile();
            Thread t = new Thread(() -> {
                ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
                StringBuilder cmd = new StringBuilder();
                for (String p : pkgs) {
                    if (p.equals(getPackageName())) continue;
                    try {
                        if (am != null) am.killBackgroundProcesses(p);
                    } catch (RuntimeException ignored) { }
                    cmd.append("am force-stop ").append(p).append("; ");
                }
                if (adb) {
                    try {
                        LocalAdb.shell(HomeActivity.this, cmd + "true", null);
                    } catch (Exception ignored) { }
                }
            }, "glass-closer");
            t.setDaemon(true);
            t.start();
        }
    };

    /** Settings sent from the phone show within about a second (the file's fingerprint is checked). */
    private String seenStamp = "";
    private final Runnable configWatch = new Runnable() {
        @Override
        public void run() {
            String st = Store.stamp();
            if (!st.equals(seenStamp)) {
                seenStamp = st;
                if (overlay == null && !Store.config().toString().equals(cfg.toString())) {
                    recreate();
                    return;
                }
            }
            main.postDelayed(this, 900);
        }
    };

    /** The apps found last time (icons and banners), kept until an app is installed, removed or updated. */
    private static Map<String, App> appCache;
    private static boolean cacheBanners;
    private static boolean listening;

    private final Runnable rotate = new Runnable() {
        @Override
        public void run() {
            loadWallpaper(false);
        }
    };

    // ------------------------------------------------------------------ lifecycle

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("glass", MODE_PRIVATE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        DisplayMetrics m = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getRealMetrics(m);
        W = Math.max(m.widthPixels, m.heightPixels);
        H = Math.min(m.widthPixels, m.heightPixels);

        root = new FrameLayout(this);
        root.setBackgroundColor(0xFF0B0D12);
        wallA = wallpaperView();
        wallB = wallpaperView();
        wallB.setAlpha(0f);
        root.addView(wallA, new FrameLayout.LayoutParams(-1, -1));
        root.addView(wallB, new FrameLayout.LayoutParams(-1, -1));
        cfg = Store.config();
        row = "row".equals(cfg.optString("dock_style", "icons"));
        iconStyle = cfg.optString("icon_style", "original");
        right = "right".equals(cfg.optString("dock_side", "left"));
        glowOn = cfg.optBoolean("glow", false);
        darkInk = "dark".equals(cfg.optString("ink", "light"));
        list = "list".equals(cfg.optString("dock_style", "icons"));
        tileStyle = cfg.optString("tile_style", "glass");
        String bgMode = cfg.optString("bg", "wallpaper");
        usesWall = "wallpaper".equals(bgMode);
        if (!usesWall) {
            // a plain colour or a soft gradient instead of the pictures
            wallA.setVisibility(View.GONE);
            wallB.setVisibility(View.GONE);
            int c1 = parseColor(cfg.optString("bg_color", "#0B0D12"), 0xFF0B0D12);
            if ("gradient".equals(bgMode)) {
                root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                        new int[]{c1, parseColor(cfg.optString("bg_color2", ""), c1)}));
            } else {
                root.setBackgroundColor(c1);
            }
        }
        float dim = Math.max(0f, Math.min(0.9f, pct("wallpaper_dim", 0)));
        if (dim > 0f) {
            int v = Math.round(255 * (1f - dim));
            int mul = 0xFF000000 | (v << 16) | (v << 8) | v;
            wallA.setColorFilter(mul, PorterDuff.Mode.MULTIPLY);
            wallB.setColorFilter(mul, PorterDuff.Mode.MULTIPLY);
        }
        float shade = Math.max(0f, Math.min(1f, pct("shade", 0)));
        if (shade > 0f) {
            View sh = new View(this);
            int a = Math.round(0xE0 * shade);
            sh.setBackground(new GradientDrawable(right ? GradientDrawable.Orientation.RIGHT_LEFT
                    : GradientDrawable.Orientation.LEFT_RIGHT, new int[]{a << 24, (a / 2) << 24, 0}));
            FrameLayout.LayoutParams shl = new FrameLayout.LayoutParams(Math.round(W * 0.42f), -1);
            shl.gravity = right ? Gravity.END : Gravity.START;
            root.addView(sh, shl);
        }
        float bshade = Math.max(0f, Math.min(1f, pct("bottom_shade", 0)));
        if (bshade > 0f) {
            View bs = new View(this);
            int a = Math.round(0xE6 * bshade);
            bs.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                    new int[]{a << 24, (a / 3) << 24, 0}));
            FrameLayout.LayoutParams bsl = new FrameLayout.LayoutParams(-1, Math.round(H * 0.55f));
            bsl.gravity = Gravity.BOTTOM;
            root.addView(bs, bsl);
        }
        if (glowOn) {
            int sp = Math.round(H * pct("glow_size", 46));
            GradientDrawable light = new GradientDrawable();
            light.setShape(GradientDrawable.OVAL);
            light.setGradientType(GradientDrawable.RADIAL_GRADIENT);
            light.setGradientRadius(sp / 2f);
            light.setColors(new int[]{0x00FFFFFF, 0x00FFFFFF});
            spot = new View(this);
            spot.setBackground(light);
            spot.setAlpha(0f);
            root.addView(spot, new FrameLayout.LayoutParams(sp, sp));
        }
        if (!Board.list(cfg).isEmpty()) {
            board = new BoardView(this, cfg);
            root.addView(board, new FrameLayout.LayoutParams(-1, -1));
        }
        buildDock();
        buildPill();
        if (cfg.optBoolean("eyes", false)) {
            watcher = new Noir.Watcher(this);
            FrameLayout.LayoutParams wl = new FrameLayout.LayoutParams(Math.round(W * 0.15f), Math.round(H * 0.085f));
            wl.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            wl.topMargin = edge();
            root.addView(watcher, wl);
            watcher.spinIn();
        }
        themeTo(0);
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!Store.config().toString().equals(cfg.toString())) {
            // settings changed (phone app or laptop): rebuild the screen with them
            recreate();
            return;
        }
        if (usesWall) {
            loadWallpaper(wallName.isEmpty());
        } else {
            Glass.useBackdrop(null, W, H);
            themeTo(0);
        }
        loadApps();
        main.removeCallbacks(weatherTick);
        main.post(weatherTick);
        main.removeCallbacks(closer);
        main.postDelayed(closer, 4000);
        seenStamp = Store.stamp();
        main.removeCallbacks(configWatch);
        main.postDelayed(configWatch, 900);
        bg.execute(this::keepScreensaver);
    }

    /**
     * Keeps Glass Home as the screensaver when the setting names an app that is not on the TV any more (the TV
     * would otherwise just switch the screen off), and turns on the remote-button helper when buttons are set.
     */
    private void keepScreensaver() {
        try {
            android.content.ContentResolver cr = getContentResolver();
            String comp = Settings.Secure.getString(cr, "screensaver_components");
            String pkg = comp == null || !comp.contains("/") ? "" : comp.substring(0, comp.indexOf('/'));
            boolean gone = pkg.isEmpty();
            if (!gone) {
                try {
                    getPackageManager().getPackageInfo(pkg, 0);
                } catch (PackageManager.NameNotFoundException e) {
                    gone = true;
                }
            }
            boolean canWrite = checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == PackageManager.PERMISSION_GRANTED;
            if (gone) {
                String mine = getPackageName() + "/.GlassDream";
                if (canWrite) {
                    Settings.Secure.putString(cr, "screensaver_components", mine);
                    Settings.Secure.putInt(cr, "screensaver_enabled", 1);
                    Settings.Secure.putInt(cr, "screensaver_activate_on_sleep", 1);
                } else if (new File(getFilesDir(), "adbkey.pk8").isFile()) {
                    LocalAdb.shell(this, "settings put secure screensaver_components " + mine
                            + "; settings put secure screensaver_enabled 1; settings put secure screensaver_activate_on_sleep 1", null);
                }
            }
            JSONObject keys = Store.config().optJSONObject("remote_keys");
            if (canWrite && keys != null && keys.length() > 0) {
                String svc = getPackageName() + "/" + KeyService.class.getName();
                String on = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
                if (on == null || !on.contains(svc)) {
                    Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                            on == null || on.isEmpty() ? svc : on + ":" + svc);
                    Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1);
                }
            }
        } catch (Exception ignored) { }
    }

    @Override
    protected void onPause() {
        main.removeCallbacks(configWatch);
        main.removeCallbacks(weatherTick);
        main.removeCallbacks(closer);
        main.removeCallbacks(rotate);
        closeOverlay();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        bg.shutdownNow();
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (!Store.config().toString().equals(cfg.toString())) {
            recreate();
            return;
        }
        // Home pressed while Home is showing: back to the first app.
        closeOverlay();
        if (!tiles.isEmpty()) {
            if (dockScroll != null) dockScroll.smoothScrollTo(0, 0);
            if (rowScroll != null) rowScroll.smoothScrollTo(0, 0);
            tiles.get(0).requestFocus();
        }
    }

    @Override
    public void onBackPressed() {
        if (overlay != null) closeOverlay();
        // Home has nowhere to go back to.
    }

    private ImageView wallpaperView() {
        ImageView v = new ImageView(this);
        v.setScaleType(ImageView.ScaleType.CENTER_CROP);
        return v;
    }

    // ------------------------------------------------------------------ wallpaper

    private void loadWallpaper(boolean instant) {
        main.removeCallbacks(rotate);
        bg.execute(() -> {
            File f = Store.currentWallpaper(this);
            String name = f == null ? "" : f.getName();
            if (f != null && name.equals(wallName)) {
                schedule();
                return;
            }
            Bitmap pic = Store.decode(f, W, H);
            Export.current(this, name);
            Bitmap blur = pic == null ? null : Glass.blurred(pic);
            int accent = pic != null ? Noir.accent(pic) : 0;
            main.post(() -> {
                wallName = name;
                ImageView in = showingA ? wallB : wallA, out = showingA ? wallA : wallB;
                if (pic == null) {
                    in.setImageDrawable(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                            new int[]{0xFF1B2A44, 0xFF3A1F3D, 0xFF0B0D12}));
                } else {
                    in.setImageBitmap(pic);
                }
                in.animate().alpha(1f).setDuration(instant ? 0 : 900).start();
                out.animate().alpha(0f).setDuration(instant ? 0 : 900)
                        .withEndAction(() -> out.setImageDrawable(null)).start();
                showingA = !showingA;
                Glass.useBackdrop(blur, W, H);
                themeTo(accent);
                schedule();
            });
        });
    }

    private void schedule() {
        long in = Store.nextChangeIn(this);
        main.removeCallbacks(rotate);
        if (in > 0) main.postDelayed(rotate, in);
    }

    // ------------------------------------------------------------------ dock (left)

    private void buildDock() {
        if (row) {
            buildRow();
            return;
        }
        int panelW = Math.round(W * 0.19f);
        pad = Math.round(H * 0.022f);
        tileW = panelW - 2 * pad;
        tileH = Math.round(tileW / 2.0f);
        gap = pad;
        float tileRadius = tileH * 0.30f;
        dock = new Glass.Frame(this, tileRadius + pad, darkInk ? 0x73FFFFFF : 0x40C8CDD8);
        if ("tonal".equals(tileStyle)) recolor.add(() -> dock.setTint(Board.alpha(Board.tones(accentColor())[0], 0xC7)));
        int edge = edge();
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(panelW, H - 2 * edge);
        lp.leftMargin = edge;
        lp.topMargin = edge;
        if (right) {
            lp.gravity = Gravity.END | Gravity.TOP;
            lp.leftMargin = 0;
            lp.rightMargin = edge;
        }
        root.addView(dock, lp);

        dockScroll = new ScrollView(this);
        dockScroll.setVerticalScrollBarEnabled(false);
        dockScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        dockScroll.setClipToPadding(false);
        dockScroll.setPadding(0, pad, 0, pad);
        dockList = new LinearLayout(this);
        dockList.setOrientation(LinearLayout.VERTICAL);
        dockList.setClipChildren(false);
        dockList.setClipToPadding(false);
        dockList.setPadding(pad, 0, pad, 0);
        dockScroll.addView(dockList);
        dock.addView(dockScroll, new FrameLayout.LayoutParams(-1, -1));
        dock.setClipChildren(true);
        bare = bareStyle();
        if (bare) {
            dock.setBare(true);
            dockScroll.setClipChildren(false);
        }
    }

    static final class App {
        String pkg, label;
        ComponentName component;
        boolean leanback;
        Drawable banner, icon;
        Bitmap logo, halo;
        int tint;
    }

    private void loadApps() {
        if (!listening) {
            // an app installed, removed or updated: look again next time
            listening = true;
            android.content.IntentFilter pf = new android.content.IntentFilter();
            pf.addAction(Intent.ACTION_PACKAGE_ADDED);
            pf.addAction(Intent.ACTION_PACKAGE_REMOVED);
            pf.addAction(Intent.ACTION_PACKAGE_CHANGED);
            pf.addAction(Intent.ACTION_PACKAGE_REPLACED);
            pf.addDataScheme("package");
            getApplicationContext().registerReceiver(new android.content.BroadcastReceiver() {
                @Override
                public void onReceive(android.content.Context c, Intent i) {
                    appCache = null;
                }
            }, pf);
        }
        bg.execute(() -> {
            PackageManager pm = getPackageManager();
            JSONObject look0 = Store.config();
            boolean wantBanners = !"icons".equals(look0.optString("dock_style", "icons"))
                    || "banner".equals(look0.optString("icon_style", "original"));
            Map<String, App> cached = appCache;
            boolean fresh = cached == null || (wantBanners && !cacheBanners);
            Map<String, App> found = new LinkedHashMap<>();
            if (!fresh) {
                for (App c : cached.values()) {
                    App a = new App();
                    a.pkg = c.pkg;
                    a.label = c.label;
                    a.component = c.component;
                    a.leanback = c.leanback;
                    a.banner = c.banner;
                    a.icon = c.icon;
                    found.put(a.pkg, a);
                }
            }
            for (String cat : fresh ? new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER} : new String[0]) {
                for (ResolveInfo ri : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(cat), 0)) {
                    ActivityInfo ai = ri.activityInfo;
                    if (ai == null || found.containsKey(ai.packageName)) continue;
                    if (Arrays.asList(NEVER_LISTED).contains(ai.packageName)) continue;
                    App a = new App();
                    a.pkg = ai.packageName;
                    a.label = String.valueOf(ri.loadLabel(pm));
                    a.component = new ComponentName(ai.packageName, ai.name);
                    a.leanback = cat.equals(Intent.CATEGORY_LEANBACK_LAUNCHER);
                    if (wantBanners) {
                        Drawable b = ai.loadBanner(pm);
                        if (b == null) b = ai.applicationInfo.loadBanner(pm);
                        a.banner = b;
                    }
                    a.icon = ri.loadIcon(pm);
                    found.put(a.pkg, a);
                }
            }
            if (fresh) {
                appCache = new LinkedHashMap<>(found);
                cacheBanners = wantBanners;
            }
            JSONObject cfg = Store.config();
            Set<String> hidden = new HashSet<>(Store.strings(cfg, "hidden"));
            hidden.addAll(csv(prefs.getString("hidden", "")));
            String dockNow = Store.rawConfig().optString("dock", "") + "|" + Store.rawConfig().optString("hidden", "");
            if (!dockNow.equals(prefs.getString("dock_seen", ""))) {
                prefs.edit().putString("dock_seen", dockNow).remove("order").remove("hidden").apply();
            }
            List<String> order = csv(prefs.getString("order", ""));
            if (order.isEmpty()) order = Store.strings(cfg, "dock", "com.google.android.youtube.tv", "com.netflix.ninja",
                    "in.startv.hotstar", "com.amazon.amazonvideo.livingroom", "org.videolan.vlc", "com.android.vending");
            List<App> list = new ArrayList<>();
            for (String p : order) {
                App a = found.remove(p);
                if (a != null && !hidden.contains(p)) list.add(a);
            }
            List<App> rest = new ArrayList<>(found.values());
            Collections.sort(rest, (x, y) -> x.label.compareToIgnoreCase(y.label));
            int pinned = list.size();
            if (cfg.optBoolean("show_other_apps", false)) for (App a : rest) if (!hidden.contains(a.pkg)) list.add(a);
            String style = cfg.optString("icon_style", "original");
            if (Packs.usesLogo(style)) {
                for (int i = 0; i < list.size(); i++) {
                    App a = list.get(i);
                    if (a.icon == null) continue;
                    String key = a.pkg + ":" + style + ("rainbow".equals(style) ? ":" + i + "/" + list.size() : "");
                    Bitmap[] made;
                    synchronized (LOGOS) {
                        made = LOGOS.get(key);
                    }
                    if (made == null) {
                        Bitmap mask;
                        synchronized (LOGOS) {
                            Bitmap[] base = LOGOS.get(a.pkg + ":mask");
                            mask = base == null ? null : base[0];
                        }
                        if (mask == null) {
                            mask = Noir.whiteLogo(a.icon, 144);
                            synchronized (LOGOS) {
                                LOGOS.put(a.pkg + ":mask", new Bitmap[]{mask});
                            }
                        }
                        made = new Bitmap[]{Packs.render(style, mask, i, list.size()),
                                mask != null && Packs.haloed(style) ? Noir.halo(mask) : null};
                        synchronized (LOGOS) {
                            LOGOS.put(key, made);
                        }
                    }
                    a.logo = made[0];
                    a.halo = made[1];
                    a.tint = "rainbow".equals(style) ? Packs.hue(i, list.size()) : 0;
                }
            }
            boolean inputs = cfg.optBoolean("inputs_tile", false) && hasInputs();
            main.post(() -> fillDock(list, inputs ? pinned : -1));
            // for the phone app (only redone when apps or wallpapers changed)
            if (fresh) Export.apps(this);
            Export.thumbs(this);
        });
    }

    private void fillDock(List<App> apps, int inputsAt) {
        sizeDock(apps.size() + (inputsAt >= 0 ? 1 : 0));
        String focusedPkg = lastTile == null ? null : (String) lastTile.getTag();
        dockList.removeAllViews();
        tiles.clear();
        tinted.clear();
        tileRecolor.clear();
        floating.clear();
        halos.clear();
        View refocus = null;
        for (int i = 0; i <= apps.size(); i++) {
            if (i == inputsAt) addTile(inputsTile());
            if (i == apps.size()) break;
            View t = appTile(apps.get(i));
            addTile(t);
            if (apps.get(i).pkg.equals(focusedPkg)) refocus = t;
        }
        wireKeys();
        View target = refocus != null ? refocus : tiles.isEmpty() ? null : tiles.get(0);
        if (target != null && (getCurrentFocus() == null || overlay == null)) target.requestFocus();
    }

    private void addTile(View t) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(tileW, tileH);
        if (row) lp.leftMargin = tiles.isEmpty() ? 0 : gap;
        else lp.topMargin = tiles.isEmpty() ? 0 : gap;
        dockList.addView(t, lp);
        tiles.add(t);
    }

    /**
     * Up to 7 apps: tiles (2:1) sized so they fill the panel exactly, with the same gap between tiles as
     * around them. More apps keep the standard size and scroll. The panel's width follows the tiles.
     */
    private void sizeDock(int n) {
        if (row) return;
        if (bare) {
            int colH = H - 2 * edge();
            gap = Math.round(H * pct("icon_gap", 4.2f));
            int s = Math.round(H * pct("icon_size", 10.5f));
            if (list) {
                gap = Math.round(H * 0.012f);
                s = Math.round(H * 0.072f);
            }
            int room = colH - 2 * pad;
            if (n > 0 && n * s + (n - 1) * gap > room)
                s = Math.max(Math.round(H * 0.06f), (room - (n - 1) * gap) / n);
            tileH = s;
            tileW = Math.round(W * (list ? 0.34f : 0.26f));
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) dock.getLayoutParams();
            lp.width = tileW + 2 * pad;
            dock.setLayoutParams(lp);
            int used = n * s + Math.max(0, n - 1) * gap;
            int top = Math.max(pad, (colH - used) / 2);
            dockList.setPadding(pad, 0, pad, 0);
            dockScroll.setPadding(0, top, 0, top);
            return;
        }
        int panelH = H - 2 * edge();
        int std = Math.round((W * 0.19f - 2 * pad) / 2f);
        int fit = n > 0 ? (panelH - 2 * pad - (n - 1) * gap) / n : std;
        tileH = n > 0 && n <= 7 ? Math.min(fit, Math.round(H * 0.2f)) : std;
        tileW = Math.round(tileH * ("tonal".equals(tileStyle) ? 2.8f : 2f));
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) dock.getLayoutParams();
        lp.width = tileW + 2 * pad;
        dock.setLayoutParams(lp);
        dock.setRadius(tileH * 0.30f + pad);
        dockList.setPadding(pad, 0, pad, 0);
        dockScroll.setPadding(0, pad, 0, pad);
    }

    private View appTile(App a) {
        if (list) return listTile(a);
        if (bare) return iconTile(a);
        if ("plush".equals(tileStyle) || "soft".equals(tileStyle) || "tonal".equals(tileStyle)) return styledTile(a);
        FrameLayout card = card();
        card.setTag(a.pkg);
        float cr = row ? tileH * 0.14f : tileH * 0.30f;
        boolean logoStyle = Packs.usesLogo(iconStyle) && a.logo != null;
        boolean boxed = cfg.optBoolean("tile_box", true);
        if (!boxed && (logoStyle || "original".equals(iconStyle))) {
            // floating: no box, no ring; the glow (if on) and the size show the selection
            card.setOutlineProvider(null);
            card.setClipToOutline(false);
            card.setClipChildren(false);
            floating.add(card);
        }
        if (logoStyle) {
            boolean round = "bubble".equals(iconStyle) || "badge".equals(iconStyle);
            int s = Math.round(tileH * (boxed ? (round ? 0.86f : 0.66f) : (round ? 0.92f : 0.88f)));
            if (boxed) card.setBackground(rounded(0x26FFFFFF, cr));
            if (a.halo != null) {
                ImageView hv = new ImageView(this);
                hv.setImageBitmap(a.halo);
                hv.setAlpha(0f);
                if (a.tint != 0) {
                    hv.setImageTintList(ColorStateList.valueOf(a.tint));
                } else {
                    hv.setImageTintList(ColorStateList.valueOf(accentColor()));
                    tinted.add(hv);
                }
                int hs = Math.round(s * 1.5f);
                FrameLayout.LayoutParams hl = new FrameLayout.LayoutParams(hs, hs);
                hl.gravity = Gravity.CENTER;
                card.addView(hv, hl);
                halos.put(card, hv);
            }
            ImageView lg = new ImageView(this);
            lg.setScaleType(ImageView.ScaleType.FIT_CENTER);
            lg.setImageBitmap(a.logo);
            if (Packs.tinted(iconStyle)) {
                lg.setImageTintList(ColorStateList.valueOf(accentColor()));
                tinted.add(lg);
            }
            FrameLayout.LayoutParams ll = new FrameLayout.LayoutParams(s, s);
            ll.gravity = Gravity.CENTER;
            card.addView(lg, ll);
        } else if (row && "original".equals(iconStyle) && a.icon != null) {
            // the app's own icon in the middle of the tile (on glass when boxed)
            if (boxed) card.setBackground(rounded(0x33FFFFFF, cr));
            ImageView ic = new ImageView(this);
            Drawable d = a.icon;
            if (d instanceof AdaptiveIconDrawable) d = new FullBleed((AdaptiveIconDrawable) d);
            ic.setImageDrawable(d);
            int s = Math.round(tileH * (boxed ? 0.6f : 0.72f));
            float ir = s * 0.23f;
            ic.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View v, Outline o) {
                    o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), ir);
                }
            });
            ic.setClipToOutline(true);
            FrameLayout.LayoutParams il = new FrameLayout.LayoutParams(s, s);
            il.gravity = Gravity.CENTER;
            card.addView(ic, il);
        } else if (a.banner != null && !"original".equals(iconStyle)) {
            ImageView b = new ImageView(this);
            b.setScaleType(ImageView.ScaleType.CENTER_CROP);
            b.setImageDrawable(a.banner);
            card.addView(b, new FrameLayout.LayoutParams(-1, -1));
        } else {
            card.setBackground(rounded(0xFF1C1C1F, cr));
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.HORIZONTAL);
            col.setGravity(Gravity.CENTER);
            ImageView ic = new ImageView(this);
            ic.setImageDrawable(a.icon);
            int s = Math.round(tileH * 0.46f);
            col.addView(ic, new LinearLayout.LayoutParams(s, s));
            TextView name = text(a.label, tileH * 0.17f, Color.WHITE, true);
            name.setSingleLine(true);
            name.setPadding(Math.round(tileH * 0.1f), 0, 0, 0);
            col.addView(name);
            card.addView(col, new FrameLayout.LayoutParams(-1, -1));
        }
        card.setContentDescription(a.label);
        card.setOnClickListener(v -> launch(a));
        card.setOnLongClickListener(v -> {
            appMenu(a, v);
            return true;
        });
        return card;
    }

    /** A selected button or pill: light on dark themes, dark on light ones. */
    private int focusBg() {
        return darkInk ? 0xFF2B3240 : 0xFFF5F5F7;
    }

    private int focusFg() {
        return darkInk ? Color.WHITE : 0xFF1C1C1E;
    }

    /** Minimalist: the app's name only; the chosen one turns bright and slides out, with an accent bar. */
    private View listTile(App a) {
        FrameLayout t = new FrameLayout(this);
        t.setTag(a.pkg);
        t.setFocusable(true);
        t.setClickable(true);
        t.setClipChildren(false);
        t.setContentDescription(a.label);
        float rest = pct("icon_idle", 40);
        String f = cfg.optString("title_font", "light");
        if ("dot".equals(f)) f = "light";
        TextView name = text(a.label, tileH * 0.62f, ink(255), false);
        name.setTypeface(font(f));
        name.setSingleLine(true);
        name.setAlpha(rest);
        if (!darkInk) name.setShadowLayer(H * 0.01f, 0, H * 0.002f, 0x99000000);
        int side = (right ? Gravity.END : Gravity.START) | Gravity.CENTER_VERTICAL;
        FrameLayout.LayoutParams nl = new FrameLayout.LayoutParams(-2, -2);
        nl.gravity = side;
        if (right) nl.rightMargin = Math.round(H * 0.035f);
        else nl.leftMargin = Math.round(H * 0.035f);
        t.addView(name, nl);
        View bar = new View(this);
        bar.setBackground(rounded(accentColor(), H * 0.004f));
        bar.setAlpha(0f);
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(Math.max(3, Math.round(H * 0.007f)), Math.round(tileH * 0.55f));
        bl.gravity = side;
        t.addView(bar, bl);
        tileRecolor.add(() -> bar.setBackground(rounded(accentColor(), H * 0.004f)));
        float slide = H * 0.018f * (right ? -1 : 1);
        t.setOnFocusChangeListener((v, focused) -> {
            name.animate().alpha(focused ? 1f : rest).translationX(focused ? slide : 0f).setDuration(180)
                    .setInterpolator(new DecelerateInterpolator(1.6f)).start();
            bar.animate().alpha(focused ? 1f : 0f).setDuration(180).start();
            if (focused) {
                lastTile = v;
                keepVisible(v);
            }
        });
        t.setOnClickListener(v -> launch(a));
        t.setOnLongClickListener(v -> {
            appMenu(a, v);
            return true;
        });
        return t;
    }

    /**
     * Plush (pastel cushions from the wallpaper's colour), Soft (light pillows that rise) and Material (tonal
     * pills, the chosen one filled): the app's own icon and its name on each tile.
     */
    private View styledTile(App a) {
        boolean tonal = "tonal".equals(tileStyle), soft = "soft".equals(tileStyle);
        float r = tonal ? tileH * 0.5f : tileH * (row ? 0.24f : 0.3f);
        Runnable[] hook = new Runnable[1];
        FrameLayout card = card(r, () -> {
            if (hook[0] != null) hook[0].run();
        });
        card.setTag(a.pkg);
        GradientDrawable back = new GradientDrawable();
        back.setCornerRadius(r);
        card.setBackground(back);
        card.setElevation(tonal ? 0f : soft ? H * 0.012f : H * 0.016f);
        ImageView ic = new ImageView(this);
        Drawable d = a.icon;
        if (d instanceof AdaptiveIconDrawable) d = new FullBleed((AdaptiveIconDrawable) d);
        ic.setImageDrawable(d);
        TextView name = text(a.label, tileH * (tonal ? 0.2f : 0.13f), Color.WHITE, true);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        if (tonal) {
            int s = Math.round(tileH * 0.58f);
            ic.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View v, Outline o) {
                    o.setOval(0, 0, v.getWidth(), v.getHeight());
                }
            });
            ic.setClipToOutline(true);
            LinearLayout line = new LinearLayout(this);
            line.setGravity(Gravity.CENTER_VERTICAL);
            line.setPadding(Math.round(tileH * 0.21f), 0, Math.round(tileH * 0.25f), 0);
            line.addView(ic, new LinearLayout.LayoutParams(s, s));
            LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(0, -2, 1f);
            nl.leftMargin = Math.round(tileH * 0.2f);
            line.addView(name, nl);
            card.addView(line, new FrameLayout.LayoutParams(-1, -1));
        } else {
            int s = Math.round(tileH * (soft ? 0.44f : 0.5f));
            float ir = s * 0.24f;
            ic.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View v, Outline o) {
                    o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), ir);
                }
            });
            ic.setClipToOutline(true);
            ic.setElevation(H * 0.004f);
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER);
            col.addView(ic, new LinearLayout.LayoutParams(s, s));
            name.setGravity(Gravity.CENTER);
            name.setPadding(Math.round(tileH * 0.08f), Math.round(tileH * 0.07f), Math.round(tileH * 0.08f), 0);
            col.addView(name, new LinearLayout.LayoutParams(-1, -2));
            card.addView(col, new FrameLayout.LayoutParams(-1, -1));
        }
        Runnable paint = () -> {
            boolean f = card.isFocused();
            int ac = accentColor();
            if (tonal) {
                int[] t = Board.tones(ac);
                back.setColor(f ? t[3] : Board.alpha(t[1], 0xEB));
                name.setTextColor(f ? t[4] : t[2]);
            } else if (soft) {
                int base = parseColor(cfg.optString("bg_color", "#E3E8EF"), 0xFFE3E8EF);
                back.setOrientation(GradientDrawable.Orientation.TL_BR);
                back.setColors(new int[]{Board.mix(base, Color.WHITE, f ? 0.6f : 0.38f), Board.mix(base, Color.BLACK, 0.03f)});
                name.setTextColor(f ? Board.mix(ac, Color.BLACK, 0.2f) : 0xCC2B3240);
                card.setElevation(f ? H * 0.026f : H * 0.012f);
            } else {
                back.setOrientation(GradientDrawable.Orientation.TOP_BOTTOM);
                back.setColors(new int[]{Board.mix(ac, Color.WHITE, f ? 0.5f : 0.64f), Board.mix(ac, Color.WHITE, f ? 0.2f : 0.38f)});
                name.setTextColor(Board.mix(ac, Color.BLACK, 0.72f));
            }
        };
        hook[0] = paint;
        paint.run();
        tileRecolor.add(paint);
        card.setContentDescription(a.label);
        card.setOnClickListener(v -> launch(a));
        card.setOnLongClickListener(v -> {
            appMenu(a, v);
            return true;
        });
        return card;
    }

    /** Just the icon; the selected one grows (and glows) and its name fades in beside it. */
    private View iconTile(App a) {
        int s = tileH;
        FrameLayout t = new FrameLayout(this);
        t.setTag(a.pkg);
        t.setFocusable(true);
        t.setClickable(true);
        t.setClipChildren(false);
        t.setContentDescription(a.label);
        boolean flat = Packs.usesLogo(iconStyle) && a.logo != null;
        boolean wide = "banner".equals(iconStyle);
        int iw = wide ? Math.round(s * 16f / 9f) : s;
        iconW = iw;
        boolean accent = flat && Packs.tinted(iconStyle);
        int side = (right ? Gravity.END : Gravity.START) | Gravity.CENTER_VERTICAL;
        float grow = pct("focus_scale", 122);
        float rest = pct("icon_idle", 85);

        ImageView halo = null;
        if (flat && a.halo != null) {
            halo = new ImageView(this);
            halo.setImageBitmap(a.halo);
            halo.setAlpha(0f);
            if (a.tint != 0) {
                halo.setImageTintList(ColorStateList.valueOf(a.tint));
            } else {
                halo.setImageTintList(ColorStateList.valueOf(accentColor()));
                tinted.add(halo);
            }
            int hs = Math.round(s * 1.5f);
            FrameLayout.LayoutParams hl = new FrameLayout.LayoutParams(hs, hs);
            hl.gravity = side;
            if (right) hl.rightMargin = -(hs - s) / 2;
            else hl.leftMargin = -(hs - s) / 2;
            t.addView(halo, hl);
        }

        ImageView ic = new ImageView(this);
        ic.setScaleType(ImageView.ScaleType.FIT_CENTER);
        if (flat) {
            ic.setImageBitmap(a.logo);
            if (accent) {
                ic.setImageTintList(ColorStateList.valueOf(accentColor()));
                tinted.add(ic);
            }
        } else {
            Drawable d = wide && a.banner != null ? a.banner : a.icon;
            if (d instanceof AdaptiveIconDrawable) d = new FullBleed((AdaptiveIconDrawable) d);
            ic.setImageDrawable(d);
            if (wide) ic.setScaleType(a.banner != null ? ImageView.ScaleType.CENTER_CROP : ImageView.ScaleType.FIT_CENTER);
            if (wide && a.banner == null) ic.setBackground(rounded(0xFF1C1C1F, s * 0.18f));
            float r = s * (wide ? 0.18f : 0.23f);
            ic.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View v, Outline o) {
                    o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), r);
                }
            });
            ic.setClipToOutline(true);
            ic.setElevation(H * 0.008f);
        }
        ic.setAlpha(rest);
        FrameLayout.LayoutParams il = new FrameLayout.LayoutParams(iw, s);
        il.gravity = side;
        t.addView(ic, il);

        TextView name = null;
        if (cfg.optBoolean("labels", true)) {
            name = text(a.label, s * pct("label_size", 24), ink(255), true);
            name.setSingleLine(true);
            if (!darkInk) name.setShadowLayer(H * 0.012f, 0, H * 0.002f, 0xB3000000);
            name.setAlpha(0f);
            FrameLayout.LayoutParams nl = new FrameLayout.LayoutParams(-2, -2);
            nl.gravity = side;
            if (right) nl.rightMargin = Math.round(iw + s * 0.42f);
            else nl.leftMargin = Math.round(iw + s * 0.42f);
            t.addView(name, nl);
        }

        final ImageView haloView = halo;
        final TextView nameView = name;
        t.setOnFocusChangeListener((v, focused) -> {
            ic.animate().scaleX(focused ? grow : 1f).scaleY(focused ? grow : 1f).alpha(focused ? 1f : rest)
                    .translationZ(focused && !flat ? H * 0.02f : 0f)
                    .setDuration(180).setInterpolator(new DecelerateInterpolator(1.6f)).start();
            if (haloView != null) {
                haloView.animate().alpha(focused ? 1f : 0f).scaleX(focused ? grow : 1f).scaleY(focused ? grow : 1f)
                        .setDuration(220).start();
            }
            if (nameView != null) {
                float off = s * 0.15f * (right ? 1 : -1);
                nameView.animate().alpha(focused ? 1f : 0f).translationX(focused ? 0f : off).setDuration(180).start();
            }
            if (focused) {
                lastTile = v;
                keepVisible(v);
                follow(v);
            } else if (spot != null) {
                v.postDelayed(() -> {
                    if (!tiles.contains(getCurrentFocus())) spot.animate().alpha(0f).setDuration(200).start();
                }, 60);
            }
        });
        t.setOnClickListener(v -> launch(a));
        t.setOnLongClickListener(v -> {
            appMenu(a, v);
            return true;
        });
        return t;
    }

    /** The colour everything accent-coloured uses: a fixed one from the settings, or the wallpaper's. */
    private int accentColor() {
        return accentNow != 0 ? accentNow : 0xFF39E6FF;
    }

    /** Re-colours the glow, accent icons, accent clock and the eyes for a new wallpaper colour. */
    private void themeTo(int wallAccent) {
        String fixed = cfg.optString("accent", "auto");
        accentNow = "auto".equals(fixed) ? wallAccent : parseColor(fixed, wallAccent);
        int ac = accentColor();
        for (ImageView v : tinted) v.setImageTintList(ColorStateList.valueOf(ac));
        for (TextView v : accentText) v.setTextColor(ac);
        if (secondsView != null) secondsView.setColor(clockColor());
        if (spot != null && spot.getBackground() instanceof GradientDrawable) {
            String gc = cfg.optString("glow_color", "auto");
            int c = "auto".equals(gc) ? (accentNow == 0 ? 0xFFEEF4FF : accentNow)
                    : "white".equals(gc) ? 0xFFFFFFFF : parseColor(gc, 0xFFEEF4FF);
            int soft = Color.rgb((Color.red(c) + 255) / 2, (Color.green(c) + 255) / 2, (Color.blue(c) + 255) / 2);
            float k = Math.max(0f, Math.min(1f, pct("glow_strength", 55)));
            int centre = Math.round(255 * Math.min(1f, k * 0.92f));
            ((GradientDrawable) spot.getBackground()).setColors(new int[]{
                    Noir.withAlpha(soft, centre), Noir.withAlpha(c, Math.round(centre * 0.38f)), Noir.withAlpha(c, 0)});
        }
        if (watcher != null) watcher.setAccent(accentNow);
        for (DotViews.Clock dc : dotClocks) dc.setColor(clockColor());
        for (Runnable r : recolor) r.run();
        for (Runnable r : tileRecolor) r.run();
        if (board != null) board.setAccent(accentNow);
    }

    private int clockColor() {
        String cc = cfg.optString("clock_color", "white");
        if ("accent".equals(cc)) return accentColor();
        return "white".equals(cc) ? ink(255) : parseColor(cc, ink(255));
    }

    /** The glow slides behind the selected icon (and the eyes, if on, look at it). */
    private void follow(View v) {
        if (spot == null && watcher == null) return;
        v.postDelayed(() -> {
            int[] at = new int[2];
            v.getLocationOnScreen(at);
            float half = (iconW > 0 ? iconW : tileH) / 2f;
            float cx = row || !bare ? at[0] + v.getWidth() / 2f : right ? at[0] + v.getWidth() - half : at[0] + half;
            float cy = at[1] + v.getHeight() / 2f;
            if (spot != null) {
                boolean first = spot.getTranslationX() == 0f && spot.getTranslationY() == 0f;
                spot.animate().translationX(cx - spot.getWidth() / 2f).translationY(cy - spot.getHeight() / 2f)
                        .alpha(1f).setDuration(first ? 0 : 260).setInterpolator(new DecelerateInterpolator(1.8f)).start();
            }
            if (watcher != null) watcher.lookAt(cx, cy);
        }, 40);
    }

    /** An adaptive icon drawn edge to edge (its safe zone fills the square), so every icon has the same shape. */
    static final class FullBleed extends Drawable {
        private final Drawable back, fore;
        private final Rect big = new Rect();

        FullBleed(AdaptiveIconDrawable d) {
            back = d.getBackground();
            fore = d.getForeground();
        }

        @Override
        public void draw(Canvas c) {
            Rect b = getBounds();
            int dx = b.width() / 4, dy = b.height() / 4;
            big.set(b.left - dx, b.top - dy, b.right + dx, b.bottom + dy);
            if (back != null) {
                back.setBounds(big);
                back.draw(c);
            }
            if (fore != null) {
                fore.setBounds(big);
                fore.draw(c);
            }
        }

        @Override public int getIntrinsicWidth() { return 192; }
        @Override public int getIntrinsicHeight() { return 192; }
        @Override public void setAlpha(int a) { if (back != null) back.setAlpha(a); if (fore != null) fore.setAlpha(a); }
        @Override public void setColorFilter(ColorFilter f) { if (back != null) back.setColorFilter(f); if (fore != null) fore.setColorFilter(f); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    private View inputsTile() {
        FrameLayout card = card();
        card.setTag("::inputs");
        card.setBackground(rounded(0xFF1C1C1F, tileH * 0.30f));
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER);
        ImageView ic = new ImageView(this);
        ic.setImageResource(R.drawable.g_input);
        ic.setImageTintList(ColorStateList.valueOf(Color.WHITE));
        int s = Math.round(tileH * 0.34f);
        row.addView(ic, new LinearLayout.LayoutParams(s, s));
        TextView t = text("Inputs", tileH * 0.2f, Color.WHITE, true);
        t.setPadding(Math.round(tileH * 0.1f), 0, 0, 0);
        row.addView(t);
        card.addView(row, new FrameLayout.LayoutParams(-1, -1));
        card.setContentDescription("Inputs");
        card.setOnClickListener(v -> showInputs(v));
        return card;
    }

    /** A rounded, focusable tile that lifts and gains a bright ring when focused. */
    private FrameLayout card() {
        return card(row ? tileH * 0.14f : tileH * 0.30f, null);
    }

    private FrameLayout card(float r, Runnable onFocus) {
        FrameLayout card = new FrameLayout(this);
        card.setFocusable(true);
        card.setClickable(true);
        card.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), r);
            }
        });
        card.setClipToOutline(true);
        GradientDrawable ring = new GradientDrawable();
        ring.setCornerRadius(r);
        ring.setStroke(Math.max(2, Math.round(H * 0.004f)), FOCUS_RING);
        ring.setColor(Color.TRANSPARENT);
        float grow = row ? pct("focus_scale", 108) : 1.045f;
        card.setOnFocusChangeListener((v, focused) -> {
            if (Store.config().optBoolean("focus_ring", false) && !floating.contains(v)) v.setForeground(focused ? ring : null);
            View hv = halos.get(v);
            if (hv != null) hv.animate().alpha(focused ? 1f : 0f).setDuration(220).start();
            v.animate().scaleX(focused ? grow : 1f).scaleY(focused ? grow : 1f)
                    .translationZ(focused ? H * 0.015f : 0f)
                    .setDuration(170).setInterpolator(new DecelerateInterpolator(1.6f)).start();
            if (onFocus != null) onFocus.run();
            if (focused) {
                lastTile = v;
                keepVisible(v);
                if (appTitle != null && v.getContentDescription() != null) appTitle.setText(v.getContentDescription());
                if (dotTitle != null && v.getContentDescription() != null) dotTitle.setText(v.getContentDescription());
                follow(v);
            } else if (spot != null) {
                v.postDelayed(() -> {
                    if (!tiles.contains(getCurrentFocus())) spot.animate().alpha(0f).setDuration(200).start();
                }, 60);
            }
        });
        return card;
    }

    /** Keeps the focused tile inside the panel with a little of the next one showing. */
    private void keepVisible(View v) {
        if (dockScroll == null) {
            if (rowScroll == null) return;
            int left = v.getLeft(), right = v.getRight(), x = rowScroll.getScrollX(), w = rowScroll.getWidth();
            int margin = tileW / 3;
            if (right + margin > x + w) rowScroll.smoothScrollTo(right + margin - w, 0);
            else if (left - margin < x) rowScroll.smoothScrollTo(Math.max(0, left - margin), 0);
            return;
        }
        int top = v.getTop(), bottom = v.getBottom();
        int viewTop = dockScroll.getScrollY(), viewBottom = viewTop + dockScroll.getHeight() - gap;
        int margin = tileH / 2;
        if (bottom + margin > viewBottom) dockScroll.smoothScrollTo(0, bottom + margin - (viewBottom - viewTop));
        else if (top - margin < viewTop) dockScroll.smoothScrollTo(0, Math.max(0, top - margin));
    }

    // ------------------------------------------------------------------ Clean theme (row)

    private void buildRow() {
        pad = Math.round(H * 0.022f);
        gap = Math.round(H * 0.033f);
        int side = Math.round(W * 0.06f);
        int bandW = W - 2 * side;
        int visible = Math.max(2, Math.min(7, cfg.optInt("row_visible", 4)));
        tileW = (bandW - 2 * pad - (visible - 1) * gap) / visible;
        float ratio = "plush".equals(tileStyle) ? 0.78f : "soft".equals(tileStyle) ? 0.7f : "tonal".equals(tileStyle) ? 0.5f : 0.5625f;
        tileH = Math.round(tileW * ratio);
        int bandH = tileH + 2 * pad;
        int bandTop = Math.round(H * pct("row_top", 64.5f));
        dock = new Glass.Frame(this, tileH * 0.22f + pad, 0x99121318);
        if (!cfg.optBoolean("row_band", true)) dock.setBare(true);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(bandW, bandH);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.leftMargin = side;
        lp.topMargin = bandTop;
        root.addView(dock, lp);
        rowScroll = new HorizontalScrollView(this);
        rowScroll.setHorizontalScrollBarEnabled(false);
        rowScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        rowScroll.setClipToPadding(false);
        rowScroll.setClipChildren(false);
        rowScroll.setPadding(pad, 0, pad, 0);
        dockList = new LinearLayout(this);
        dockList.setOrientation(LinearLayout.HORIZONTAL);
        dockList.setGravity(Gravity.CENTER_VERTICAL);
        dockList.setClipChildren(false);
        dockList.setClipToPadding(false);
        rowScroll.addView(dockList, new FrameLayout.LayoutParams(-2, -1));
        dock.addView(rowScroll, new FrameLayout.LayoutParams(-1, -1));

        appTitle = text("", H * 0.075f, ink(255), false);
        appTitle.setTypeface(font(cfg.optString("title_font", "light")));
        appTitle.setSingleLine(true);
        if (!darkInk) appTitle.setShadowLayer(H * 0.015f, 0, H * 0.003f, 0x80000000);
        boolean dotName = "dot".equals(cfg.optString("title_font", "light"));
        appTitle.setVisibility(cfg.optBoolean("row_title", true) && !dotName ? View.VISIBLE : View.GONE);
        FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(-2, -2);
        tl.gravity = Gravity.TOP | Gravity.START;
        tl.leftMargin = side + pad;
        tl.topMargin = bandTop - Math.round(H * 0.135f);
        root.addView(appTitle, tl);
        if (dotName && cfg.optBoolean("row_title", true)) {
            dotTitle = new DotViews.Text(this, H * 0.075f * 0.72f / 7f);
            dotTitle.setColor(ink(255));
            FrameLayout.LayoutParams dl = new FrameLayout.LayoutParams(-2, -2);
            dl.gravity = Gravity.TOP | Gravity.START;
            dl.leftMargin = side + pad;
            dl.topMargin = bandTop - Math.round(H * 0.12f);
            root.addView(dotTitle, dl);
        }
    }

    /** Greeting, a big clock and the date top left; labelled pills (RAM, Inputs, Settings...) top right. */
    private void buildPills() {
        int side = Math.round(W * 0.06f) + Math.round(H * 0.022f);
        int top = Math.round(H * 0.06f);
        boolean h12 = "12".equals(cfg.optString("clock", "24"));
        int col = clockColor();
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        if (cfg.optBoolean("greeting", true)) {
            TextView greet = text(greeting(), H * 0.022f, ink(0xBF), true);
            greet.setLetterSpacing(0.25f);
            if (!darkInk) greet.setShadowLayer(H * 0.01f, 0, 0, 0x80000000);
            block.addView(greet);
        }
        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.BOTTOM);
        boolean dotClock = "dot".equals(cfg.optString("clock_style", "text"));
        if (cfg.optBoolean("bar_clock", true) && dotClock) {
            DotViews.Clock dc = new DotViews.Clock(this, H * 0.105f * pct("clock_scale", 100), h12, cfg.optBoolean("seconds", false));
            dc.setColor(col);
            dotClocks.add(dc);
            LinearLayout.LayoutParams dcl0 = new LinearLayout.LayoutParams(-2, -2);
            dcl0.bottomMargin = Math.round(H * 0.006f);
            line.addView(dc, dcl0);
            View div = new View(this);
            div.setBackgroundColor(ink(0x4D));
            LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(Math.max(1, Math.round(H * 0.0015f)), Math.round(H * 0.055f));
            dl.leftMargin = Math.round(H * 0.03f);
            dl.rightMargin = Math.round(H * 0.03f);
            dl.bottomMargin = Math.round(H * 0.006f);
            line.addView(div, dl);
        } else if (cfg.optBoolean("bar_clock", true)) {
            TextClock time = new TextClock(this);
            String fmt = h12 ? "h:mm" : "HH:mm";
            time.setFormat12Hour(fmt);
            time.setFormat24Hour(fmt);
            time.setTextColor(col);
            if ("accent".equals(cfg.optString("clock_color", "white"))) accentText.add(time);
            time.setTextSize(TypedValue.COMPLEX_UNIT_PX, H * 0.105f * pct("clock_scale", 100));
            time.setTypeface(font(cfg.optString("clock_font", "light")));
            time.setIncludeFontPadding(false);
            if (!darkInk) time.setShadowLayer(H * 0.015f, 0, H * 0.003f, 0x80000000);
            line.addView(time);
            if (cfg.optBoolean("seconds", false)) {
                secondsView = new Noir.Seconds(this, time, H * 0.035f * pct("clock_scale", 100));
                secondsView.setColor(col);
                secondsView.setTypeface(font(cfg.optString("clock_font", "light")));
                LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-2, -1);
                sl.leftMargin = Math.round(H * 0.008f);
                line.addView(secondsView, sl);
            }
            if (h12) {
                TextClock ap = new TextClock(this);
                ap.setFormat12Hour("a");
                ap.setFormat24Hour("a");
                ap.setTextColor(ink(0xD9));
                ap.setTextSize(TypedValue.COMPLEX_UNIT_PX, H * 0.03f);
                ap.setTypeface(font("medium"));
                ap.setAllCaps(true);
                LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(-2, -2);
                al.leftMargin = Math.round(H * 0.012f);
                al.bottomMargin = Math.round(H * 0.012f);
                line.addView(ap, al);
            }
            View div = new View(this);
            div.setBackgroundColor(ink(0x4D));
            LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(Math.max(1, Math.round(H * 0.0015f)), Math.round(H * 0.055f));
            dl.leftMargin = Math.round(H * 0.03f);
            dl.rightMargin = Math.round(H * 0.03f);
            dl.bottomMargin = Math.round(H * 0.006f);
            line.addView(div, dl);
        }
        LinearLayout dateCol = new LinearLayout(this);
        dateCol.setOrientation(LinearLayout.VERTICAL);
        TextClock date = new TextClock(this);
        date.setFormat12Hour("EEEE  dd MMMM");
        date.setFormat24Hour("EEEE  dd MMMM");
        date.setAllCaps(true);
        date.setTextColor(ink(0xD9));
        date.setTextSize(TypedValue.COMPLEX_UNIT_PX, H * 0.026f);
        date.setTypeface(font("medium"));
        date.setLetterSpacing(0.15f);
        if (!darkInk) date.setShadowLayer(H * 0.01f, 0, 0, 0x80000000);
        dateCol.addView(date);
        if (cfg.optBoolean("bar_weather", true)) {
            weatherView = text("", H * 0.024f, ink(0xBF), false);
            weatherView.setLetterSpacing(0.08f);
            if (!darkInk) weatherView.setShadowLayer(H * 0.01f, 0, 0, 0x80000000);
            weatherView.setVisibility(View.GONE);
            dateCol.addView(weatherView);
        }
        LinearLayout.LayoutParams dcl = new LinearLayout.LayoutParams(-2, -2);
        dcl.bottomMargin = Math.round(H * 0.008f);
        line.addView(dateCol, dcl);
        block.addView(line);
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(-2, -2);
        bl.gravity = Gravity.TOP | Gravity.START;
        bl.leftMargin = side;
        bl.topMargin = top;
        root.addView(block, bl);

        pillRow = new LinearLayout(this);
        pillRow.setGravity(Gravity.CENTER_VERTICAL);
        pillRow.setClipChildren(false);
        FrameLayout.LayoutParams pl = new FrameLayout.LayoutParams(-2, Math.round(H * 0.08f));
        pl.gravity = Gravity.TOP | Gravity.END;
        pl.rightMargin = side;
        pl.topMargin = top + Math.round(H * 0.045f);
        root.addView(pillRow, pl);
        for (String which : Store.strings(cfg, "buttons", "memory", "inputs", "settings")) {
            switch (which) {
                case "wifi": addPill(R.drawable.g_wifi, "Wi-Fi", v -> open(new Intent(Settings.ACTION_WIFI_SETTINGS))); break;
                case "bluetooth": addPill(R.drawable.g_bluetooth, "Bluetooth", v -> open(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS))); break;
                case "profile": addPill(R.drawable.g_person, "Accounts", v -> open(new Intent(Settings.ACTION_SETTINGS))); break;
                case "memory": memoryPill = addPill(R.drawable.g_chip, memoryShort(), this::showMemory); break;
                case "music": addPill(R.drawable.g_music, "Music", v -> openMusic()); break;
                case "inputs": addPill(R.drawable.g_inputs, "Inputs", this::showInputs); break;
                case "settings": addPill(R.drawable.g_gear, "Settings", v -> open(new Intent(Settings.ACTION_SETTINGS))); break;
                default: break;
            }
        }
    }

    private String greeting() {
        int h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        return h < 5 ? "GOOD NIGHT" : h < 12 ? "GOOD MORNING" : h < 17 ? "GOOD AFTERNOON" : h < 22 ? "GOOD EVENING" : "GOOD NIGHT";
    }

    private String memoryShort() {
        long[] m = meminfo();
        int ram = m == null ? -1 : (int) Math.round(100.0 * (m[0] - m[1]) / m[0]);
        int disk = -1;
        try {
            StatFs fs = new StatFs(Environment.getDataDirectory().getPath());
            disk = (int) Math.round(100.0 * (fs.getTotalBytes() - fs.getAvailableBytes()) / fs.getTotalBytes());
        } catch (RuntimeException ignored) { }
        return "RAM " + ram + "%  ·  " + disk + "%";
    }

    /** A rounded glass pill with an icon and a word; white when selected. Returns its text. */
    private TextView addPill(int icon, String label, View.OnClickListener click) {
        int h = Math.round(H * 0.08f);
        LinearLayout b = new LinearLayout(this);
        b.setGravity(Gravity.CENTER_VERTICAL);
        b.setFocusable(true);
        b.setClickable(true);
        b.setContentDescription(label);
        b.setPadding(Math.round(h * 0.36f), 0, Math.round(h * 0.42f), 0);
        GradientDrawable rest = new GradientDrawable();
        rest.setCornerRadius(h / 2f);
        rest.setColor(darkInk ? 0x73FFFFFF : 0x59202226);
        rest.setStroke(Math.max(1, Math.round(H * 0.0015f)), darkInk ? 0x1F000000 : 0x4DFFFFFF);
        GradientDrawable focus = new GradientDrawable();
        focus.setCornerRadius(h / 2f);
        focus.setColor(focusBg());
        b.setBackground(rest);
        ImageView ic = new ImageView(this);
        ic.setImageResource(icon);
        ic.setImageTintList(ColorStateList.valueOf(ink(255)));
        int s = Math.round(h * 0.42f);
        LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(s, s);
        il.rightMargin = Math.round(h * 0.2f);
        b.addView(ic, il);
        TextView t = text(label, h * 0.3f, ink(255), true);
        t.setSingleLine(true);
        b.addView(t);
        b.setOnFocusChangeListener((v, focused) -> {
            v.setBackground(focused ? focus : rest);
            ic.setImageTintList(ColorStateList.valueOf(focused ? focusFg() : ink(255)));
            t.setTextColor(focused ? focusFg() : ink(255));
            v.animate().scaleX(focused ? 1.06f : 1f).scaleY(focused ? 1.06f : 1f).setDuration(150).start();
            if (focused) lastButton = v;
        });
        b.setOnClickListener(click);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, h);
        lp.leftMargin = Math.round(h * 0.25f);
        pillRow.addView(b, lp);
        buttons.add(b);
        return t;
    }

    private void wireRowKeys() {
        View.OnKeyListener tileKeys = (v, key, e) -> {
            if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
            int i = tiles.indexOf(v);
            if (key == KeyEvent.KEYCODE_DPAD_UP) {
                if (!buttons.isEmpty()) (lastButton != null ? lastButton : buttons.get(buttons.size() - 1)).requestFocus();
                return true;
            }
            if (key == KeyEvent.KEYCODE_DPAD_DOWN) return true;
            if (key == KeyEvent.KEYCODE_DPAD_LEFT && i == 0) return true;
            if (key == KeyEvent.KEYCODE_DPAD_RIGHT && i == tiles.size() - 1) return true;
            if (key == KeyEvent.KEYCODE_MENU) {
                v.performLongClick();
                return true;
            }
            return false;
        };
        for (View t : tiles) t.setOnKeyListener(tileKeys);
        View.OnKeyListener buttonKeys = (v, key, e) -> {
            if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
            int i = buttons.indexOf(v);
            if (key == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (!tiles.isEmpty()) (lastTile != null && tiles.contains(lastTile) ? lastTile : tiles.get(0)).requestFocus();
                return true;
            }
            if (key == KeyEvent.KEYCODE_DPAD_UP) return true;
            if (key == KeyEvent.KEYCODE_DPAD_LEFT && i == 0) return true;
            if (key == KeyEvent.KEYCODE_DPAD_RIGHT && i == buttons.size() - 1) return true;
            return false;
        };
        for (View b : buttons) b.setOnKeyListener(buttonKeys);
    }

    private void launch(App a) {
        PackageManager pm = getPackageManager();
        Intent i = a.leanback ? pm.getLeanbackLaunchIntentForPackage(a.pkg) : null;
        if (i == null) i = pm.getLaunchIntentForPackage(a.pkg);
        if (i == null) i = new Intent(Intent.ACTION_MAIN).setComponent(a.component);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        open(i);
    }

    private void open(Intent i) {
        try {
            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, "Not available on this TV", Toast.LENGTH_SHORT).show();
        }
    }

    /** The one gap around the panel and the pill (config "edge" in % of the screen height, 4.5 by default). */
    private int edge() {
        return Math.round(H * (float) Store.config().optDouble("edge", 4.5) / 100f);
    }

    // ------------------------------------------------------------------ pill (top right)

    private void buildPill() {
        if (row) {
            buildPills();
            return;
        }
        int h = Math.round(H * 0.076f);
        pill = new Glass.Frame(this, h / 2f, darkInk ? 0x8CFFFFFF : 0x4A9AA3B5);
        if ("tonal".equals(tileStyle)) recolor.add(() -> pill.setTint(Board.alpha(Board.tones(accentColor())[0], 0xC7)));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, h);
        lp.gravity = Gravity.TOP | (right ? Gravity.START : Gravity.END);
        lp.topMargin = edge();
        if (right) lp.leftMargin = edge();
        else lp.rightMargin = edge();
        root.addView(pill, lp);
        barePill = !"pill".equals(cfg.optString("bar_style", "bare"));
        if (barePill) pill.setBare(true);

        pillRow = new LinearLayout(this);
        pillRow.setGravity(Gravity.CENTER_VERTICAL);
        pillRow.setPadding(Math.round(h * (barePill ? 0.1f : 0.42f)), 0, Math.round(h * 0.16f), 0);
        pillRow.setClipChildren(false);
        pillRow.setClipToPadding(false);
        if (cfg.optBoolean("bar_clock", true) && "dot".equals(cfg.optString("clock_style", "text"))) {
            DotViews.Clock dc = new DotViews.Clock(this, h * 0.46f * pct("clock_scale", 100),
                    "12".equals(cfg.optString("clock", "24")), cfg.optBoolean("seconds", false));
            dc.setColor(clockColor());
            dotClocks.add(dc);
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-2, -2);
            tl.rightMargin = Math.round(h * 0.3f);
            pillRow.addView(dc, tl);
        } else if (cfg.optBoolean("bar_clock", true)) {
            TextClock time = new TextClock(this);
            String fmt = "12".equals(cfg.optString("clock", "24")) ? "h:mm" : "HH:mm";
            time.setFormat12Hour(fmt);
            time.setFormat24Hour(fmt);
            time.setTextColor(clockColor());
            if ("accent".equals(cfg.optString("clock_color", "white"))) accentText.add(time);
            time.setTextSize(TypedValue.COMPLEX_UNIT_PX, h * 0.46f * pct("clock_scale", 100));
            time.setTypeface(font(cfg.optString("clock_font", "medium")));
            time.setIncludeFontPadding(false);
            if (barePill && !darkInk) time.setShadowLayer(h * 0.12f, 0, h * 0.02f, 0x99000000);
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-2, -2);
            tl.rightMargin = Math.round(h * 0.26f);
            pillRow.addView(time, tl);
            if (cfg.optBoolean("seconds", false)) {
                tl.rightMargin = Math.round(h * 0.08f);
                secondsView = new Noir.Seconds(this, time, h * 0.32f * Math.max(0.8f, pct("clock_scale", 100) * 0.7f));
                secondsView.setColor(clockColor());
                secondsView.setTypeface(font(cfg.optString("clock_font", "medium")));
                LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-2, -1);
                sl.rightMargin = Math.round(h * 0.3f);
                pillRow.addView(secondsView, sl);
            }
        }

        if (cfg.optBoolean("bar_weather", true)) {
            weatherView = new TextView(this);
            weatherView.setTextColor(ink(0xE6));
            weatherView.setTextSize(TypedValue.COMPLEX_UNIT_PX, h * 0.3f);
            weatherView.setTypeface(font("medium"));
            weatherView.setSingleLine(true);
            weatherView.setIncludeFontPadding(false);
            if (barePill && !darkInk) weatherView.setShadowLayer(h * 0.12f, 0, h * 0.02f, 0x99000000);
            weatherView.setVisibility(View.GONE);
            LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(-2, -2);
            wl.rightMargin = Math.round(h * 0.3f);
            pillRow.addView(weatherView, wl);
        }

        int d = Math.round(h * 0.66f);
        for (String which : Store.strings(Store.config(), "buttons", "memory", "inputs", "settings")) {
            switch (which) {
                case "wifi": addButton(R.drawable.g_wifi, 0x33FFFFFF, d, "Wi-Fi", v -> open(new Intent(Settings.ACTION_WIFI_SETTINGS))); break;
                case "bluetooth": addButton(R.drawable.g_bluetooth, 0x33FFFFFF, d, "Bluetooth", v -> open(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS))); break;
                case "profile": addButton(R.drawable.g_person, 0x33FFFFFF, d, "Accounts", v -> open(new Intent(Settings.ACTION_SETTINGS))); break;
                case "memory": addButton(R.drawable.g_chip, 0x2EFFFFFF, d, "RAM and storage", this::showMemory); break;
                case "music": addButton(R.drawable.g_music, 0xFFFA2D48, d, "Music", v -> openMusic()); break;
                case "inputs": addButton(R.drawable.g_inputs, 0x2EFFFFFF, d, "Inputs", this::showInputs); break;
                case "settings": addButton(R.drawable.g_gear, 0x2EFFFFFF, d, "Settings", v -> open(new Intent(Settings.ACTION_SETTINGS))); break;
                default: break;
            }
        }
        pill.addView(pillRow, new FrameLayout.LayoutParams(-2, -1));
    }

    private void addButton(int icon, int fill, int d, String name, View.OnClickListener click) {
        FrameLayout b = new FrameLayout(this);
        b.setFocusable(true);
        b.setClickable(true);
        b.setContentDescription(name);
        GradientDrawable rest = new GradientDrawable();
        rest.setShape(GradientDrawable.OVAL);
        rest.setColor(darkInk && fill != 0xFFFA2D48 ? 0x1A000000 : fill);
        GradientDrawable focus = new GradientDrawable();
        focus.setShape(GradientDrawable.OVAL);
        focus.setColor(focusBg());
        if (barePill) {
            rest.setColor(Color.TRANSPARENT);
            b.setElevation(0f);
        }
        b.setBackground(rest);
        ImageView ic = new ImageView(this);
        ic.setImageResource(icon);
        int restInk = fill == 0xFFFA2D48 ? Color.WHITE : ink(255);
        ic.setImageTintList(ColorStateList.valueOf(restInk));
        int s = Math.round(d * 0.58f);
        FrameLayout.LayoutParams il = new FrameLayout.LayoutParams(s, s);
        il.gravity = Gravity.CENTER;
        b.addView(ic, il);
        b.setOnFocusChangeListener((v, focused) -> {
            v.setBackground(focused ? focus : rest);
            ic.setImageTintList(ColorStateList.valueOf(focused ? focusFg() : restInk));
            v.animate().scaleX(focused ? 1.12f : 1f).scaleY(focused ? 1.12f : 1f).setDuration(150).start();
            if (focused) lastButton = v;
        });
        b.setOnClickListener(click);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(d, d);
        lp.leftMargin = Math.round(d * 0.2f);
        pillRow.addView(b, lp);
        buttons.add(b);
    }

    private void openMusic() {
        String chosen = Store.config().optString("music_app", "");
        PackageManager pm = getPackageManager();
        for (String p : new String[]{chosen, "com.spotify.tv.android", "com.google.android.youtube.tvmusic",
                "com.jio.media.jiobeats", "com.google.android.youtube.tv"}) {
            if (p.isEmpty()) continue;
            Intent i = pm.getLeanbackLaunchIntentForPackage(p);
            if (i == null) i = pm.getLaunchIntentForPackage(p);
            if (i != null) {
                open(i);
                return;
            }
        }
        Toast.makeText(this, "No music app found", Toast.LENGTH_SHORT).show();
    }

    // ------------------------------------------------------------------ remote keys between dock and pill

    private void wireKeys() {
        if (row) {
            wireRowKeys();
            return;
        }
        View.OnKeyListener tileKeys = (v, key, e) -> {
            if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
            int i = tiles.indexOf(v);
            int toBar = right ? KeyEvent.KEYCODE_DPAD_LEFT : KeyEvent.KEYCODE_DPAD_RIGHT;
            int away = right ? KeyEvent.KEYCODE_DPAD_RIGHT : KeyEvent.KEYCODE_DPAD_LEFT;
            if (!buttons.isEmpty() && (key == toBar || (key == KeyEvent.KEYCODE_DPAD_UP && i == 0))) {
                (lastButton != null ? lastButton : buttons.get(right ? buttons.size() - 1 : 0)).requestFocus();
                return true;
            }
            if (key == away || key == toBar) return true;
            if (key == KeyEvent.KEYCODE_DPAD_UP && i == 0) return true;
            if (key == KeyEvent.KEYCODE_DPAD_DOWN && i == tiles.size() - 1) return true;
            if (key == KeyEvent.KEYCODE_MENU) {
                v.performLongClick();
                return true;
            }
            return false;
        };
        for (View t : tiles) t.setOnKeyListener(tileKeys);
        View.OnKeyListener buttonKeys = (v, key, e) -> {
            if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
            int i = buttons.indexOf(v);
            boolean toDock = right ? key == KeyEvent.KEYCODE_DPAD_RIGHT && i == buttons.size() - 1
                    : key == KeyEvent.KEYCODE_DPAD_LEFT && i == 0;
            if (toDock || key == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (!tiles.isEmpty()) (lastTile != null && tiles.contains(lastTile) ? lastTile : tiles.get(0)).requestFocus();
                return true;
            }
            if (key == KeyEvent.KEYCODE_DPAD_UP) return true;
            if (right ? key == KeyEvent.KEYCODE_DPAD_LEFT && i == 0
                    : key == KeyEvent.KEYCODE_DPAD_RIGHT && i == buttons.size() - 1) return true;
            return false;
        };
        for (View b : buttons) b.setOnKeyListener(buttonKeys);
    }

    // ------------------------------------------------------------------ glass menus (memory, inputs, app)

    interface Action { void run(); }

    private LinearLayout openMenu(View anchor, boolean underPill, float widthShare) {
        closeOverlay();
        int w = Math.round(W * widthShare);
        Glass.Frame panel = new Glass.Frame(this, H * 0.035f, 0x8C1E222B);
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        int p = Math.round(H * 0.012f);
        rows.setPadding(p, p, p, p);
        panel.addView(rows, new FrameLayout.LayoutParams(-1, -2));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, -2);
        if (underPill) {
            lp.gravity = Gravity.TOP | Gravity.END;
            lp.topMargin = pill.getBottom() + Math.round(H * 0.015f);
            lp.rightMargin = edge();
        } else {
            int[] at = new int[2];
            anchor.getLocationOnScreen(at);
            lp.leftMargin = at[0] + anchor.getWidth() + Math.round(W * 0.02f);
            lp.topMargin = Math.max(Math.round(H * 0.05f), Math.min(at[1], Math.round(H * 0.55f)));
        }
        root.addView(panel, lp);
        panel.setAlpha(0f);
        panel.setTranslationY(-H * 0.01f);
        panel.animate().alpha(1f).translationY(0f).setDuration(180).start();
        overlay = panel;
        overlayReturn = anchor;
        return rows;
    }

    private void closeOverlay() {
        if (overlay == null) return;
        View o = overlay;
        overlay = null;
        root.removeView(o);
        if (overlayReturn != null && overlayReturn.isAttachedToWindow()) overlayReturn.requestFocus();
        overlayReturn = null;
    }

    private TextView menuHeader(LinearLayout rows, String s) {
        TextView t = text(s, H * 0.028f, 0xCCFFFFFF, true);
        int p = Math.round(H * 0.014f);
        t.setPadding(p, p, p, p);
        rows.addView(t);
        return t;
    }

    private View menuRow(LinearLayout rows, Drawable icon, boolean tintIcon, String label, boolean closeMark, Action a) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        int ph = Math.round(H * 0.018f);
        r.setPadding(ph, 0, ph, 0);
        r.setFocusable(true);
        r.setClickable(true);
        GradientDrawable on = rounded(0xF2F5F5F7, H * 0.026f);
        ImageView ic = new ImageView(this);
        ic.setImageDrawable(icon);
        if (tintIcon) ic.setImageTintList(ColorStateList.valueOf(Color.WHITE));
        int s = Math.round(H * 0.042f);
        r.addView(ic, new LinearLayout.LayoutParams(s, s));
        TextView t = text(label, H * 0.03f, Color.WHITE, true);
        t.setSingleLine(true);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, -2, 1f);
        tl.leftMargin = Math.round(H * 0.018f);
        r.addView(t, tl);
        ImageView x = null;
        if (closeMark) {
            x = new ImageView(this);
            x.setImageResource(R.drawable.g_close);
            x.setImageTintList(ColorStateList.valueOf(Color.WHITE));
            int xs = Math.round(H * 0.03f);
            r.addView(x, new LinearLayout.LayoutParams(xs, xs));
        }
        final ImageView mark = x;
        r.setOnFocusChangeListener((v, f) -> {
            v.setBackground(f ? on : null);
            t.setTextColor(f ? 0xFF1C1C1E : Color.WHITE);
            if (tintIcon) ic.setImageTintList(ColorStateList.valueOf(f ? 0xFF1C1C1E : Color.WHITE));
            if (mark != null) mark.setImageTintList(ColorStateList.valueOf(f ? 0xFF1C1C1E : Color.WHITE));
        });
        r.setOnClickListener(v -> a.run());
        rows.addView(r, new LinearLayout.LayoutParams(-1, Math.round(H * 0.075f)));
        return r;
    }

    private void separator(LinearLayout rows) {
        View line = new View(this);
        line.setBackgroundColor(0x33FFFFFF);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, Math.max(1, Math.round(H * 0.0015f)));
        int m = Math.round(H * 0.01f);
        lp.setMargins(m * 2, m, m * 2, m);
        rows.addView(line, lp);
    }

    // ---- memory: RAM and storage, the apps running now, Close all (a real force-stop)

    static final class Running {
        String pkg, label;
        Drawable icon;
        long kb;
    }

    private void showMemory(View anchor) {
        LinearLayout rows = openMenu(anchor, true, 0.30f);
        final View panel = overlay;
        TextView head = menuHeader(rows, memoryLine());
        final List<Running> shown = new ArrayList<>();
        View closeAll = menuRow(rows, getDrawable(R.drawable.g_close), true, "Close all", false, () -> {
            if (shown.isEmpty()) return;
            List<String> pkgs = new ArrayList<>();
            for (Running r : shown) pkgs.add(r.pkg);
            stopApps(pkgs, () -> {
                if (overlay == panel) showMemory(anchor);
            });
        });
        separator(rows);
        TextView note = text("Checking what is running...", H * 0.024f, 0x99FFFFFF, false);
        int p = Math.round(H * 0.014f);
        note.setPadding(p, p, p, p);
        rows.addView(note);
        closeAll.requestFocus();
        bg.execute(() -> {
            List<Running> got;
            String why = null;
            try {
                got = running(() -> main.post(() -> note.setText("On the TV: tick 'Always allow' and press Allow")));
            } catch (java.io.IOException e) {
                got = null;
                why = e.getMessage();
            }
            final List<Running> list = got;
            final String problem = why;
            main.post(() -> {
                if (overlay != panel) return;
                if (list == null) {
                    note.setText("Can't reach the TV's debugging (" + problem + "). Network debugging must be on.");
                    return;
                }
                head.setText(memoryLine());
                if (list.isEmpty()) {
                    note.setText("Nothing else is running");
                    return;
                }
                rows.removeView(note);
                shown.addAll(list);
                for (Running r : list) {
                    final View[] self = new View[1];
                    self[0] = menuRow(rows, r.icon, false, r.label + "   " + (r.kb / 1024) + " MB", true, () -> {
                        int i = rows.indexOfChild(self[0]);
                        rows.removeView(self[0]);
                        shown.remove(r);
                        View next = rows.getChildAt(Math.min(i, rows.getChildCount() - 1));
                        (next != null && next.isFocusable() ? next : closeAll).requestFocus();
                        stopApps(Collections.singletonList(r.pkg), () -> {
                            if (overlay == panel) head.setText(memoryLine());
                        });
                    });
                }
            });
        });
    }

    /** Force-stops the apps (the TV's own adb), then tells how much RAM came back. */
    private void stopApps(List<String> pkgs, Runnable after) {
        long before = availMb();
        bg.execute(() -> {
            StringBuilder cmd = new StringBuilder();
            for (String p : pkgs) if (!kept(p)) cmd.append("am force-stop ").append(p).append("; ");
            String problem = null;
            try {
                if (cmd.length() > 0) LocalAdb.shell(this, cmd.toString() + "true", null);
            } catch (java.io.IOException e) {
                problem = e.getMessage();
                for (String p : pkgs) kill(p);
            }
            try {
                Thread.sleep(700);
            } catch (InterruptedException ignored) { }
            final String why = problem;
            main.post(() -> {
                long freed = Math.max(0, availMb() - before);
                Toast.makeText(this, why == null ? "Closed. Freed " + freed + " MB" : "Could not force-stop: " + why,
                        Toast.LENGTH_SHORT).show();
                if (after != null) after.run();
            });
        });
    }

    /** Apps with a launcher entry that have a process running now, biggest first. */
    private List<Running> running(AdbLink.Prompt prompt) throws java.io.IOException {
        String out = LocalAdb.shell(this, "ps -A -o RSS,NAME", prompt);
        Map<String, Long> kb = new HashMap<>();
        for (String line : out.split("\n")) {
            String[] t = line.trim().split("\\s+");
            if (t.length < 2) continue;
            try {
                long k = Long.parseLong(t[0]);
                String name = t[1];
                int colon = name.indexOf(':');
                String pkg = colon > 0 ? name.substring(0, colon) : name;
                Long had = kb.get(pkg);
                kb.put(pkg, had == null ? k : had + k);
            } catch (NumberFormatException ignored) { }
        }
        PackageManager pm = getPackageManager();
        List<Running> list = new ArrayList<>();
        for (Map.Entry<String, Long> e : kb.entrySet()) {
            String pkg = e.getKey();
            if (kept(pkg)) continue;
            Intent li = pm.getLeanbackLaunchIntentForPackage(pkg);
            if (li == null) li = pm.getLaunchIntentForPackage(pkg);
            if (li == null) continue;
            try {
                Running r = new Running();
                r.pkg = pkg;
                r.label = String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)));
                r.icon = pm.getApplicationIcon(pkg);
                r.kb = e.getValue();
                list.add(r);
            } catch (PackageManager.NameNotFoundException ignored) { }
        }
        list.sort((a, b) -> Long.compare(b.kb, a.kb));
        return list;
    }

    private boolean kept(String pkg) {
        if (pkg.equals(getPackageName()) || Arrays.asList(NEVER_LISTED).contains(pkg)) return true;
        for (String k : KEEP) if (pkg.equals(k) || pkg.startsWith(k + ".") || pkg.startsWith(k)) return true;
        return false;
    }

    private String memoryLine() {
        long[] m = meminfo();
        int ram = m == null ? -1 : (int) Math.round(100.0 * (m[0] - m[1]) / m[0]);
        int disk = -1;
        try {
            StatFs fs = new StatFs(Environment.getDataDirectory().getPath());
            disk = (int) Math.round(100.0 * (fs.getTotalBytes() - fs.getAvailableBytes()) / fs.getTotalBytes());
        } catch (RuntimeException ignored) { }
        return "RAM " + ram + "%  ·  Storage " + disk + "%";
    }

    private static long[] meminfo() {
        long total = -1, avail = -1;
        try (BufferedReader r = new BufferedReader(new FileReader("/proc/meminfo"))) {
            String line;
            while ((line = r.readLine()) != null) {
                String[] t = line.split("\\s+");
                if (t.length < 2) continue;
                if (line.startsWith("MemTotal:")) total = Long.parseLong(t[1]);
                else if (line.startsWith("MemAvailable:")) avail = Long.parseLong(t[1]);
            }
        } catch (Exception e) {
            return null;
        }
        return total > 0 && avail >= 0 ? new long[]{total, avail} : null;
    }

    private static long availMb() {
        long[] m = meminfo();
        return m == null ? -1 : m[1] / 1024;
    }

    private static final String[] KEEP = {"com.google.android.gms", "com.google.android.gsf", "com.google.android.inputmethod",
            "com.android.bluetooth", "com.mi.bluetooth", "com.android.systemui", "com.android.tv", "com.droidlogic",
            "com.sriramteja.glasshome", "com.android.providers"};

    private void kill(String pkg) {
        for (String k : KEEP) if (pkg.startsWith(k)) return;
        ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        try {
            if (am != null) am.killBackgroundProcesses(pkg);
        } catch (SecurityException ignored) { }
    }

    private void killAll() {
        for (ResolveInfo ri : getPackageManager().queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0)) {
            if (ri.activityInfo != null) kill(ri.activityInfo.packageName);
        }
        for (ResolveInfo ri : getPackageManager().queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)) {
            if (ri.activityInfo != null) kill(ri.activityInfo.packageName);
        }
    }

    private boolean usageAllowed() {
        AppOpsManager ops = (AppOpsManager) getSystemService(APP_OPS_SERVICE);
        if (ops == null) return false;
        @SuppressWarnings("deprecation")
        int mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    private List<App> recentApps() {
        List<App> out = new ArrayList<>();
        if (!usageAllowed()) return out;
        UsageStatsManager usm = (UsageStatsManager) getSystemService(USAGE_STATS_SERVICE);
        if (usm == null) return out;
        long now = System.currentTimeMillis();
        Map<String, Long> last = new HashMap<>();
        try {
            UsageEvents ev = usm.queryEvents(now - 6L * 3600_000L, now);
            UsageEvents.Event e = new UsageEvents.Event();
            while (ev.hasNextEvent()) {
                ev.getNextEvent(e);
                @SuppressWarnings("deprecation")
                boolean fg = e.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND;
                if (fg) last.put(e.getPackageName(), e.getTimeStamp());
            }
        } catch (RuntimeException e) {
            return out;
        }
        List<Map.Entry<String, Long>> sorted = new ArrayList<>(last.entrySet());
        sorted.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        PackageManager pm = getPackageManager();
        for (Map.Entry<String, Long> en : sorted) {
            String p = en.getKey();
            boolean keep = false;
            for (String k : KEEP) if (p.startsWith(k)) keep = true;
            if (keep || Arrays.asList(NEVER_LISTED).contains(p)) continue;
            Intent li = pm.getLeanbackLaunchIntentForPackage(p);
            if (li == null) li = pm.getLaunchIntentForPackage(p);
            if (li == null) continue;
            try {
                App a = new App();
                a.pkg = p;
                a.label = String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(p, 0)));
                a.icon = pm.getApplicationIcon(p);
                out.add(a);
            } catch (PackageManager.NameNotFoundException ignored) { }
            if (out.size() >= 7) break;
        }
        return out;
    }

    // ---- inputs: HDMI and the other sources

    private boolean hasInputs() {
        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        if (tim == null) return false;
        for (TvInputInfo i : tim.getTvInputList()) if (i.isPassthroughInput()) return true;
        return false;
    }

    private void showInputs(View anchor) {
        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        if (tim == null) return;
        LinearLayout rows = openMenu(anchor, buttons.contains(anchor), 0.24f);
        menuHeader(rows, "Inputs");
        List<TvInputInfo> list = new ArrayList<>(tim.getTvInputList());
        list.sort((a, b) -> String.valueOf(a.loadLabel(this)).compareToIgnoreCase(String.valueOf(b.loadLabel(this))));
        View first = null;
        for (TvInputInfo in : list) {
            if (!in.isPassthroughInput() || in.isHidden(this)) continue;
            String label = String.valueOf(in.loadLabel(this));
            CharSequence custom = in.loadCustomLabel(this);
            if (custom != null && custom.length() > 0 && !custom.toString().equals(label)) label = custom + " · " + label;
            int st = tim.getInputState(in.getId());
            if (in.getType() == TvInputInfo.TYPE_HDMI && st == TvInputManager.INPUT_STATE_CONNECTED) label += "  •";
            String id = in.getId();
            View r = menuRow(rows, getDrawable(R.drawable.g_input), true, label, false, () -> {
                closeOverlay();
                open(new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(id)));
            });
            if (first == null) first = r;
        }
        if (first != null) first.requestFocus();
    }

    // ---- app menu: move, hide, app info

    private void appMenu(App a, View tile) {
        LinearLayout rows = openMenu(tile, false, 0.22f);
        menuHeader(rows, a.label);
        Drawable none = new GradientDrawable();
        View first = menuRow(rows, none, false, "Move up", false, () -> { move(a.pkg, -1); closeOverlay(); });
        menuRow(rows, none, false, "Move down", false, () -> { move(a.pkg, 1); closeOverlay(); });
        menuRow(rows, none, false, "Move to the top", false, () -> { move(a.pkg, -999); closeOverlay(); });
        menuRow(rows, none, false, "Hide", false, () -> {
            List<String> h = csv(prefs.getString("hidden", ""));
            h.add(a.pkg);
            prefs.edit().putString("hidden", String.join(",", h)).apply();
            closeOverlay();
            loadApps();
        });
        if (!csv(prefs.getString("hidden", "")).isEmpty()) {
            menuRow(rows, none, false, "Show hidden apps", false, () -> {
                prefs.edit().remove("hidden").apply();
                closeOverlay();
                loadApps();
            });
        }
        menuRow(rows, none, false, "App info", false, () -> {
            closeOverlay();
            open(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + a.pkg)));
        });
        first.requestFocus();
    }

    private void move(String pkg, int by) {
        List<String> order = new ArrayList<>();
        for (View t : tiles) {
            Object tag = t.getTag();
            if (tag instanceof String && !((String) tag).startsWith("::")) order.add((String) tag);
        }
        int i = order.indexOf(pkg);
        if (i < 0) return;
        order.remove(i);
        int to = by == -999 ? 0 : Math.max(0, Math.min(order.size(), i + by));
        order.add(to, pkg);
        prefs.edit().putString("order", String.join(",", order)).apply();
        loadApps();
    }

    // ------------------------------------------------------------------ helpers

    private static List<String> csv(String s) {
        List<String> out = new ArrayList<>();
        for (String x : s.split(",")) if (!x.trim().isEmpty()) out.add(x.trim());
        return out;
    }

    private TextView text(String s, float px, int color, boolean medium) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, px);
        t.setTextColor(color);
        t.setTypeface(Typeface.create(medium ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        t.setIncludeFontPadding(false);
        return t;
    }

    private static GradientDrawable rounded(int color, float r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(r);
        return g;
    }

    @SuppressWarnings("unused")
    private static ViewGroup.LayoutParams match() {
        return new ViewGroup.LayoutParams(-1, -1);
    }
}
