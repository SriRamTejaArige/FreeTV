package com.sriramteja.freetv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * FreeTV: the phone side of Glass Home. A liquid-glass app that sets up any Android TV, mirrors and
 * remote-controls it, and designs every detail of its home screen, icon packs, wallpapers and
 * screensavers. Talks to the TV over adb on the home Wi-Fi; nothing goes to the internet.
 */
public class MainActivity extends Activity {

    private static final int BG = 0xFF06060C, CARD = 0x1FFFFFFF, LINE = 0x33FFFFFF, TEXT = 0xFFFFFFFF,
            DIM = 0xA6FFFFFF, ACCENT = 0xFFA78BFA, GOOD = 0xFF34D399, BAD = 0xFFFB7185;
    private static final int PICK = 41, PICK_APK = 42;
    private static final String[] TABS = {"Remote", "Themes", "Design", "Walls", "Saver", "More"};
    private static final int[] TAB_ICONS = {R.drawable.f_remote, R.drawable.f_spark, R.drawable.f_palette,
            R.drawable.f_image, R.drawable.f_moon, R.drawable.f_grid};
    private static final String[] THEME_NAMES = Themes.NAMES;
    private static final String[] THEME_TEXT = {
            "Your first clean TV look: big clock and date top left, Inputs and Settings pills, the apps in one row of banners with the selected app's name above.",
            "A frosted glass panel with the apps' banners and a glass pill for the clock.",
            "App icons floating on the wallpaper, the name beside the selected one. Clean and colourful.",
            "Dark wallpapers, white logo icons, a soft spotlight that follows you, rolling seconds.",
            "Logos in the wallpaper's own colour with a neon halo on the selected app, thin accent clock.",
            "Small white logos, no names, calm dimmed wallpaper, thin clock. Nothing extra.",
            "Big pastel cushions coloured from your wallpaper, each with the app's icon and name. Soft, friendly and bold.",
            "Your wallpaper edge to edge: a huge thin clock, white logos floating along the bottom with a glow.",
            "Bright and airy by day, dark Noir by night. It switches by itself at the hours you choose.",
            "Soft UI: one calm light colour, pillowy tiles that rise when chosen, dark text and a calendar.",
            "Black, white logos and dotted lettering for the clock and app names, with one red dot.",
            "Tones taken from your wallpaper: pill-shaped tiles in a tonal panel, a friendly clock, date and weather at a glance.",
            "Only words: your apps as a list of names. The chosen one slides out with an accent bar."};
    private static final String[] THEME_TAG = {"Row of banners", "Glass panel", "Floating icons", "Dark spotlight",
            "Neon halo", "Quiet logos", "Pastel cushions", "Edge to edge", "Changes with the sun", "Light and soft",
            "Dotted display", "Tonal pills", "Just names"};
    private static final String[] BG_SWATCHES = {"#000000", "#0B0D12", "#14161F", "#1B2A44", "#2A1B3D", "#123D3A",
            "#E3E8EF", "#F4F1EA", "#D9E9F8", "#FCEFE2", "#E8F5E9", "#FDE2E4"};
    private static final String[] DEFAULT_DOCK = {"com.google.android.youtube.tv", "com.netflix.ninja",
            "in.startv.hotstar", "com.amazon.amazonvideo.livingroom", "org.videolan.vlc", "com.android.vending"};
    private static final String[] SWATCHES = {"#FFFFFF", "#39E6FF", "#22B8F0", "#3E63DD", "#8E4EC6", "#E54DD0",
            "#E5484D", "#FF7A00", "#FFC53D", "#2BB673", "#A3E635", "#F2F4F7"};

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final ExecutorService puller = Executors.newSingleThreadExecutor();
    /** Settings go out on their own line, so they never wait behind icon and picture downloads. */
    private final ExecutorService sender = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private AdbLink link;
    private Tv tv;
    private volatile boolean connected;
    private JSONObject raw = new JSONObject();
    private File cache, draftFile;

    private final List<Tv.App> homeApps = new ArrayList<>();
    private final Map<String, Bitmap> icons = new HashMap<>(), banners = new HashMap<>(), thumbs = new HashMap<>();
    private final List<Tv.Wall> walls = new ArrayList<>();
    private String currentWall = "";
    private volatile String homeNow = "";
    private final Map<String, Bitmap[]> logos = new HashMap<>();

    private LinearLayout page;
    private ScrollView pageScroll;
    private Backdrop backdrop;
    private TextView pageTitle, chipText, snackText;
    private View chipDot, snackDot, navPill;
    private GlassCard snack;
    private final List<View> tabs = new ArrayList<>();
    private int currentTab, contentW;
    private String sub = "", designSeg = "layout";
    /** The theme opened from the Themes grid (null: the grid). */
    private String themeOpen;
    /** The app being sent to the TV (Install apps). */
    private File apkFile;
    private String apkName = "", apkLabel = "", apkPkg = "", apkVersion = "", installMsg = "";
    private android.graphics.drawable.Drawable apkIcon;
    private boolean installing, apkBundle;
    private Boolean installOk;
    private float installK = -1f;
    private View installFill;
    private TextView installText;
    private int setupStep;
    private final List<String> found = new ArrayList<>();
    private final Runnable hideSnack = () -> {
        if (snack != null) snack.animate().alpha(0f).translationY(Fx.px(16)).setDuration(250).start();
    };
    private float dp;
    private final List<Runnable> onChange = new ArrayList<>();
    private boolean dirty, resumed;
    private final Runnable autoSend = () -> sendNow(true);

    private ImageView mirror;
    private volatile boolean mirrorOn;
    private Thread mirrorThread;

    // ------------------------------------------------------------------ setup

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Fx.init(this);
        dp = getResources().getDisplayMetrics().density;
        prefs = getSharedPreferences("freetv", MODE_PRIVATE);
        cache = new File(getFilesDir(), "tv");
        //noinspection ResultOfMethodCallIgnored
        cache.mkdirs();
        draftFile = new File(getFilesDir(), "config.json");
        loadDraft();
        try {
            link = new AdbLink(getFilesDir());
            tv = new Tv(link);
        } catch (Exception e) {
            toast("Could not make this phone's key: " + e.getMessage());
        }
        getWindow().setStatusBarColor(0x00000000);
        getWindow().setNavigationBarColor(0x00000000);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        int screenW = getResources().getDisplayMetrics().widthPixels;
        contentW = Math.min(screenW, px(560));

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BG);
        backdrop = new Backdrop(this);
        root.addView(backdrop, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(contentW, -1);
        cl.gravity = Gravity.CENTER_HORIZONTAL;
        root.addView(column, cl);

        // header: FreeTV in the brand gradient, the page name, the TV's status
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(px(20), statusBarHeight() + px(14), px(16), px(8));
        LinearLayout names = new LinearLayout(this);
        names.setOrientation(LinearLayout.VERTICAL);
        TextView brand = text("FreeTV", 30, TEXT, true);
        brand.setTypeface(Fx.font(800));
        brand.setLetterSpacing(-0.02f);
        Fx.gradientText(brand, 0xFFC4B5FD, 0xFF67E8F9);
        names.addView(brand);
        pageTitle = text("", 13, DIM, false);
        pageTitle.setLetterSpacing(0.12f);
        pageTitle.setAllCaps(true);
        names.addView(pageTitle);
        head.addView(names, new LinearLayout.LayoutParams(0, -2, 1f));
        GlassCard chip = new GlassCard(this, px(18), 0x1FFFFFFF);
        chip.setOrientation(LinearLayout.HORIZONTAL);
        chip.setGravity(Gravity.CENTER_VERTICAL);
        chip.setPadding(px(12), px(8), px(14), px(8));
        chipDot = new View(this);
        chipDot.setBackground(Fx.pill(BAD, px(5), 0, 0));
        chip.addView(chipDot, new LinearLayout.LayoutParams(px(9), px(9)));
        chipText = text("Not connected", 13, TEXT, true);
        chipText.setPadding(px(8), 0, 0, 0);
        chip.addView(chipText);
        chip.setOnClickListener(v -> {
            if (!connected) {
                sub = "system";
                showTab(5);
            } else if (dirty) {
                sendNow(true);
            } else {
                status("Connected. Every change goes to the TV straight away.", GOOD);
            }
        });
        Fx.press(chip);
        head.addView(chip);
        column.addView(head);

        pageScroll = new GlassCard.Scroll(this);
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(px(14), px(4), px(14), px(130));
        pageScroll.addView(page);
        column.addView(pageScroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        // floating snackbar
        snack = new GlassCard(this, px(22), 0x33000000);
        snack.setOrientation(LinearLayout.HORIZONTAL);
        snack.setGravity(Gravity.CENTER_VERTICAL);
        snack.setPadding(px(16), px(12), px(18), px(12));
        snackDot = new View(this);
        snack.addView(snackDot, new LinearLayout.LayoutParams(px(8), px(8)));
        snackText = text("", 14, TEXT, false);
        snackText.setPadding(px(10), 0, 0, 0);
        snack.addView(snackText, new LinearLayout.LayoutParams(0, -2, 1f));
        snack.setAlpha(0f);
        snack.setOnClickListener(v -> hideSnack.run());
        FrameLayout.LayoutParams sl = new FrameLayout.LayoutParams(contentW - px(32), -2);
        sl.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        sl.bottomMargin = navBarHeight() + px(104);
        root.addView(snack, sl);

        root.addView(buildNav(), navParams());
        setContentView(root);
        loadCache();
        styleSend();

        int startTab = getIntent().getIntExtra("tab", -1);
        themeOpen = getIntent().getStringExtra("theme");
        Uri shared = sharedApk(getIntent());
        if (shared != null) {
            getIntent().putExtra("sub", "install");
            prepareApk(shared);
        }
        String startSub = getIntent().getStringExtra("sub");
        if (startSub != null) {
            sub = startSub;
            setupStep = getIntent().getIntExtra("step", 0);
            designSeg = getIntent().getStringExtra("seg") != null ? getIntent().getStringExtra("seg") : designSeg;
            showTab(5);
        } else if (getIntent().getStringExtra("seg") != null) {
            designSeg = getIntent().getStringExtra("seg");
            showTab(2);
        } else if (!prefs.getBoolean("welcomed", false) && startTab < 0) {
            sub = "setup";
            setupStep = 0;
            showTab(5);
        } else {
            showTab(startTab >= 0 ? startTab : prefs.getInt("tab", 0));
        }
        String ip = prefs.getString("ip", "");
        if (!ip.isEmpty() && !getIntent().getBooleanExtra("noconnect", false)) connect(ip);
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        if (currentTab == 0) startMirror();
    }

    @Override
    protected void onPause() {
        resumed = false;
        stopMirror();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        worker.shutdownNow();
        puller.shutdownNow();
        sender.shutdownNow();
        if (link != null) new Thread(link::close).start();
        super.onDestroy();
    }

    private int statusBarHeight() {
        int id = getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id > 0 ? getResources().getDimensionPixelSize(id) : px(24);
    }

    private int navBarHeight() {
        int id = getResources().getIdentifier("navigation_bar_height", "dimen", "android");
        return id > 0 ? getResources().getDimensionPixelSize(id) : 0;
    }

    private FrameLayout.LayoutParams navParams() {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Math.min(contentW - px(24), px(440)), px(70));
        lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        lp.bottomMargin = navBarHeight() + px(16);
        return lp;
    }

    /** The floating glass tab bar; a glowing pill slides to the chosen tab. */
    private View buildNav() {
        GlassCard bar = new GlassCard(this, px(35), 0x2E101018);
        bar.setElevation(px(10));
        FrameLayout inner = new FrameLayout(this);
        bar.addView(inner, new LinearLayout.LayoutParams(-1, -1));
        int w = Math.min(contentW - px(24), px(440));
        int itemW = (w - px(12)) / TABS.length;
        navPill = new View(this);
        navPill.setBackground(Fx.brand(px(26)));
        navPill.setAlpha(0.9f);
        FrameLayout.LayoutParams pl = new FrameLayout.LayoutParams(itemW - px(8), px(54));
        pl.gravity = Gravity.CENTER_VERTICAL;
        pl.leftMargin = px(4);
        inner.addView(navPill, pl);
        LinearLayout row = new LinearLayout(this);
        row.setPadding(px(6), 0, px(6), 0);
        inner.addView(row, new FrameLayout.LayoutParams(-1, -1));
        for (int i = 0; i < TABS.length; i++) {
            final int index = i;
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            ImageView ic = new ImageView(this);
            ic.setImageResource(TAB_ICONS[i]);
            item.addView(ic, new LinearLayout.LayoutParams(px(22), px(22)));
            TextView t = text(TABS[i], 10.5f, TEXT, true);
            t.setPadding(0, px(3), 0, 0);
            item.addView(t);
            item.setOnClickListener(v -> {
                sub = "";
                showTab(index);
            });
            Fx.press(item);
            row.addView(item, new LinearLayout.LayoutParams(0, -1, 1f));
            tabs.add(item);
        }
        return bar;
    }

    private void styleNav() {
        for (int k = 0; k < tabs.size(); k++) {
            View item = tabs.get(k);
            item.setAlpha(k == currentTab ? 1f : 0.62f);
        }
        if (navPill != null && !tabs.isEmpty()) {
            View target = tabs.get(currentTab);
            target.post(() -> navPill.animate().translationX(target.getLeft()).setDuration(380)
                    .setInterpolator(new android.view.animation.OvershootInterpolator(1.4f)).start());
        }
    }

    private void showTab(int i) {
        if (i < 0 || i >= TABS.length) i = 0;
        currentTab = i;
        prefs.edit().putInt("tab", i).apply();
        styleNav();
        stopMirror();
        onChange.clear();
        page.removeAllViews();
        String title = TABS[i];
        if (i == 5 && !sub.isEmpty()) {
            switch (sub) {
                case "system": title = "TV and connection"; buildSubHeader(title); buildSystem(); break;
                case "apps": title = "Apps on the TV"; buildSubHeader(title); buildApps(); break;
                case "install": title = "Install apps on the TV"; buildSubHeader(title); buildInstall(); break;
                case "presets": title = "Presets and backup"; buildSubHeader(title); buildPresets(); break;
                case "about": title = "About"; buildSubHeader(title); buildAbout(); break;
                default: title = "Set up a TV"; buildSetup(); break;
            }
        } else {
            switch (i) {
                case 0: buildLive(); break;
                case 1: buildThemes(); break;
                case 2: buildDesign(); break;
                case 3: buildWalls(); break;
                case 4: buildSaver(); break;
                default: buildMore(); break;
            }
        }
        pageTitle.setText(title);
        enter();
        if (i == 0 && resumed) startMirror();
    }

    /** New page: the cards float up into place one after another. */
    private void enter() {
        for (int k = 0; k < page.getChildCount(); k++) {
            View v = page.getChildAt(k);
            v.setAlpha(0f);
            v.setTranslationY(px(28));
            v.animate().alpha(1f).translationY(0f).setStartDelay(Math.min(8, k) * 45L).setDuration(420)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator(2f))
                    .withEndAction(GlassCard::refreshAll).start();
        }
        pageScroll.scrollTo(0, 0);
    }

    private void refreshTab() {
        int y = pageScroll.getScrollY();
        showTab(currentTab);
        for (int k = 0; k < page.getChildCount(); k++) {
            page.getChildAt(k).animate().cancel();
            page.getChildAt(k).setAlpha(1f);
            page.getChildAt(k).setTranslationY(0f);
        }
        pageScroll.post(() -> pageScroll.scrollTo(0, y));
    }

    @Override
    public void onBackPressed() {
        if (currentTab == 5 && "setup".equals(sub) && setupStep > 0) {
            setupStep--;
            showTab(5);
        } else if (currentTab == 5 && !sub.isEmpty()) {
            sub = "";
            showTab(5);
        } else if (currentTab != 0) {
            showTab(0);
        } else {
            super.onBackPressed();
        }
    }

    private void buildSubHeader(String title) {
        LinearLayout r = row();
        r.setGravity(Gravity.CENTER_VERTICAL);
        ImageView back = new ImageView(this);
        back.setImageResource(R.drawable.f_back);
        back.setPadding(px(10), px(10), px(10), px(10));
        back.setBackground(Fx.pill(0x1FFFFFFF, px(22), LINE, 1));
        back.setOnClickListener(v -> {
            sub = "";
            showTab(5);
        });
        Fx.press(back);
        r.addView(back, new LinearLayout.LayoutParams(px(44), px(44)));
        TextView t = text(title, 22, TEXT, true);
        t.setTypeface(Fx.font(700));
        t.setPadding(px(12), 0, 0, 0);
        r.addView(t);
        page.addView(r);
    }

    // ------------------------------------------------------------------ background work

    interface Job { String run() throws Exception; }

    private void work(String busy, Job job, Runnable after) {
        if (!connected) {
            status("Connect to the TV first (More > TV and connection).", BAD);
            return;
        }
        if (busy != null) status(busy, DIM);
        worker.execute(() -> {
            String result;
            boolean ok = true;
            try {
                result = job.run();
            } catch (Exception e) {
                ok = false;
                result = e.getMessage() == null ? e.toString() : e.getMessage();
                if (link != null && !link.isConnected()) connected = false;
            }
            final String r = result;
            final boolean good = ok;
            main.post(() -> {
                if (r != null) status(r, good ? GOOD : BAD);
                styleSend();
                if (after != null) after.run();
            });
        });
    }

    private void status(String s, int color) {
        if (snack == null) return;
        snackText.setText(s);
        snackDot.setBackground(Fx.pill(color == DIM ? Fx.CYAN : color, px(4), 0, 0));
        snack.animate().cancel();
        if (snack.getAlpha() < 0.5f) snack.setTranslationY(Fx.px(16));
        snack.animate().alpha(1f).translationY(0f).setDuration(260)
                .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
        main.removeCallbacks(hideSnack);
        main.postDelayed(hideSnack, color == BAD ? 6000 : 3500);
    }

    // ------------------------------------------------------------------ connection and TV data

    private void connect(String ip) {
        connect(ip, null);
    }

    /** Connects (the TV asks Allow the first time); after = run on the main thread once connected. */
    private void connect(String ip, Runnable after) {
        if (tv == null) return;
        prefs.edit().putString("ip", ip).apply();
        String host = ip.contains(":") ? ip.substring(0, ip.indexOf(':')) : ip;
        int port = 5555;
        if (ip.contains(":")) {
            try { port = Integer.parseInt(ip.substring(ip.indexOf(':') + 1)); } catch (NumberFormatException ignored) { }
        }
        final int p = port;
        status("Connecting to " + host + "...", DIM);
        worker.execute(() -> {
            try {
                link.connect(host, p, () -> main.post(() ->
                        status("On the TV: press Allow (tick 'Always allow from this computer').", ACCENT)));
                connected = true;
                Tv.Status s = tv.status();
                homeNow = s.home;
                prefs.edit().putString("tv_name", s.model).apply();
                main.post(() -> {
                    status("Connected to " + (s.model.isEmpty() ? host : s.model) + ". Getting the TV's apps and pictures...", GOOD);
                    styleSend();
                    if (apkFile != null && !installing && installOk == null) startInstall();
                    if (after != null) after.run();
                    else if (currentTab == 0 || (currentTab == 5 && "system".equals(sub))) refreshTab();
                });
                if (s.glassInstalled) syncFromTv(!dirty && !prefs.getBoolean("have_draft", false));
            } catch (Exception e) {
                connected = false;
                String m = e.getMessage() == null ? e.toString() : e.getMessage();
                main.post(() -> {
                    status("Not connected: " + m, BAD);
                    styleSend();
                });
            }
        });
    }

    /** Pulls the TV's settings (optionally), apps, icons, wallpapers and thumbnails into the phone's cache. */
    private void syncFromTv(boolean takeConfig) {
        worker.execute(() -> {
            try {
                if (takeConfig) {
                    JSONObject c = tv.readConfig();
                    if (c.length() > 0) {
                        main.post(() -> {
                            raw = c;
                            saveDraft();
                        });
                    }
                }
                List<Tv.App> apps = tv.homeApps();
                JSONArray a = new JSONArray();
                for (Tv.App x : apps) a.put(new JSONObject().put("pkg", x.pkg).put("label", x.label).put("tv", x.tv));
                write(new File(cache, "apps.json"), a.toString().getBytes(StandardCharsets.UTF_8));
                File iconDir = new File(cache, "icons"), bannerDir = new File(cache, "banners");
                //noinspection ResultOfMethodCallIgnored
                iconDir.mkdirs();
                //noinspection ResultOfMethodCallIgnored
                bannerDir.mkdirs();
                int i = 0;
                for (Tv.App x : apps) {
                    i++;
                    File f = new File(iconDir, x.pkg + ".png");
                    if (!f.isFile()) {
                        byte[] b = tv.icon(x.pkg);
                        if (b.length > 100) write(f, b);
                    }
                    File bf = new File(bannerDir, x.pkg + ".png");
                    if (!bf.isFile() && !new File(bannerDir, x.pkg + ".none").isFile()) {
                        byte[] b = tv.export("banners/" + x.pkg + ".png");
                        if (b.length > 100) write(bf, b);
                        else write(new File(bannerDir, x.pkg + ".none"), new byte[0]);
                    }
                    final int n = i;
                    if (i % 5 == 0) main.post(() -> status("Getting app icons " + n + "/" + apps.size() + "...", DIM));
                }
                List<Tv.Wall> ws = tv.walls();
                JSONArray wj = new JSONArray();
                for (Tv.Wall w : ws) wj.put(new JSONObject().put("name", w.name).put("bytes", w.bytes).put("brightness", w.brightness));
                write(new File(cache, "walls.json"), wj.toString().getBytes(StandardCharsets.UTF_8));
                File thumbDir = new File(cache, "thumbs");
                //noinspection ResultOfMethodCallIgnored
                thumbDir.mkdirs();
                i = 0;
                for (Tv.Wall w : ws) {
                    i++;
                    File f = new File(thumbDir, w.name + ".jpg");
                    if (!f.isFile()) {
                        byte[] b = tv.thumb(w.name);
                        if (b.length < 100) {
                            // no thumbnail yet on the TV: make one from the picture itself
                            byte[] full = tv.wallpaper(w.name);
                            Bitmap bm = decodeSmall(full, 320);
                            if (bm != null) {
                                java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
                                bm.compress(Bitmap.CompressFormat.JPEG, 80, o);
                                b = o.toByteArray();
                            }
                        }
                        if (b.length > 100) write(f, b);
                    }
                    final int n = i;
                    if (i % 4 == 0) main.post(() -> status("Getting wallpapers " + n + "/" + ws.size() + "...", DIM));
                }
                String cur = new String(tv.export("current.txt"), StandardCharsets.UTF_8).trim();
                write(new File(cache, "current.txt"), cur.getBytes(StandardCharsets.UTF_8));
                main.post(() -> {
                    loadCache();
                    status("Ready. " + homeApps.size() + " apps and " + walls.size() + " wallpapers from the TV.", GOOD);
                    if (!(currentTab == 5 && "setup".equals(sub))) refreshTab();
                });
            } catch (Exception e) {
                String m = e.getMessage() == null ? e.toString() : e.getMessage();
                main.post(() -> status("Could not read everything from the TV: " + m, BAD));
            }
        });
    }

    private static Bitmap decodeSmall(byte[] data, int width) {
        if (data == null || data.length < 100) return null;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, o);
        int s = 1;
        while (o.outWidth / (s * 2) >= width) s *= 2;
        BitmapFactory.Options d = new BitmapFactory.Options();
        d.inSampleSize = s;
        return BitmapFactory.decodeByteArray(data, 0, data.length, d);
    }

    private static void write(File f, byte[] data) throws IOException {
        File tmp = new File(f.getPath() + ".tmp");
        try (OutputStream o = new FileOutputStream(tmp)) {
            o.write(data);
        }
        //noinspection ResultOfMethodCallIgnored
        tmp.renameTo(f);
    }

    private static String read(File f) {
        try {
            return new String(AdbLink.readAll(f), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    private void loadCache() {
        homeApps.clear();
        try {
            JSONArray a = new JSONArray(read(new File(cache, "apps.json")));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Tv.App x = new Tv.App();
                x.pkg = o.optString("pkg");
                x.label = o.optString("label", Names.of(x.pkg));
                x.tv = o.optBoolean("tv");
                homeApps.add(x);
            }
        } catch (JSONException ignored) { }
        icons.clear();
        banners.clear();
        File[] fs = new File(cache, "icons").listFiles();
        if (fs != null) for (File f : fs) {
            if (!f.getName().endsWith(".png")) continue;
            Bitmap b = BitmapFactory.decodeFile(f.getPath());
            if (b != null) icons.put(f.getName().replace(".png", ""), b);
        }
        fs = new File(cache, "banners").listFiles();
        if (fs != null) for (File f : fs) {
            if (!f.getName().endsWith(".png")) continue;
            Bitmap b = BitmapFactory.decodeFile(f.getPath());
            if (b != null) banners.put(f.getName().replace(".png", ""), b);
        }
        walls.clear();
        try {
            JSONArray a = new JSONArray(read(new File(cache, "walls.json")));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Tv.Wall w = new Tv.Wall();
                w.name = o.optString("name");
                w.bytes = o.optLong("bytes");
                w.brightness = o.optInt("brightness", -1);
                walls.add(w);
            }
        } catch (JSONException ignored) { }
        thumbs.clear();
        for (Tv.Wall w : walls) {
            File f = new File(new File(cache, "thumbs"), w.name + ".jpg");
            if (f.isFile()) {
                Bitmap b = BitmapFactory.decodeFile(f.getPath());
                if (b != null) thumbs.put(w.name, b);
            }
        }
        currentWall = read(new File(cache, "current.txt")).trim();
        logos.clear();
        updateBackdrop();
    }

    // ------------------------------------------------------------------ the settings draft

    private void loadDraft() {
        try {
            raw = new JSONObject(read(draftFile));
        } catch (JSONException e) {
            raw = new JSONObject();
        }
    }

    private void saveDraft() {
        try {
            write(draftFile, raw.toString(1).getBytes(StandardCharsets.UTF_8));
            prefs.edit().putBoolean("have_draft", true).apply();
        } catch (Exception ignored) { }
    }

    private JSONObject merged() {
        return Themes.merge(raw);
    }

    private void put(String key, Object value) {
        try {
            if (value == null) raw.remove(key);
            else raw.put(key, value);
        } catch (JSONException ignored) { }
        changed();
    }

    private void remove(String... keys) {
        for (String k : keys) raw.remove(k);
        changed();
    }

    /** Settings of one watch face live under "faces" -> face name. */
    private void putFace(String face, String key, Object value) {
        try {
            JSONObject all = raw.optJSONObject("faces");
            if (all == null) {
                all = new JSONObject();
                raw.put("faces", all);
            }
            JSONObject mine = all.optJSONObject(face);
            if (mine == null) {
                mine = new JSONObject();
                all.put(face, mine);
            }
            if (value == null) mine.remove(key);
            else mine.put(key, value);
        } catch (JSONException ignored) { }
        changed();
    }

    private void changed() {
        saveDraft();
        dirty = true;
        styleSend();
        for (Runnable r : new ArrayList<>(onChange)) r.run();
        main.removeCallbacks(autoSend);
        if (prefs.getBoolean("autosend", true) && connected) main.postDelayed(autoSend, 400);
    }

    private android.animation.ObjectAnimator pulse;

    /** The header chip: green and breathing when connected and up to date, a send hint when not. */
    private void styleSend() {
        if (chipText == null) return;
        int col;
        String t;
        if (!connected) {
            col = BAD;
            t = "Not connected";
        } else if (dirty) {
            col = 0xFFFBBF24;
            t = "Send";
        } else {
            col = GOOD;
            String name = prefs.getString("tv_name", "");
            t = name.isEmpty() ? "Live" : name;
        }
        chipText.setText(t);
        chipDot.setBackground(Fx.pill(col, px(5), 0, 0));
        if (pulse == null) {
            pulse = android.animation.ObjectAnimator.ofFloat(chipDot, View.ALPHA, 1f, 0.35f);
            pulse.setDuration(900);
            pulse.setRepeatCount(android.animation.ValueAnimator.INFINITE);
            pulse.setRepeatMode(android.animation.ValueAnimator.REVERSE);
        }
        if (connected && !dirty) {
            if (!pulse.isStarted()) pulse.start();
        } else {
            pulse.cancel();
            chipDot.setAlpha(1f);
        }
    }

    /** The backdrop follows the TV's wallpaper (or an aurora before there is one). */
    private void updateBackdrop() {
        if (backdrop == null) return;
        Bitmap w = previewWall(merged());
        backdrop.setWallpaper(w, w != null ? Noir.accent(w) : 0);
    }

    private void sendNow(boolean showNow) {
        main.removeCallbacks(autoSend);
        if (!connected) {
            status("Not connected. Settings are kept on the phone; connect in System to send them.", BAD);
            return;
        }
        final JSONObject snapshot;
        try {
            snapshot = new JSONObject(raw.toString());
        } catch (JSONException e) {
            return;
        }
        sender.execute(() -> {
            String r;
            boolean ok = true;
            try {
                r = tv.sendConfig(snapshot, showNow);
            } catch (Exception e) {
                ok = false;
                r = "Not sent: " + (e.getMessage() == null ? e.toString() : e.getMessage());
                if (link != null && !link.isConnected()) connected = false;
            }
            final String msg = r;
            final boolean good = ok;
            main.post(() -> {
                // only clear "Send" if nothing changed while it was on its way
                if (good && snapshot.toString().equals(raw.toString())) dirty = false;
                if (!good) status(msg, BAD);
                styleSend();
            });
        });
    }

    private List<String> list(JSONObject o, String key, String... fallback) {
        List<String> out = new ArrayList<>();
        JSONArray a = o.optJSONArray(key);
        if (a == null) out.addAll(Arrays.asList(fallback));
        else for (int i = 0; i < a.length(); i++) out.add(a.optString(i));
        return out;
    }

    private void putList(String key, List<String> values) {
        put(key, new JSONArray(values));
    }

    // ------------------------------------------------------------------ previews

    private List<HomePreview.Entry> dockEntries(JSONObject m) {
        List<String> dock = list(m, "dock", DEFAULT_DOCK);
        Set<String> hidden = new HashSet<>(list(m, "hidden"));
        List<HomePreview.Entry> out = new ArrayList<>();
        Set<String> used = new HashSet<>();
        for (String p : dock) {
            if (hidden.contains(p) || used.contains(p)) continue;
            Tv.App a = app(p);
            if (a == null && !homeApps.isEmpty()) continue;
            out.add(entry(p));
            used.add(p);
        }
        if (m.optBoolean("show_other_apps", false)) {
            for (Tv.App a : homeApps) if (!used.contains(a.pkg) && !hidden.contains(a.pkg)) out.add(entry(a.pkg));
        }
        return out;
    }

    private HomePreview.Entry entry(String pkg) {
        HomePreview.Entry e = new HomePreview.Entry();
        e.pkg = pkg;
        Tv.App a = app(pkg);
        e.label = a != null ? a.label : Names.of(pkg);
        e.icon = icons.get(pkg);
        e.banner = banners.get(pkg);
        return e;
    }

    private Tv.App app(String pkg) {
        for (Tv.App a : homeApps) if (a.pkg.equals(pkg)) return a;
        return null;
    }

    /** The wallpaper the preview shows: the fixed one, the one on the TV now, or the first that fits. */
    private Bitmap previewWall(JSONObject m) {
        String fixed = m.optString("wallpaper_fixed", "");
        if (!fixed.isEmpty() && thumbs.containsKey(fixed)) return thumbs.get(fixed);
        boolean dark = m.optBoolean("dark_only", false);
        int limit = m.optInt("dark_level", 45);
        Tv.Wall cur = null;
        for (Tv.Wall w : walls) if (w.name.equals(currentWall)) cur = w;
        if (cur != null && thumbs.containsKey(cur.name) && (!dark || (cur.brightness >= 0 && cur.brightness < limit))) {
            return thumbs.get(cur.name);
        }
        for (Tv.Wall w : walls) {
            if (dark && !(w.brightness >= 0 && w.brightness < limit)) continue;
            if (thumbs.containsKey(w.name)) return thumbs.get(w.name);
        }
        return thumbs.isEmpty() ? null : thumbs.values().iterator().next();
    }

    private Bitmap[] slides(JSONObject m) {
        List<Bitmap> out = new ArrayList<>();
        boolean dark = m.optBoolean("dark_only", false);
        int limit = m.optInt("dark_level", 45);
        for (Tv.Wall w : walls) {
            if (dark && !(w.brightness >= 0 && w.brightness < limit)) continue;
            Bitmap b = thumbs.get(w.name);
            if (b != null) out.add(b);
            if (out.size() >= 8) break;
        }
        return out.toArray(new Bitmap[0]);
    }

    /** A live preview of the home screen that follows every change; tap it to move the selection. */
    private HomePreview homePreview() {
        HomePreview p = new HomePreview(this, logos);
        int[] focus = {0, 0};
        Runnable update = () -> {
            JSONObject m = merged();
            p.set(m, previewWall(m), dockEntries(m));
        };
        update.run();
        onChange.add(update);
        p.setOnClickListener(v -> {
            int n = dockEntries(merged()).size();
            int buttons = list(merged(), "buttons").size();
            focus[0]++;
            if (focus[1] == 0 && focus[0] >= n) {
                focus[1] = 1;
                focus[0] = 0;
            } else if (focus[1] == 1 && focus[0] >= buttons) {
                focus[1] = 0;
                focus[0] = 0;
            }
            p.setFocus(focus[0], focus[1] == 1);
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, px(8), 0, px(2));
        p.setLayoutParams(lp);
        return p;
    }

    // ------------------------------------------------------------------ Live tab

    private void buildLive() {
        LinearLayout c = card(null);
        LinearLayout top = row();
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = text("Your TV", 18, TEXT, true);
        t.setTypeface(Fx.font(700));
        top.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView live = text("●  LIVE", 11.5f, 0xFFFFFFFF, true);
        live.setLetterSpacing(0.12f);
        live.setPadding(px(10), px(4), px(10), px(4));
        live.setBackground(Fx.pill(connected ? 0xCCE11D48 : 0x40FFFFFF, px(12), 0, 0));
        top.addView(live);
        c.addView(top);
        if (connected) {
            android.animation.ObjectAnimator blink = android.animation.ObjectAnimator.ofFloat(live, View.ALPHA, 1f, 0.55f);
            blink.setDuration(800);
            blink.setRepeatCount(android.animation.ValueAnimator.INFINITE);
            blink.setRepeatMode(android.animation.ValueAnimator.REVERSE);
            blink.start();
        }
        FrameLayout frame = new FrameLayout(this) {
            @Override
            protected void onMeasure(int w, int h) {
                int width = MeasureSpec.getSize(w);
                super.onMeasure(w, MeasureSpec.makeMeasureSpec(Math.round(width * 9f / 16f), MeasureSpec.EXACTLY));
            }
        };
        frame.setBackground(Fx.pill(0xE6000000, px(16), 0x33FFFFFF, 1));
        frame.setClipToOutline(true);
        frame.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View v, android.graphics.Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), px(16));
            }
        });
        mirror = new ImageView(this);
        mirror.setScaleType(ImageView.ScaleType.FIT_CENTER);
        frame.addView(mirror, new FrameLayout.LayoutParams(-1, -1));
        TextView hint = text(connected ? "Loading the TV's screen..." : "Connect a TV to see it here", 13, DIM, false);
        hint.setGravity(Gravity.CENTER);
        frame.addView(hint, new FrameLayout.LayoutParams(-1, -1));
        mirror.setTag(hint);
        LinearLayout.LayoutParams fl = new LinearLayout.LayoutParams(-1, -2);
        fl.topMargin = px(10);
        c.addView(frame, fl);
        if (!connected) {
            LinearLayout cr = row();
            cr.addView(button("Connect a TV", true, v -> {
                sub = found.isEmpty() && prefs.getString("ip", "").isEmpty() ? "setup" : "system";
                showTab(5);
            }));
            c.addView(cr);
        }
        page.addView(c);

        // pad or touchpad
        String mode = prefs.getString("remote_mode", "pad");
        LinearLayout modes = row();
        modes.setGravity(Gravity.CENTER);
        TextView m1 = chip("D-pad", "pad".equals(mode)), m2 = chip("Touchpad", "touch".equals(mode));
        m1.setOnClickListener(v -> { prefs.edit().putString("remote_mode", "pad").apply(); refreshTab(); });
        m2.setOnClickListener(v -> { prefs.edit().putString("remote_mode", "touch").apply(); refreshTab(); });
        modes.addView(m1);
        modes.addView(m2);
        LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(-1, -2);
        ml.topMargin = px(14);
        page.addView(modes, ml);
        View padView = "touch".equals(mode) ? new Widgets.Touchpad(this, this::remote) : new Widgets.Pad(this, this::remote);
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(-1, -2);
        pl.topMargin = px(10);
        page.addView(padView, pl);

        LinearLayout keys = row();
        keys.setGravity(Gravity.CENTER);
        keys.addView(roundKey(R.drawable.f_back, "Back", "KEYCODE_BACK"));
        keys.addView(roundKey(R.drawable.f_home, "Home", "KEYCODE_HOME"));
        keys.addView(roundKey(R.drawable.f_menu, "Menu", "KEYCODE_MENU"));
        keys.addView(roundKey(R.drawable.f_play, "Play", "KEYCODE_MEDIA_PLAY_PAUSE"));
        page.addView(keys);
        LinearLayout keys2 = row();
        keys2.setGravity(Gravity.CENTER);
        keys2.addView(roundKey(R.drawable.f_vol_down, "Vol −", "KEYCODE_VOLUME_DOWN"));
        keys2.addView(roundKey(R.drawable.f_mute, "Mute", "KEYCODE_VOLUME_MUTE"));
        keys2.addView(roundKey(R.drawable.f_vol_up, "Vol +", "KEYCODE_VOLUME_UP"));
        keys2.addView(roundKey(R.drawable.g_inputs, "Inputs", "KEYCODE_TV_INPUT"));
        page.addView(keys2);

        LinearLayout kb = card("Type on the TV");
        EditText e = edit("", "Search text for the TV");
        kb.addView(e);
        LinearLayout tr = row();
        tr.addView(button("Type it", true, v -> {
            String str = e.getText().toString();
            if (!str.isEmpty()) work(null, () -> { tv.type(str); return "Typed on the TV."; }, null);
        }));
        tr.addView(button("⌫", false, v -> remote("KEYCODE_DEL")));
        tr.addView(button("Enter", false, v -> remote("KEYCODE_ENTER")));
        kb.addView(tr);
        page.addView(kb);

        LinearLayout q = card("Quick");
        LinearLayout q1 = row();
        q1.addView(tile(R.drawable.g_chip, "Close all apps", v -> work("Closing apps...", tv::closeAll, null)));
        q1.addView(tile(R.drawable.f_refresh, "Clear caches", v -> work("Clearing caches...", tv::clearCaches, null)));
        q.addView(q1);
        LinearLayout q2 = row();
        q2.addView(tile(R.drawable.f_moon, "Screensaver now", v -> work("Starting the screensaver...", () -> {
            tv.testFace(null);
            return "Showing the screensaver. Any button on the remote closes it.";
        }, null)));
        q2.addView(tile(R.drawable.f_home, "Restart home", v -> work("Restarting...", () -> { tv.restartHome(); return "Home restarted."; }, null)));
        q.addView(q2);
        TextView mem = text("", 13.5f, DIM, false);
        mem.setPadding(px(4), px(8), 0, 0);
        q.addView(mem);
        page.addView(q);
        if (connected) {
            worker.execute(() -> {
                try {
                    Tv.Status st = tv.status();
                    main.post(() -> mem.setText("RAM " + st.ramUsedPct + "% used  ·  " + st.ramFreeMb + " MB free\nStorage "
                            + st.storageUsedPct + "% used  ·  " + gb(st.storageFreeMb) + " free"));
                } catch (IOException ignored) { }
            });
        }
    }

    /** A round glass key with an icon and a word under it. */
    private View roundKey(int icon, String name, String code) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        GlassCard b = new GlassCard(this, px(31), 0x24FFFFFF);
        b.setGravity(Gravity.CENTER);
        ImageView ic = new ImageView(this);
        ic.setImageResource(icon);
        ic.setImageTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
        b.addView(ic, new LinearLayout.LayoutParams(px(24), px(24)));
        b.setOnClickListener(v -> remote(code));
        Fx.press(b);
        col.addView(b, new LinearLayout.LayoutParams(px(62), px(62)));
        TextView t = text(name, 11.5f, DIM, true);
        t.setPadding(0, px(5), 0, 0);
        col.addView(t);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
        lp.setMargins(0, px(10), 0, px(4));
        col.setLayoutParams(lp);
        return col;
    }

    /** A square-ish glass tile with an icon and a label (quick actions, menu). */
    private View tile(int icon, String name, View.OnClickListener l) {
        GlassCard b = new GlassCard(this, px(20), 0x1AFFFFFF);
        b.setPadding(px(14), px(14), px(14), px(14));
        ImageView ic = new ImageView(this);
        ic.setImageResource(icon);
        ic.setImageTintList(android.content.res.ColorStateList.valueOf(0xFFC4B5FD));
        b.addView(ic, new LinearLayout.LayoutParams(px(26), px(26)));
        TextView t = text(name, 14, TEXT, true);
        t.setPadding(0, px(10), 0, 0);
        b.addView(t);
        b.setOnClickListener(l);
        Fx.press(b);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
        lp.setMargins(px(4), px(4), px(4), px(4));
        b.setLayoutParams(lp);
        return b;
    }

    private static String gb(long mb) {
        return mb > 1024 ? String.format(Locale.ROOT, "%.1f GB", mb / 1024.0) : mb + " MB";
    }

    private void remote(String code) {
        if (!connected) {
            status("Connect to the TV first (More > TV and connection).", BAD);
            return;
        }
        worker.execute(() -> {
            try {
                if (code.startsWith("LONG_")) tv.longKey("KEYCODE_" + code.substring(5));
                else tv.key(code);
            } catch (IOException e) {
                main.post(() -> status(e.getMessage(), BAD));
            }
        });
    }

    private void startMirror() {
        if (!connected || mirror == null || mirrorOn) return;
        mirrorOn = true;
        final ImageView target = mirror;
        mirrorThread = new Thread(() -> {
            while (mirrorOn) {
                try {
                    byte[] png = tv.screenshot();
                    BitmapFactory.Options o = new BitmapFactory.Options();
                    o.inSampleSize = 2;
                    Bitmap b = BitmapFactory.decodeByteArray(png, 0, png.length, o);
                    if (b != null) main.post(() -> {
                        if (!mirrorOn) return;
                        target.setImageBitmap(b);
                        Object h = target.getTag();
                        if (h instanceof View) ((View) h).setVisibility(View.GONE);
                    });
                    Thread.sleep(350);
                } catch (Exception e) {
                    mirrorOn = false;
                    main.post(() -> status("The TV picture stopped: " + e.getMessage(), BAD));
                }
            }
        }, "mirror");
        mirrorThread.start();
    }

    private void stopMirror() {
        mirrorOn = false;
    }

    // ------------------------------------------------------------------ Themes tab

    /** Keys a theme decides; picking a theme clears them so its own look shows in full. */
    static final String[] STYLE_KEYS = {"bg", "bg_color", "bg_color2", "ink", "tile_style", "clock_style", "bottom_shade",
            "widget_style", "dock_style", "dock_side", "icon_style", "icon_size", "icon_gap",
            "icon_idle", "focus_scale", "labels", "label_size", "focus_ring", "glow", "glow_color", "glow_strength",
            "glow_size", "accent", "wallpaper_dim", "dark_only", "dark_level", "shade", "bar_style", "bar_clock",
            "clock_font", "clock_scale", "clock_color", "seconds", "edge", "screensaver", "eyes", "bar_weather",
            "row_visible", "row_top", "row_band", "row_title", "title_font", "greeting", "tile_box"};

    private void buildThemes() {
        boolean classic = homeNow.startsWith(Tv.CLEAN);
        String now = classic ? "" : merged().optString("theme", "glass");
        if (themeOpen != null && Arrays.asList(Themes.IDS).contains(themeOpen)) {
            buildThemeDetail(themeOpen, now);
            return;
        }
        themeOpen = null;
        page.addView(note(Themes.IDS.length + " looks for your TV. Tap one to see it big and use it. Your apps, "
                + "wallpapers and widgets stay."));
        LinearLayout line = null;
        for (int i = 0; i < Themes.IDS.length; i++) {
            if (i % 2 == 0) {
                line = row();
                line.setPadding(0, 0, 0, 0);
                page.addView(line);
            }
            String id = Themes.IDS[i];
            boolean inUse = id.equals(now);
            GlassCard cell = new GlassCard(this, px(22), inUse ? 0x2EA78BFA : CARD);
            cell.setPadding(px(7), px(7), px(7), px(10));
            HomePreview p = new HomePreview(this, logos);
            JSONObject m = themeLook(id);
            p.set(m, previewWall(m), dockEntries(m));
            p.setClickable(false);
            cell.addView(p);
            LinearLayout nameRow = row();
            nameRow.setGravity(Gravity.CENTER_VERTICAL);
            nameRow.setPadding(px(5), px(8), px(4), 0);
            TextView n = text(THEME_NAMES[i], 15, TEXT, true);
            n.setTypeface(Fx.font(700));
            nameRow.addView(n, new LinearLayout.LayoutParams(0, -2, 1f));
            if (inUse) {
                View dot = new View(this);
                dot.setBackground(Fx.brand(px(5)));
                nameRow.addView(dot, new LinearLayout.LayoutParams(px(10), px(10)));
            }
            cell.addView(nameRow);
            TextView tag = text(inUse ? "In use  ·  " + THEME_TAG[i] : THEME_TAG[i], 11.5f, inUse ? ACCENT : DIM, false);
            tag.setPadding(px(5), px(1), px(4), 0);
            tag.setSingleLine(true);
            cell.addView(tag);
            cell.setOnClickListener(v -> {
                themeOpen = id;
                showTab(1);
            });
            Fx.press(cell);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
            lp.setMargins(px(5), px(6), px(5), px(6));
            line.addView(cell, lp);
        }
        if (line != null && line.getChildCount() == 1) {
            LinearLayout.LayoutParams fl = new LinearLayout.LayoutParams(0, 1, 1f);
            fl.setMargins(px(5), 0, px(5), 0);
            line.addView(new View(this), fl);
        }
        classicCard(classic);
    }

    /** One theme big: its home screen, its screensaver, what it is, and the button to use it. */
    private void buildThemeDetail(String id, String now) {
        int i = Arrays.asList(Themes.IDS).indexOf(id);
        boolean inUse = id.equals(now);
        String themeName = THEME_NAMES[i];
        LinearLayout top = row();
        top.setGravity(Gravity.CENTER_VERTICAL);
        ImageView back = new ImageView(this);
        back.setImageResource(R.drawable.f_back);
        back.setPadding(px(10), px(10), px(10), px(10));
        back.setBackground(Fx.pill(0x1FFFFFFF, px(22), LINE, 1));
        back.setOnClickListener(v -> {
            themeOpen = null;
            showTab(1);
        });
        Fx.press(back);
        top.addView(back, new LinearLayout.LayoutParams(px(44), px(44)));
        LinearLayout names = new LinearLayout(this);
        names.setOrientation(LinearLayout.VERTICAL);
        names.setPadding(px(12), 0, 0, 0);
        TextView t = text(themeName, 26, TEXT, true);
        t.setTypeface(Fx.font(800));
        names.addView(t);
        TextView tg = text((i + 1) + " of " + Themes.IDS.length + "  ·  " + THEME_TAG[i], 12.5f, DIM, false);
        names.addView(tg);
        top.addView(names, new LinearLayout.LayoutParams(0, -2, 1f));
        if (inUse) {
            TextView badge = text("IN USE", 11, TEXT, true);
            badge.setLetterSpacing(0.1f);
            badge.setPadding(px(10), px(4), px(10), px(4));
            badge.setBackground(Fx.brand(px(12)));
            top.addView(badge);
        }
        page.addView(top);

        GlassCard big = new GlassCard(this, px(26), CARD);
        big.setPadding(px(10), px(10), px(10), px(12));
        HomePreview p = new HomePreview(this, logos);
        JSONObject m = themeLook(id);
        p.set(m, previewWall(m), dockEntries(m));
        int[] focus = {0};
        p.setOnClickListener(v -> {
            focus[0] = (focus[0] + 1) % Math.max(1, dockEntries(m).size());
            p.setFocus(focus[0], false);
        });
        big.addView(p);
        TextView hint = text("Tap the picture to move the selection", 11.5f, DIM, false);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, px(8), 0, 0);
        big.addView(hint);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, -2);
        bl.topMargin = px(12);
        big.setLayoutParams(bl);
        page.addView(big);

        LinearLayout about = card(null);
        TextView d = text(THEME_TEXT[i], 15, TEXT, false);
        d.setLineSpacing(px(3), 1f);
        about.addView(d);
        List<JSONObject> ws = Board.list(m);
        if (!ws.isEmpty()) {
            StringBuilder names2 = new StringBuilder();
            for (JSONObject w : ws) names2.append(names2.length() > 0 ? ", " : "").append(Board.name(w.optString("type")));
            about.addView(label("Widgets"));
            about.addView(note(names2 + (raw.has("widgets") ? " (yours)" : " (this theme's; change them in Design > Widgets)")));
        }
        about.addView(label("Its screensaver"));
        FacePreview f = new FacePreview(this);
        f.set(m.optString("face", "glass"), m, previewWall(m), slides(m));
        about.addView(f);
        TextView fn = text(Faces.NAMES[Math.max(0, Arrays.asList(Faces.IDS).indexOf(m.optString("face", "glass")))], 13, DIM, false);
        fn.setPadding(0, px(6), 0, 0);
        about.addView(fn);
        page.addView(about);

        LinearLayout r = row();
        r.addView(button(inUse ? "Reset its look" : "Use " + themeName, !inUse, v -> applyTheme(id, themeName)));
        r.addView(button("Customise", false, v -> {
            if (!inUse) applyTheme(id, themeName);
            designSeg = "layout";
            showTab(2);
        }));
        page.addView(r);
        LinearLayout nav = row();
        nav.setGravity(Gravity.CENTER);
        TextView prev = small("‹  " + THEME_NAMES[(i + Themes.IDS.length - 1) % Themes.IDS.length], v -> {
            themeOpen = Themes.IDS[(i + Themes.IDS.length - 1) % Themes.IDS.length];
            showTab(1);
        });
        TextView next = small(THEME_NAMES[(i + 1) % Themes.IDS.length] + "  ›", v -> {
            themeOpen = Themes.IDS[(i + 1) % Themes.IDS.length];
            showTab(1);
        });
        nav.addView(prev);
        nav.addView(next);
        page.addView(nav);
    }

    private void applyTheme(String id, String themeName) {
        for (String k : STYLE_KEYS) raw.remove(k);
        raw.remove("faces");
        try {
            raw.put("theme", id);
        } catch (JSONException ignored) { }
        changed();
        status(themeName + " theme set. Your apps and wallpapers stay.", GOOD);
        if (connected && !homeNow.startsWith(Tv.GLASS)) {
            work("Switching the TV to Glass Home...", () -> {
                String done = tv.useGlass();
                homeNow = Tv.GLASS_HOME;
                return themeName + " theme on. " + done;
            }, this::refreshTab);
        } else {
            refreshTab();
        }
    }

    /** A sideways carousel that settles on one card and makes the others smaller and dimmer. */
    final class Carousel extends GlassCard.HScroll {
        Carousel(android.content.Context ctx) {
            super(ctx);
            setClipChildren(false);
            setClipToPadding(false);
        }

        LinearLayout strip() {
            return (LinearLayout) getChildAt(0);
        }

        void snapTo(int i, boolean smooth) {
            LinearLayout s = strip();
            if (s == null || i < 0 || i >= s.getChildCount()) return;
            View c = s.getChildAt(i);
            int x = c.getLeft() + c.getWidth() / 2 - getWidth() / 2;
            if (smooth) smoothScrollTo(x, 0);
            else scrollTo(x, 0);
            shape();
        }

        void shape() {
            LinearLayout s = strip();
            if (s == null) return;
            float mid = getScrollX() + getWidth() / 2f;
            for (int k = 0; k < s.getChildCount(); k++) {
                View c = s.getChildAt(k);
                float d = Math.abs(c.getLeft() + c.getWidth() / 2f - mid) / Math.max(1f, c.getWidth());
                float k2 = Math.min(1f, d);
                c.setScaleX(1f - 0.07f * k2);
                c.setScaleY(1f - 0.07f * k2);
                c.setAlpha(1f - 0.45f * k2);
            }
        }

        @Override
        protected void onScrollChanged(int l, int t, int ol, int ot) {
            super.onScrollChanged(l, t, ol, ot);
            shape();
        }

        @android.annotation.SuppressLint("ClickableViewAccessibility")
        @Override
        public boolean onTouchEvent(android.view.MotionEvent e) {
            boolean r = super.onTouchEvent(e);
            if (e.getActionMasked() == android.view.MotionEvent.ACTION_UP || e.getActionMasked() == android.view.MotionEvent.ACTION_CANCEL) {
                postDelayed(() -> {
                    LinearLayout s = strip();
                    if (s == null) return;
                    float mid = getScrollX() + getWidth() / 2f;
                    int best = 0;
                    float bd = Float.MAX_VALUE;
                    for (int k = 0; k < s.getChildCount(); k++) {
                        View c = s.getChildAt(k);
                        float d = Math.abs(c.getLeft() + c.getWidth() / 2f - mid);
                        if (d < bd) {
                            bd = d;
                            best = k;
                        }
                    }
                    snapTo(best, true);
                }, 80);
            }
            return r;
        }

        @Override
        public void fling(int velocityX) {
            super.fling(velocityX / 3);
        }
    }

    /** The very first launcher (cleanyourtv) as a sixth choice; its picture is taken from the TV once used. */
    private void classicCard(boolean inUse) {
        LinearLayout c = card("Classic (the first clean launcher)" + (inUse ? "  ·  in use" : ""));
        File shot = new File(cache, "classic.jpg");
        FrameLayout frame = new FrameLayout(this) {
            @Override
            protected void onMeasure(int w, int h) {
                int width = MeasureSpec.getSize(w);
                super.onMeasure(w, MeasureSpec.makeMeasureSpec(Math.round(width * 9f / 16f), MeasureSpec.EXACTLY));
            }
        };
        frame.setBackground(round(0xFF000000, px(10), LINE, 1));
        if (shot.isFile()) {
            ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setImageBitmap(BitmapFactory.decodeFile(shot.getPath()));
            frame.addView(iv, new FrameLayout.LayoutParams(-1, -1));
        } else {
            TextView t = text("Its picture appears here after you use it once.", 13, DIM, false);
            t.setGravity(Gravity.CENTER);
            frame.addView(t, new FrameLayout.LayoutParams(-1, -1));
        }
        c.addView(frame);
        c.addView(note("Your first home screen: the RAM and storage pill, its own dock and clock screensaver. "
                + "Its look is set by its own settings; the other tabs here set up Glass Home. Pick any theme above "
                + "to come back to Glass Home."));
        LinearLayout r = row();
        r.addView(button(inUse ? "Take a new picture" : "Use the Classic launcher", !inUse, v -> work("Switching the TV to Classic...", () -> {
            String res = tv.useClassic();
            homeNow = Tv.CLEAN_HOME;
            Thread.sleep(3500);
            byte[] png = tv.screenshot();
            Bitmap b = decodeSmall(png, 960);
            if (b != null) {
                java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
                b.compress(Bitmap.CompressFormat.JPEG, 85, o);
                write(shot, o.toByteArray());
            }
            return res;
        }, this::refreshTab)));
        c.addView(r);
        page.addView(c);
    }

    /** What the user's setup looks like in a theme. */
    private JSONObject themeLook(String id) {
        try {
            JSONObject r = new JSONObject(raw.toString());
            for (String k : STYLE_KEYS) r.remove(k);
            r.remove("faces");
            r.put("theme", id);
            return Themes.merge(r);
        } catch (JSONException e) {
            return Themes.merge(new JSONObject());
        }
    }

    // ------------------------------------------------------------------ Home tab

    private void buildDesign() {
        page.addView(homePreview());
        page.addView(note("Live preview of the TV. Tap it to move the selection (apps, then the top buttons)."));
        GlassCard seg = new GlassCard(this, px(24), 0x1AFFFFFF);
        seg.setOrientation(LinearLayout.HORIZONTAL);
        seg.setPadding(px(4), px(4), px(4), px(4));
        String[] names = {"Layout", "Icons", "Glow", "Clock", "Widgets", "Dock"};
        String[] ids = {"layout", "icons", "glow", "clock", "widgets", "dock"};
        for (int i = 0; i < ids.length; i++) {
            final String id = ids[i];
            TextView t = text(names[i], 12.5f, TEXT, true);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, px(9), 0, px(9));
            t.setBackground(id.equals(designSeg) ? Fx.brand(px(20)) : null);
            t.setAlpha(id.equals(designSeg) ? 1f : 0.7f);
            t.setOnClickListener(v -> {
                designSeg = id;
                refreshTab();
            });
            Fx.press(t);
            seg.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        }
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-1, -2);
        sl.topMargin = px(6);
        page.addView(seg, sl);
        if ("dock".equals(designSeg)) buildDock(false);
        else if ("widgets".equals(designSeg)) buildWidgets();
        else buildHome(designSeg);
    }

    private void buildHome(String section) {
        JSONObject m = merged();
        final List<View> keep = new ArrayList<>();
        int before = page.getChildCount();
        boolean panel = "panel".equals(m.optString("dock_style", "icons"));

        LinearLayout lay = card("Layout");
        lay.addView(label("Apps shown as"));
        lay.addView(choices("dock_style", new String[]{"Row (Clean)", "Icons", "Glass panel", "Names only"},
                new Object[]{"row", "icons", "panel", "list"}, true));
        lay.addView(label("Side"));
        lay.addView(choices("dock_side", new String[]{"Left", "Right"}, new Object[]{"left", "right"}, false));
        lay.addView(label("Space from the screen edges"));
        lay.addView(slider("edge", 1, 12, "%", null));
        lay.addView(reset("dock_style", "dock_side", "edge"));
        page.addView(lay); lay.setTag("layout");

        LinearLayout sty = card("Style");
        sty.addView(label("Background"));
        sty.addView(choices("bg", new String[]{"Wallpaper", "Solid colour", "Gradient"}, new Object[]{"wallpaper", "solid", "gradient"}, true));
        String bgNow = m.optString("bg", "wallpaper");
        if (!"wallpaper".equals(bgNow)) {
            sty.addView(label("gradient".equals(bgNow) ? "First colour" : "Colour"));
            sty.addView(colors("bg_color", new String[]{}, new String[]{}, null, BG_SWATCHES));
            if ("gradient".equals(bgNow)) {
                sty.addView(label("Second colour"));
                sty.addView(colors("bg_color2", new String[]{}, new String[]{}, null, BG_SWATCHES));
            }
        }
        sty.addView(label("Text colour"));
        sty.addView(choices("ink", new String[]{"Light", "Dark (light backgrounds)"}, new Object[]{"light", "dark"}, false));
        sty.addView(label("Tiles (row and panel)"));
        sty.addView(choices("tile_style", new String[]{"Glass", "Plush", "Soft", "Material"},
                new Object[]{"glass", "plush", "soft", "tonal"}, false));
        sty.addView(label("Shade along the bottom"));
        sty.addView(slider("bottom_shade", 0, 100, "%", null));
        sty.addView(reset("bg", "bg_color", "bg_color2", "ink", "tile_style", "bottom_shade"));
        page.addView(sty); sty.setTag("layout");

        if ("daynight".equals(m.optString("theme"))) {
            LinearLayout dn = card("Day & Night");
            dn.addView(note("Now showing the " + m.optString("period", "day") + " look. It changes by itself at these hours."));
            dn.addView(label("Day starts at (hour)"));
            dn.addView(slider("day_from", 0, 23, ":00", null));
            dn.addView(label("Night starts at (hour)"));
            dn.addView(slider("night_from", 0, 23, ":00", null));
            dn.addView(reset("day_from", "night_from"));
            page.addView(dn); dn.setTag("layout");
        }

        LinearLayout look = card("App look (icon pack)");
        look.addView(note("Works in every theme: the row, the glass panel and the floating icons. Every pack is made "
                + "from each app's own logo, so new apps get it too."));
        look.addView(packPicker());
        look.addView(toggle("Boxes behind the apps", "tile_box"));
        look.addView(reset("icon_style", "tile_box"));
        page.addView(look); look.setTag("icons");

        boolean rowStyle = "row".equals(m.optString("dock_style", "icons"));
        if (rowStyle) {
            LinearLayout rw = card("Row of apps");
            rw.addView(label("Apps visible at once"));
            rw.addView(slider("row_visible", 2, 7, "", null));
            rw.addView(label("Height of the row"));
            rw.addView(slider("row_top", 40, 75, "%", null));
            rw.addView(label("Selected app grows to"));
            rw.addView(slider("focus_scale", 100, 130, "%", null));
            rw.addView(toggle("Glass band behind the apps", "row_band"));
            rw.addView(toggle("White ring on the selected app", "focus_ring"));
            rw.addView(toggle("Big name of the selected app", "row_title"));
            rw.addView(label("Name font"));
            rw.addView(choices("title_font", new String[]{"Thin", "Light", "Regular", "Medium", "Bold", "Dot matrix"},
                    new Object[]{"thin", "light", "regular", "medium", "bold", "dot"}, false));
            rw.addView(toggle("Greeting (Good evening)", "greeting"));
            rw.addView(reset("row_visible", "row_top", "focus_scale", "row_band", "focus_ring", "row_title", "title_font", "greeting"));
            page.addView(rw); rw.setTag("layout");
        }
        LinearLayout ic = card(panel ? "Banners" : "Icons");
        if (rowStyle) {
            // banners in a row: nothing icon-specific
        } else if (panel) {
            ic.addView(toggle("White ring on the selected app", "focus_ring"));
        } else {
            ic.addView(label("Icon size (of screen height)"));
            ic.addView(slider("icon_size", 5, 18, "%", null));
            ic.addView(label("Space between icons"));
            ic.addView(slider("icon_gap", 1, 10, "%", null));
            ic.addView(label("Brightness of the other icons"));
            ic.addView(slider("icon_idle", 15, 100, "%", null));
            ic.addView(label("Selected icon grows to"));
            ic.addView(slider("focus_scale", 100, 150, "%", null));
            ic.addView(toggle("Name beside the selected app", "labels"));
            ic.addView(label("Name size"));
            ic.addView(slider("label_size", 12, 45, "%", null));
        }
        ic.addView(reset("icon_style", "icon_size", "icon_gap", "icon_idle", "focus_scale", "labels", "label_size", "focus_ring"));
        if (!rowStyle) { page.addView(ic); ic.setTag("icons"); }

        LinearLayout gl = card("Glow behind the selected app");
        gl.addView(toggle("Glow on", "glow"));
        gl.addView(label("Colour"));
        gl.addView(colors("glow_color", new String[]{"Wallpaper colour", "White"}, new String[]{"auto", "white"}, null));
        gl.addView(label("Strength"));
        gl.addView(slider("glow_strength", 5, 100, "%", null));
        gl.addView(label("Size"));
        gl.addView(slider("glow_size", 15, 90, "%", null));
        gl.addView(reset("glow", "glow_color", "glow_strength", "glow_size"));
        page.addView(gl); gl.setTag("glow");

        LinearLayout ac = card("Accent colour");
        ac.addView(note("Used by accent logos, the neon halo, an accent clock and the screensavers."));
        ac.addView(colors("accent", new String[]{"From the wallpaper"}, new String[]{"auto"}, null));
        ac.addView(reset("accent"));
        page.addView(ac); ac.setTag("glow");

        LinearLayout wl = card("Wallpaper look");
        wl.addView(label("Darken the wallpaper"));
        wl.addView(slider("wallpaper_dim", 0, 90, "%", null));
        wl.addView(label("Shade on the apps' side"));
        wl.addView(slider("shade", 0, 100, "%", null));
        wl.addView(toggle("Only dark wallpapers", "dark_only"));
        wl.addView(label("How dark counts as dark"));
        wl.addView(slider("dark_level", 10, 120, "", null));
        wl.addView(reset("wallpaper_dim", "shade", "dark_only", "dark_level"));
        page.addView(wl); wl.setTag("layout");

        LinearLayout bar = card("Top bar");
        bar.addView(label("Look"));
        bar.addView(choices("bar_style", new String[]{"Floating", "Glass pill"}, new Object[]{"bare", "pill"}, false));
        bar.addView(toggle("Clock", "bar_clock"));
        bar.addView(label("Clock style"));
        bar.addView(choices("clock_style", new String[]{"Text", "Dot matrix"}, new Object[]{"text", "dot"}, false));
        bar.addView(choices("clock", new String[]{"24-hour", "12-hour"}, new Object[]{"24", "12"}, false));
        bar.addView(label("Clock font"));
        bar.addView(choices("clock_font", new String[]{"Thin", "Light", "Regular", "Medium", "Bold", "Condensed"},
                new Object[]{"thin", "light", "regular", "medium", "bold", "condensed"}, false));
        bar.addView(label("Clock size"));
        bar.addView(slider("clock_scale", 60, 220, "%", null));
        bar.addView(label("Clock colour"));
        bar.addView(colors("clock_color", new String[]{"White", "Accent"}, new String[]{"white", "accent"}, null));
        bar.addView(toggle("Rolling seconds", "seconds"));
        bar.addView(toggle("Temperature and weather", "bar_weather"));
        bar.addView(label("Buttons (tick to show, arrows to order)"));
        bar.addView(buttonsEditor());
        bar.addView(label("Music button opens"));
        bar.addView(appPicker("music_app", "Any music app"));
        bar.addView(reset("bar_style", "bar_clock", "clock_style", "clock", "clock_font", "clock_scale", "clock_color", "seconds", "bar_weather", "buttons"));
        page.addView(bar); bar.setTag("clock");

        LinearLayout ex = card("Extras");
        ex.addView(toggle("Watching eyes above the apps (blink and follow)", "eyes"));
        page.addView(ex); ex.setTag("glow");
            for (int k = page.getChildCount() - 1; k >= before; k--) {
            Object tag = page.getChildAt(k).getTag();
            if (tag instanceof String && !section.equals(tag)) page.removeViewAt(k);
        }
    }

    // ------------------------------------------------------------------ widgets on the TV

    private List<JSONObject> widgetList() {
        List<JSONObject> out = new ArrayList<>();
        for (JSONObject w : Board.list(merged())) {
            try {
                out.add(new JSONObject(w.toString()));
            } catch (JSONException ignored) { }
        }
        return out;
    }

    private void saveWidgets(List<JSONObject> ws, boolean rebuild) {
        JSONArray a = new JSONArray();
        for (JSONObject w : ws) a.put(w);
        put("widgets", a);
        if (rebuild) refreshTab();
    }

    private void buildWidgets() {
        JSONObject m = merged();
        List<JSONObject> ws = widgetList();
        LinearLayout on = card("On the TV" + (ws.isEmpty() ? "" : "  ·  " + ws.size()));
        if (ws.isEmpty()) {
            on.addView(note("No widgets yet. Add one below: they sit around your apps on the TV's home screen and "
                    + "keep themselves up to date."));
        } else if (!raw.has("widgets")) {
            on.addView(note("These come with the " + Themes.name(m.optString("theme")) + " theme. Change anything and "
                    + "they become yours."));
        }
        for (int i = 0; i < ws.size(); i++) {
            final int index = i;
            JSONObject w = ws.get(i);
            String type = w.optString("type");
            GlassCard item = new GlassCard(this, px(20), 0x14FFFFFF);
            item.setPadding(px(12), px(10), px(12), px(10));
            LinearLayout head = row();
            head.setGravity(Gravity.CENTER_VERTICAL);
            WidgetSample sample = new WidgetSample(this, m, w, previewWall(m));
            head.addView(sample, new LinearLayout.LayoutParams(px(92), px(56)));
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setPadding(px(12), 0, 0, 0);
            TextView n = text(Board.name(type), 16, TEXT, true);
            col.addView(n);
            String pos = w.optString("pos", Board.defaultPos(type));
            TextView where = text(Board.POS_NAMES[Math.max(0, Arrays.asList(Board.POS).indexOf(pos))], 12.5f, DIM, false);
            col.addView(where);
            head.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
            item.addView(head);
            LinearLayout pr = row();
            for (int k = 0; k < Board.POS.length; k++) {
                final String pv = Board.POS[k];
                TextView c = chip(Board.POS_NAMES[k], pv.equals(pos));
                c.setOnClickListener(v -> {
                    try {
                        ws.get(index).put("pos", pv);
                    } catch (JSONException ignored) { }
                    saveWidgets(ws, true);
                });
                pr.addView(c);
            }
            item.addView(scrollRow(pr));
            switch (type) {
                case "clock":
                    item.addView(switchRow("Seconds", w.optBoolean("seconds", false), x -> {
                        try {
                            ws.get(index).put("seconds", x);
                        } catch (JSONException ignored) { }
                        saveWidgets(ws, false);
                    }));
                    break;
                case "world": {
                    LinearLayout cr = row();
                    String tz = w.optString("tz", "Europe/London");
                    for (String[] city : Board.CITIES) {
                        TextView c = chip(city[0], city[1].equals(tz));
                        c.setOnClickListener(v -> {
                            try {
                                ws.get(index).put("tz", city[1]).remove("city");
                            } catch (JSONException ignored) { }
                            saveWidgets(ws, true);
                        });
                        cr.addView(c);
                    }
                    item.addView(scrollRow(cr));
                    break;
                }
                case "countdown": {
                    EditText title = edit(w.optString("title", "New Year"), "What are you counting down to?");
                    EditText date = edit(w.optString("date", ""), "Date, e.g. 2026-12-25 (empty = 1 January)");
                    item.addView(title);
                    item.addView(date);
                    LinearLayout sr = row();
                    sr.setGravity(Gravity.END);
                    sr.addView(small("Save", v -> {
                        String ds = date.getText().toString().trim();
                        if (!ds.isEmpty() && !ds.matches("\\d{4}-\\d{2}-\\d{2}")) {
                            status("Write the date like 2026-12-25.", BAD);
                            return;
                        }
                        try {
                            ws.get(index).put("title", title.getText().toString().trim()).put("date", ds);
                        } catch (JSONException ignored) { }
                        saveWidgets(ws, true);
                    }));
                    item.addView(sr);
                    break;
                }
                case "note":
                case "greeting": {
                    boolean note = "note".equals(type);
                    EditText e = edit(w.optString(note ? "text" : "name", ""), note ? "Your note (up to 4 lines on the TV)" : "Your name (optional)");
                    if (note) {
                        e.setSingleLine(false);
                        e.setMaxLines(4);
                    }
                    item.addView(e);
                    LinearLayout sr = row();
                    sr.setGravity(Gravity.END);
                    sr.addView(small("Save", v -> {
                        try {
                            ws.get(index).put(note ? "text" : "name", e.getText().toString().trim());
                        } catch (JSONException ignored) { }
                        saveWidgets(ws, true);
                    }));
                    item.addView(sr);
                    break;
                }
                default:
                    break;
            }
            LinearLayout act = row();
            act.setGravity(Gravity.END);
            if (i > 0) act.addView(small("↑", v -> {
                ws.add(index - 1, ws.remove(index));
                saveWidgets(ws, true);
            }));
            if (i < ws.size() - 1) act.addView(small("↓", v -> {
                ws.add(index + 1, ws.remove(index));
                saveWidgets(ws, true);
            }));
            act.addView(small("Remove", v -> {
                ws.remove(index);
                saveWidgets(ws, true);
            }));
            item.addView(act);
            LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(-1, -2);
            il.setMargins(0, px(8), 0, 0);
            on.addView(item, il);
        }
        if (raw.has("widgets")) {
            LinearLayout rr = row();
            rr.setGravity(Gravity.END);
            rr.addView(small("↺  The theme's own widgets", v -> {
                remove("widgets");
                refreshTab();
            }));
            on.addView(rr);
        }
        page.addView(on);

        LinearLayout add = card("Add a widget");
        add.addView(note("Tap one to put it on the TV. You can move it to any corner after."));
        LinearLayout line = null;
        for (int k = 0; k < Board.TYPES.length; k++) {
            if (k % 2 == 0) {
                line = row();
                add.addView(line);
            }
            String type = Board.TYPES[k];
            JSONObject w = new JSONObject();
            try {
                w.put("type", type).put("pos", Board.defaultPos(type));
                if ("note".equals(type)) w.put("text", "Movie night on Friday");
                if ("world".equals(type)) w.put("tz", "Europe/London");
                if ("countdown".equals(type)) w.put("title", "New Year");
            } catch (JSONException ignored) { }
            GlassCard cell = new GlassCard(this, px(18), 0x14FFFFFF);
            cell.setPadding(px(6), px(6), px(6), px(8));
            cell.addView(new WidgetSample(this, m, w, previewWall(m)), new LinearLayout.LayoutParams(-1, px(84)));
            TextView n = text(Board.NAMES[k], 13.5f, TEXT, true);
            n.setPadding(px(4), px(6), px(4), 0);
            cell.addView(n);
            TextView ab = text(Board.ABOUT[k], 11.5f, DIM, false);
            ab.setPadding(px(4), px(1), px(4), 0);
            cell.addView(ab);
            cell.setOnClickListener(v -> {
                List<JSONObject> now = widgetList();
                now.add(w);
                saveWidgets(now, true);
                status(Board.NAMES[Arrays.asList(Board.TYPES).indexOf(type)] + " added to the TV.", GOOD);
            });
            Fx.press(cell);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
            lp.setMargins(px(4), px(4), px(4), px(4));
            line.addView(cell, lp);
        }
        if (line != null && line.getChildCount() == 1) line.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        page.addView(add);

        LinearLayout look = card("Widget look");
        look.addView(label("Style"));
        look.addView(choices("widget_style", Board.STYLE_NAMES, Board.STYLES, false));
        look.addView(label("Size"));
        look.addView(slider("widget_size", 60, 160, "%", null));
        look.addView(reset("widget_style", "widget_size"));
        page.addView(look);
    }

    /** Every icon pack shown with three of your apps; tap one to use it. */
    private View packPicker() {
        LinearLayout r = row();
        JSONObject m = merged();
        String cur = m.optString("icon_style", "original");
        List<HomePreview.Entry> sample = dockEntries(m);
        if (sample.size() > 3) sample = sample.subList(0, 3);
        Bitmap wall = previewWall(m);
        String fixed = m.optString("accent", "auto");
        int wallAccent = wall != null ? Noir.accent(wall) : 0;
        int accent = "auto".equals(fixed) ? wallAccent : Faces.parse(fixed, wallAccent);
        if (accent == 0) accent = 0xFF39E6FF;
        for (int k = 0; k < Packs.IDS.length; k++) {
            String id = Packs.IDS[k];
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            ImageView iv = new ImageView(this);
            iv.setImageBitmap(packSample(id, sample, accent));
            iv.setBackground(round(0xFF05070A, px(10), id.equals(cur) ? ACCENT : LINE, px(id.equals(cur) ? 3 : 1)));
            iv.setPadding(px(3), px(3), px(3), px(3));
            cell.addView(iv, new LinearLayout.LayoutParams(px(132), px(56)));
            TextView n = text(Packs.NAMES[k], 12, id.equals(cur) ? ACCENT : TEXT, true);
            n.setGravity(Gravity.CENTER);
            n.setPadding(0, px(4), 0, 0);
            cell.addView(n, new LinearLayout.LayoutParams(px(132), -2));
            cell.setOnClickListener(v -> {
                put("icon_style", id);
                refreshTab();
            });
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-2, -2);
            cl.setMargins(0, px(4), px(10), px(4));
            r.addView(cell, cl);
        }
        return scrollRow(r);
    }

    private Bitmap packSample(String id, List<HomePreview.Entry> apps, int accent) {
        int w = 396, h = 150;
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        android.graphics.Canvas c = new android.graphics.Canvas(out);
        int n = Math.max(1, apps.size());
        float slot = w / (float) n;
        for (int i = 0; i < apps.size(); i++) {
            HomePreview.Entry e = apps.get(i);
            float cx = slot * i + slot / 2f, cy = h / 2f;
            android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG
                    | android.graphics.Paint.ANTI_ALIAS_FLAG);
            if ("banner".equals(id)) {
                Bitmap b = e.banner != null ? e.banner : e.icon;
                if (b == null) continue;
                float bw = slot * 0.88f, bh = bw * 0.5625f;
                android.graphics.RectF r = new android.graphics.RectF(cx - bw / 2, cy - bh / 2, cx + bw / 2, cy + bh / 2);
                android.graphics.Path path = new android.graphics.Path();
                path.addRoundRect(r, bh * 0.16f, bh * 0.16f, android.graphics.Path.Direction.CW);
                c.save();
                c.clipPath(path);
                c.drawBitmap(b, null, r, p);
                c.restore();
                continue;
            }
            if ("original".equals(id)) {
                if (e.icon == null) continue;
                float s = h * 0.62f;
                android.graphics.RectF r = new android.graphics.RectF(cx - s / 2, cy - s / 2, cx + s / 2, cy + s / 2);
                android.graphics.Path path = new android.graphics.Path();
                path.addRoundRect(r, s * 0.23f, s * 0.23f, android.graphics.Path.Direction.CW);
                c.save();
                c.clipPath(path);
                c.drawBitmap(e.icon, null, r, p);
                c.restore();
                continue;
            }
            if (e.icon == null) continue;
            Bitmap[] base = logos.get(e.pkg + ":mask");
            Bitmap mask = base == null ? null : base[0];
            if (mask == null) {
                mask = Noir.whiteLogo(new android.graphics.drawable.BitmapDrawable(getResources(), e.icon), 144);
                logos.put(e.pkg + ":mask", new Bitmap[]{mask});
            }
            if (mask == null) continue;
            Bitmap pack = Packs.render(id, mask, i, apps.size());
            float s = h * ("bubble".equals(id) || "badge".equals(id) ? 0.86f : 0.7f);
            int tint = "rainbow".equals(id) ? Packs.hue(i, apps.size()) : accent;
            if (Packs.haloed(id) && i == 0) {
                Bitmap halo = Noir.halo(mask);
                if (halo != null) {
                    android.graphics.Paint hp = new android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG);
                    hp.setColorFilter(new android.graphics.PorterDuffColorFilter(tint, android.graphics.PorterDuff.Mode.SRC_IN));
                    float hs = s * 1.5f;
                    c.drawBitmap(halo, null, new android.graphics.RectF(cx - hs / 2, cy - hs / 2, cx + hs / 2, cy + hs / 2), hp);
                }
            }
            if (Packs.tinted(id)) p.setColorFilter(new android.graphics.PorterDuffColorFilter(accent, android.graphics.PorterDuff.Mode.SRC_IN));
            c.drawBitmap(pack, null, new android.graphics.RectF(cx - s / 2, cy - s / 2, cx + s / 2, cy + s / 2), p);
        }
        return out;
    }

    private View buttonsEditor() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        String[] all = {"memory", "inputs", "settings", "wifi", "bluetooth", "music", "profile"};
        String[] names = {"RAM manager", "Inputs (HDMI)", "Settings", "Wi-Fi", "Bluetooth", "Music", "Accounts"};
        Runnable[] fill = new Runnable[1];
        fill[0] = () -> {
            box.removeAllViews();
            List<String> on = list(merged(), "buttons", "memory", "inputs", "settings");
            List<String> order = new ArrayList<>(on);
            for (String a : all) if (!order.contains(a)) order.add(a);
            for (String b : order) {
                int idx = Arrays.asList(all).indexOf(b);
                if (idx < 0) continue;
                LinearLayout r = row();
                r.setGravity(Gravity.CENTER_VERTICAL);
                View s = switchRow(names[idx], on.contains(b), checked -> {
                    List<String> cur = list(merged(), "buttons", "memory", "inputs", "settings");
                    if (checked && !cur.contains(b)) cur.add(b);
                    if (!checked) cur.remove(b);
                    putList("buttons", cur);
                    fill[0].run();
                });
                r.addView(s, new LinearLayout.LayoutParams(0, -2, 1f));
                if (on.contains(b)) {
                    r.addView(small("▲", v -> moveButton(b, -1, fill[0])));
                    r.addView(small("▼", v -> moveButton(b, 1, fill[0])));
                }
                box.addView(r);
            }
        };
        fill[0].run();
        return box;
    }

    private void moveButton(String b, int by, Runnable after) {
        List<String> cur = list(merged(), "buttons", "memory", "inputs", "settings");
        int i = cur.indexOf(b);
        int j = i + by;
        if (i < 0 || j < 0 || j >= cur.size()) return;
        cur.remove(i);
        cur.add(j, b);
        putList("buttons", cur);
        after.run();
    }

    private View appPicker(String key, String noneLabel) {
        LinearLayout r = row();
        String cur = merged().optString(key, "");
        TextView t = chip(cur.isEmpty() ? noneLabel : appName(cur), true);
        t.setOnClickListener(v -> {
            List<String> labels = new ArrayList<>();
            List<String> pkgs = new ArrayList<>();
            labels.add(noneLabel);
            pkgs.add("");
            for (Tv.App a : homeApps) {
                labels.add(a.label);
                pkgs.add(a.pkg);
            }
            new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle("Choose an app").setItems(labels.toArray(new String[0]), (d, w) -> {
                put(key, pkgs.get(w));
                t.setText(labels.get(w));
            }).show();
        });
        r.addView(t);
        return r;
    }

    private String appName(String pkg) {
        Tv.App a = app(pkg);
        return a != null ? a.label : Names.of(pkg);
    }

    // ------------------------------------------------------------------ Dock apps tab

    private void buildDock(boolean withPreview) {
        if (withPreview) page.addView(homePreview());
        LinearLayout c = card("Apps on the home screen");
        c.addView(note("In this order on the TV. Up to 7 fit without scrolling."));
        LinearLayout listBox = new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
        c.addView(listBox);
        Runnable[] fill = new Runnable[1];
        fill[0] = () -> {
            listBox.removeAllViews();
            List<String> dock = list(merged(), "dock", DEFAULT_DOCK);
            for (int i = 0; i < dock.size(); i++) {
                final int index = i;
                String p = dock.get(i);
                LinearLayout r = row();
                r.setGravity(Gravity.CENTER_VERTICAL);
                ImageView iv = new ImageView(this);
                Bitmap b = icons.get(p);
                if (b != null) iv.setImageBitmap(b);
                else iv.setBackground(round(0xFF2A2F3A, px(8), LINE, 0));
                LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(px(40), px(40));
                il.setMargins(0, 0, px(12), 0);
                iv.setOutlineProvider(new android.view.ViewOutlineProvider() {
                    @Override
                    public void getOutline(View view, android.graphics.Outline o) {
                        o.setRoundRect(0, 0, view.getWidth(), view.getHeight(), px(9));
                    }
                });
                iv.setClipToOutline(true);
                r.addView(iv, il);
                TextView n = text(appName(p), 16, app(p) == null && !homeApps.isEmpty() ? DIM : TEXT, true);
                r.addView(n, new LinearLayout.LayoutParams(0, -2, 1f));
                r.addView(small("▲", v -> moveDock(index, -1, fill[0])));
                r.addView(small("▼", v -> moveDock(index, 1, fill[0])));
                r.addView(small("✕", v -> {
                    List<String> d = list(merged(), "dock", DEFAULT_DOCK);
                    d.remove(index);
                    putList("dock", d);
                    fill[0].run();
                }));
                listBox.addView(r);
            }
        };
        fill[0].run();
        LinearLayout r = row();
        r.addView(button("Add apps", true, v -> addDockApps(fill[0])));
        r.addView(button("Default 6", false, v -> {
            remove("dock");
            fill[0].run();
        }));
        c.addView(r);
        c.addView(toggle("Also show every other app after these", "show_other_apps"));
        page.addView(c);

        List<String> hidden = list(merged(), "hidden");
        if (!hidden.isEmpty()) {
            LinearLayout h = card("Hidden apps");
            for (String p : hidden) {
                LinearLayout hr = row();
                hr.setGravity(Gravity.CENTER_VERTICAL);
                hr.addView(text(appName(p), 15, TEXT, false), new LinearLayout.LayoutParams(0, -2, 1f));
                hr.addView(small("Show again", v -> {
                    List<String> hl = list(merged(), "hidden");
                    hl.remove(p);
                    putList("hidden", hl);
                    refreshTab();
                }));
                h.addView(hr);
            }
            page.addView(h);
        }
        if (homeApps.isEmpty()) page.addView(note("Connect to the TV to see all its apps (More > TV and connection)."));
    }

    private void moveDock(int i, int by, Runnable after) {
        List<String> d = list(merged(), "dock", DEFAULT_DOCK);
        int j = i + by;
        if (j < 0 || j >= d.size()) return;
        String p = d.remove(i);
        d.add(j, p);
        putList("dock", d);
        after.run();
    }

    private void addDockApps(Runnable after) {
        if (homeApps.isEmpty()) {
            toast("Connect to the TV first to see its apps.");
            return;
        }
        List<String> dock = list(merged(), "dock", DEFAULT_DOCK);
        List<Tv.App> choices = new ArrayList<>();
        for (Tv.App a : homeApps) if (!dock.contains(a.pkg)) choices.add(a);
        String[] labels = new String[choices.size()];
        boolean[] on = new boolean[choices.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = choices.get(i).label + (choices.get(i).tv ? "" : "  (phone app)");
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle("Add to the home screen")
                .setMultiChoiceItems(labels, on, (d, w, c) -> on[w] = c)
                .setPositiveButton("Add", (d, w) -> {
                    List<String> cur = list(merged(), "dock", DEFAULT_DOCK);
                    for (int i = 0; i < on.length; i++) if (on[i]) cur.add(choices.get(i).pkg);
                    putList("dock", cur);
                    after.run();
                }).setNegativeButton("Cancel", null).show();
    }

    // ------------------------------------------------------------------ Wallpapers tab

    private void buildWalls() {
        page.addView(homePreview());
        LinearLayout s = card("Changing");
        s.addView(label("New wallpaper every"));
        s.addView(choices("wallpaper_minutes", new String[]{"Never", "1 min", "5 min", "10 min", "30 min", "1 hour", "1 day"},
                new Object[]{0, 1, 5, 10, 30, 60, 1440}, false));
        s.addView(choices("wallpaper_order", new String[]{"Shuffle", "In order"}, new Object[]{"shuffle", "sequential"}, false));
        s.addView(toggle("Only dark wallpapers", "dark_only"));
        String fixed = merged().optString("wallpaper_fixed", "");
        if (!fixed.isEmpty()) {
            LinearLayout fr = row();
            fr.setGravity(Gravity.CENTER_VERTICAL);
            fr.addView(text("Always showing: " + fixed, 14, ACCENT, true), new LinearLayout.LayoutParams(0, -2, 1f));
            fr.addView(small("Let them change", v -> {
                remove("wallpaper_fixed");
                refreshTab();
            }));
            s.addView(fr);
        }
        page.addView(s);

        LinearLayout c = card("On the TV (" + walls.size() + ")");
        LinearLayout r = row();
        r.addView(button("Add from phone", true, v -> pickPictures()));
        r.addView(button("Refresh", false, v -> {
            if (connected) syncFromTv(false);
            else status("Connect first (More > TV and connection).", BAD);
        }));
        c.addView(r);
        c.addView(note("Tap a picture for options. ● dark (shown by dark-only themes)."));
        int cols = 3;
        LinearLayout line = null;
        int i = 0;
        for (Tv.Wall w : walls) {
            if (i % cols == 0) {
                line = row();
                c.addView(line);
            }
            i++;
            FrameLayout cell = new FrameLayout(this);
            ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            Bitmap b = thumbs.get(w.name);
            if (b != null) iv.setImageBitmap(b);
            iv.setBackgroundColor(0xFF000000);
            cell.addView(iv, new FrameLayout.LayoutParams(-1, -1));
            boolean dark = w.brightness >= 0 && w.brightness < merged().optInt("dark_level", 45);
            boolean isFixed = w.name.equals(fixed), isNow = w.name.equals(currentWall);
            TextView tag = text((dark ? "● " : "") + w.name + (isFixed ? "  ★" : isNow ? "  ◉" : ""), 11, Color.WHITE, true);
            tag.setShadowLayer(4, 0, 1, Color.BLACK);
            tag.setPadding(px(5), px(3), px(5), px(3));
            FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(-2, -2);
            tl.gravity = Gravity.BOTTOM | Gravity.START;
            cell.addView(tag, tl);
            cell.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override
                public void getOutline(View view, android.graphics.Outline o) {
                    o.setRoundRect(0, 0, view.getWidth(), view.getHeight(), px(8));
                }
            });
            cell.setClipToOutline(true);
            if (isFixed || isNow) cell.setForeground(round(0, px(8), ACCENT, px(isFixed ? 3 : 2)));
            cell.setOnClickListener(v -> wallActions(w));
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, px(64), 1f);
            cl.setMargins(px(3), px(3), px(3), px(3));
            line.addView(cell, cl);
        }
        if (line != null) {
            for (int k = i % cols; k != 0 && k < cols; k++) {
                View sp = new View(this);
                line.addView(sp, new LinearLayout.LayoutParams(0, px(64), 1f));
            }
        }
        if (walls.isEmpty()) c.addView(note(connected ? "No wallpapers yet. Add some from the phone." : "Connect to see the TV's wallpapers."));
        page.addView(c);

        LinearLayout st = card("Starter wallpapers by FreeTV");
        st.addView(note("Made right here on the phone: dark aurora skies with glass spheres, in 8 colour moods. "
                + "Tap one to send it, or send them all."));
        LinearLayout strip = row();
        for (int k = 0; k < Starter.count(); k++) {
            final int idx = k;
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            ImageView iv = new ImageView(this);
            iv.setImageBitmap(Starter.draw(k, 320, 180));
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override
                public void getOutline(View view, android.graphics.Outline o) {
                    o.setRoundRect(0, 0, view.getWidth(), view.getHeight(), px(12));
                }
            });
            iv.setClipToOutline(true);
            cell.addView(iv, new LinearLayout.LayoutParams(px(144), px(81)));
            TextView n = text(Starter.NAMES[k], 12, DIM, true);
            n.setPadding(px(2), px(5), 0, 0);
            cell.addView(n);
            cell.setOnClickListener(v -> work("Sending " + Starter.NAMES[idx] + "...", () -> {
                tv.addWall(Starter.make(idx, 1920, 1080), String.format(Locale.ROOT, "freetv-%02d.jpg", idx + 1));
                return Starter.NAMES[idx] + " is on the TV.";
            }, () -> syncFromTv(false)));
            Fx.press(cell);
            LinearLayout.LayoutParams cl2 = new LinearLayout.LayoutParams(-2, -2);
            cl2.setMargins(0, px(4), px(10), px(4));
            strip.addView(cell, cl2);
        }
        st.addView(scrollRow(strip));
        LinearLayout sr = row();
        sr.addView(button("Send all 8 to the TV", true, v -> work("Making and sending 8 wallpapers...", () -> {
            for (int k = 0; k < Starter.count(); k++) {
                tv.addWall(Starter.make(k, 1920, 1080), String.format(Locale.ROOT, "freetv-%02d.jpg", k + 1));
            }
            return "8 starter wallpapers are on the TV.";
        }, () -> syncFromTv(false))));
        st.addView(sr);
        page.addView(st);
    }

    private void wallActions(Tv.Wall w) {
        String[] items = {"Show this one now and keep it", "Use it for the previews", "Delete from the TV"};
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle(w.name).setItems(items, (d, which) -> {
            if (which == 0) {
                put("wallpaper_fixed", w.name);
                currentWall = w.name;
                refreshTab();
            } else if (which == 1) {
                currentWall = w.name;
                refreshTab();
            } else {
                confirm("Delete " + w.name + " from the TV?", "It is removed from the TV for good.", () ->
                        work("Deleting...", () -> {
                            tv.deleteWall(w.name);
                            //noinspection ResultOfMethodCallIgnored
                            new File(new File(cache, "thumbs"), w.name + ".jpg").delete();
                            return "Deleted " + w.name + ".";
                        }, () -> {
                            if (w.name.equals(merged().optString("wallpaper_fixed", ""))) remove("wallpaper_fixed");
                            syncFromTv(false);
                        }));
            }
        }).show();
    }

    private void pickPictures() {
        if (!connected) {
            status("Connect to the TV first (More > TV and connection).", BAD);
            return;
        }
        Intent i;
        if (Build.VERSION.SDK_INT >= 33) {
            i = new Intent(MediaStore.ACTION_PICK_IMAGES);
            i.putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, Math.min(50, MediaStore.getPickImagesMaxLimit()));
        } else {
            i = new Intent(Intent.ACTION_GET_CONTENT);
            i.setType("image/*");
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        }
        try {
            startActivityForResult(i, PICK);
        } catch (RuntimeException e) {
            toast("No picture picker on this phone.");
        }
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == PICK_APK && result == RESULT_OK && data != null && data.getData() != null) {
            prepareApk(data.getData());
            return;
        }
        if (request != PICK || result != RESULT_OK || data == null) return;
        List<Uri> uris = new ArrayList<>();
        ClipData clip = data.getClipData();
        if (clip != null) for (int i = 0; i < clip.getItemCount(); i++) uris.add(clip.getItemAt(i).getUri());
        else if (data.getData() != null) uris.add(data.getData());
        File out = new File(getCacheDir(), "outbox");
        //noinspection ResultOfMethodCallIgnored
        out.mkdirs();
        File[] old = out.listFiles();
        if (old != null) for (File f : old) //noinspection ResultOfMethodCallIgnored
            f.delete();
        work("Preparing " + uris.size() + " pictures...", () -> {
            int ok = 0;
            String stamp = new SimpleDateFormat("yyMMdd-HHmmss", Locale.ROOT).format(new Date());
            for (Uri u : uris) {
                if (!Images.add(getContentResolver(), u, out)) continue;
                List<File> made = Images.list(out);
                File f = made.get(made.size() - 1);
                String name = "m" + stamp + "-" + (ok + 1) + ".jpg";
                byte[] jpg = AdbLink.readAll(f);
                tv.addWall(jpg, name);
                Bitmap small = decodeSmall(jpg, 320);
                if (small != null) {
                    java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
                    small.compress(Bitmap.CompressFormat.JPEG, 80, o);
                    File tdir = new File(cache, "thumbs");
                    //noinspection ResultOfMethodCallIgnored
                    tdir.mkdirs();
                    write(new File(tdir, name + ".jpg"), o.toByteArray());
                }
                //noinspection ResultOfMethodCallIgnored
                f.delete();
                ok++;
                final int n = ok;
                main.post(() -> status("Sent " + n + " of " + uris.size() + " to the TV...", DIM));
            }
            return "Added " + ok + " wallpapers to the TV.";
        }, () -> syncFromTv(false));
    }

    // ------------------------------------------------------------------ Screensaver tab

    private void buildSaver() {
        JSONObject m = merged();
        String face = m.optString("face", "glass");
        String setting = m.optString("screensaver", "auto");
        String theme = m.optString("theme", "glass");

        LinearLayout top = card("Screensaver: " + Faces.NAMES[Math.max(0, Arrays.asList(Faces.IDS).indexOf(face))]
                + ("auto".equals(setting) ? " (the theme's own)" : ""));
        FacePreview big = new FacePreview(this);
        Runnable upd = () -> {
            JSONObject mm = merged();
            big.set(mm.optString("face", "glass"), mm, previewWall(mm), slides(mm));
        };
        upd.run();
        onChange.add(upd);
        top.addView(big);
        LinearLayout tr = row();
        tr.addView(button("Show it on the TV", true, v -> {
            if (dirty) sendNow(false);
            work("Opening it on the TV...", () -> {
                Thread.sleep(dirty ? 1500 : 0);
                tv.testFace(merged().optString("face", "glass"));
                return "Showing on the TV. Any button on the remote closes it.";
            }, null);
        }));
        top.addView(tr);
        page.addView(top);

        LinearLayout pick = card("Choose");
        TextView auto = chip("Theme's own (" + Faces.NAMES[Arrays.asList(Faces.IDS).indexOf(Themes.face(theme))] + ")", "auto".equals(setting));
        auto.setOnClickListener(v -> {
            put("screensaver", "auto");
            refreshTab();
        });
        pick.addView(auto);
        LinearLayout line = null;
        for (int i = 0; i < Faces.IDS.length; i++) {
            if (i % 2 == 0) {
                line = row();
                pick.addView(line);
            }
            String id = Faces.IDS[i];
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            FacePreview f = new FacePreview(this);
            f.set(id, m, previewWall(m), slides(m));
            f.setSelected2(id.equals(face));
            cell.addView(f);
            TextView n = text(Faces.NAMES[i], 13, id.equals(face) ? ACCENT : TEXT, true);
            n.setPadding(px(2), px(4), 0, px(6));
            cell.addView(n);
            cell.setOnClickListener(v -> {
                put("screensaver", id);
                refreshTab();
            });
            f.setOnClickListener(v -> {
                put("screensaver", id);
                refreshTab();
            });
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, -2, 1f);
            cl.setMargins(px(4), px(4), px(4), px(4));
            line.addView(cell, cl);
        }
        page.addView(pick);

        LinearLayout d = card("Details of " + Faces.NAMES[Math.max(0, Arrays.asList(Faces.IDS).indexOf(face))]);
        d.addView(faceToggle(face, "Wallpaper behind it", "ss_wallpaper"));
        d.addView(label("Darken the wallpaper"));
        d.addView(faceSlider(face, "ss_dim", 0, 95, "%"));
        boolean dial = "analog".equals(face) || "neon".equals(face);
        d.addView(label(dial ? "Watch size" : "Clock size"));
        d.addView(faceSlider(face, "ss_clock_size", dial ? 25 : 4, dial ? 95 : 40, "%"));
        d.addView(label("Height on screen"));
        d.addView(faceSlider(face, "ss_clock_top", 8, 92, "%"));
        d.addView(label("Position"));
        d.addView(faceChoices(face, "ss_align", new String[]{"Left", "Centre", "Right"}, new Object[]{"left", "center", "right"}));
        if (!"analog".equals(face)) {
            d.addView(label("Font"));
            d.addView(faceChoices(face, "ss_font", new String[]{"Thin", "Light", "Regular", "Medium", "Bold", "Condensed"},
                    new Object[]{"thin", "light", "regular", "medium", "bold", "condensed"}));
        }
        d.addView(label("Colour"));
        d.addView(faceColors(face));
        d.addView(faceToggle(face, "Date", "ss_date"));
        d.addView(faceToggle(face, "analog".equals(face) ? "Seconds hand" : "Seconds", "ss_seconds"));
        if (dial) d.addView(faceToggle(face, "Smooth sweeping seconds", "ss_smooth"));
        if ("analog".equals(face)) d.addView(faceToggle(face, "Numbers 12, 3, 6, 9", "ss_numerals"));
        d.addView(faceToggle(face, "Sunrise and sunset", "ss_sun"));
        d.addView(faceToggle(face, "Temperature and weather", "ss_weather"));
        d.addView(faceToggle(face, "Floating bubbles", "ss_bubbles"));
        if ("photo".equals(face)) {
            d.addView(label("Next photo every"));
            d.addView(faceSlider(face, "ss_photo_seconds", 5, 120, " s"));
        }
        LinearLayout rr = row();
        rr.addView(small("Reset this screensaver", v -> {
            JSONObject all = raw.optJSONObject("faces");
            if (all != null) all.remove(face);
            changed();
            refreshTab();
        }));
        d.addView(rr);
        page.addView(d);

        LinearLayout p = card("When it starts");
        Widgets.Switch on = new Widgets.Switch(this, true);
        LinearLayout onRow = new LinearLayout(this);
        onRow.setGravity(Gravity.CENTER_VERTICAL);
        onRow.setPadding(0, px(10), 0, px(10));
        onRow.addView(text("Screensaver on", 15.5f, TEXT, false), new LinearLayout.LayoutParams(0, -2, 1f));
        onRow.addView(on);
        p.addView(onRow);
        TextView minsLabel = label("Start after 5 min without a button press");
        p.addView(minsLabel);
        SeekBar mins = glassSeek(29, 4);
        mins.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar b, int v, boolean u) { minsLabel.setText("Start after " + (v + 1) + " min without a button press"); }
            @Override public void onStartTrackingTouch(SeekBar b) { }
            @Override public void onStopTrackingTouch(SeekBar b) { }
        });
        p.addView(mins);
        Widgets.Switch never = new Widgets.Switch(this, true);
        LinearLayout neverRow = new LinearLayout(this);
        neverRow.setGravity(Gravity.CENTER_VERTICAL);
        neverRow.setPadding(0, px(10), 0, px(10));
        neverRow.addView(text("TV never turns itself off", 15.5f, TEXT, false), new LinearLayout.LayoutParams(0, -2, 1f));
        neverRow.addView(never);
        p.addView(neverRow);
        LinearLayout pr = row();
        pr.addView(button("Save on the TV", true, v -> work("Saving...",
                () -> tv.setPower(on.isOn(), mins.getProgress() + 1, never.isOn()), null)));
        p.addView(pr);
        page.addView(p);
        if (connected) {
            worker.execute(() -> {
                try {
                    Tv.Power pw = tv.power();
                    main.post(() -> {
                        on.set(pw.saverOn && pw.ours, false);
                        mins.setProgress(Math.max(0, Math.min(29, pw.minutes - 1)));
                        never.set(pw.neverOff, false);
                    });
                } catch (IOException ignored) { }
            });
        }
    }

    private Object faceValue(String face, String key) {
        return Faces.value(face, merged(), key);
    }

    private View faceToggle(String face, String label, String key) {
        Object v = faceValue(face, key);
        boolean on = v instanceof Boolean ? (Boolean) v : "true".equals(String.valueOf(v));
        return switchRow(label, on, x -> putFace(face, key, x));
    }

    private View faceSlider(String face, String key, int min, int max, String unit) {
        LinearLayout r = row();
        r.setGravity(Gravity.CENTER_VERTICAL);
        int cur;
        try {
            cur = Math.round(Float.parseFloat(String.valueOf(faceValue(face, key))));
        } catch (RuntimeException e) {
            cur = min;
        }
        cur = Math.max(min, Math.min(max, cur));
        SeekBar s = glassSeek(max - min, cur - min);
        TextView v = valueBubble(cur + unit);
        s.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar b, int p, boolean user) {
                v.setText((p + min) + unit);
                if (user) putFaceQuiet(face, key, p + min);
            }
            @Override public void onStartTrackingTouch(SeekBar b) { Fx.tick(b); }
            @Override public void onStopTrackingTouch(SeekBar b) { putFace(face, key, b.getProgress() + min); }
        });
        r.addView(s, new LinearLayout.LayoutParams(0, -2, 1f));
        r.addView(v);
        return r;
    }

    /** While dragging: preview follows, but the TV is only sent the value on release. */
    private void putFaceQuiet(String face, String key, Object value) {
        try {
            JSONObject all = raw.optJSONObject("faces");
            if (all == null) {
                all = new JSONObject();
                raw.put("faces", all);
            }
            JSONObject mine = all.optJSONObject(face);
            if (mine == null) {
                mine = new JSONObject();
                all.put(face, mine);
            }
            mine.put(key, value);
        } catch (JSONException ignored) { }
        for (Runnable r : new ArrayList<>(onChange)) r.run();
    }

    private View faceChoices(String face, String key, String[] labels, Object[] values) {
        LinearLayout r = row();
        Object cur = faceValue(face, key);
        List<TextView> chips = new ArrayList<>();
        for (int i = 0; i < labels.length; i++) {
            final Object value = values[i];
            TextView c = chip(labels[i], String.valueOf(value).equals(String.valueOf(cur)));
            c.setOnClickListener(v -> {
                putFace(face, key, value);
                for (TextView t : chips) styleChip(t, t == c);
            });
            chips.add(c);
            r.addView(c);
        }
        return scrollRow(r);
    }

    private View faceColors(String face) {
        LinearLayout r = row();
        String cur = String.valueOf(faceValue(face, "ss_color"));
        List<View> all = new ArrayList<>();
        String[] names = {"Auto", "White", "Accent"};
        String[] vals = {"auto", "white", "accent"};
        for (int i = 0; i < names.length; i++) {
            final String val = vals[i];
            TextView c = chip(names[i], val.equals(cur));
            c.setOnClickListener(v -> {
                putFace(face, "ss_color", val);
                for (View x : all) if (x instanceof TextView) styleChip((TextView) x, x == c);
            });
            all.add(c);
            r.addView(c);
        }
        for (String hex : SWATCHES) {
            View dot = swatch(hex, hex.equalsIgnoreCase(cur));
            dot.setOnClickListener(v -> {
                putFace(face, "ss_color", hex);
                refreshTab();
            });
            r.addView(dot);
        }
        return scrollRow(r);
    }

    // ------------------------------------------------------------------ TV apps tab

    private String appFilter = "TV apps";
    private String appSearch = "";

    private void buildApps() {
        if (!connected) {
            page.addView(note("Connect to the TV first (More > TV and connection)."));
            return;
        }
        LinearLayout inst = row();
        inst.addView(button("Install an app from this phone", true, v -> { sub = "install"; showTab(5); }));
        page.addView(inst);
        LinearLayout c = card("Apps on the TV");
        c.addView(note("Turn apps off to save RAM (they can be turned back on here). Needed parts of the TV are protected."));
        EditText search = edit(appSearch, "Search");
        c.addView(search);
        LinearLayout filters = row();
        for (String f : new String[]{"TV apps", "Off", "Installed by you", "All"}) {
            TextView ch = chip(f, f.equals(appFilter));
            ch.setOnClickListener(v -> { appFilter = f; refreshTab(); });
            filters.addView(ch);
        }
        c.addView(scrollRow(filters));
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        c.addView(list);
        page.addView(c);
        list.addView(note("Loading..."));
        worker.execute(() -> {
            try {
                List<Tv.Pkg> apps = tv.apps();
                main.post(() -> {
                    Runnable fill = () -> {
                        list.removeAllViews();
                        String q = appSearch.toLowerCase(Locale.ROOT);
                        int shown = 0;
                        for (Tv.Pkg a : apps) {
                            boolean match = appFilter.equals("All") || (appFilter.equals("TV apps") && a.launchable)
                                    || (appFilter.equals("Off") && !a.enabled) || (appFilter.equals("Installed by you") && a.userApp);
                            if (!match) continue;
                            String name = appName(a.pkg);
                            if (!q.isEmpty() && !name.toLowerCase(Locale.ROOT).contains(q) && !a.pkg.contains(q)) continue;
                            list.addView(appRow(a));
                            if (++shown >= 300) break;
                        }
                        if (shown == 0) list.addView(note("Nothing here."));
                    };
                    fill.run();
                    search.addTextChangedListener(new TextWatcher() {
                        @Override public void beforeTextChanged(CharSequence s, int a, int b, int d) { }
                        @Override public void onTextChanged(CharSequence s, int a, int b, int d) { }
                        @Override public void afterTextChanged(Editable s) { appSearch = s.toString(); fill.run(); }
                    });
                });
            } catch (IOException e) {
                main.post(() -> { list.removeAllViews(); status(e.getMessage(), BAD); });
            }
        });
    }

    private View appRow(Tv.Pkg a) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(px(4), px(8), px(4), px(8));
        ImageView iv = new ImageView(this);
        Bitmap b = icons.get(a.pkg);
        if (b != null) iv.setImageBitmap(b);
        LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(px(34), px(34));
        il.setMargins(0, 0, px(10), 0);
        r.addView(iv, il);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView n = text(appName(a.pkg), 16, a.enabled ? TEXT : DIM, true);
        String tag = (a.enabled ? "On" : "Off") + (a.userApp ? " · installed by you" : "") + (Tv.isProtected(a.pkg) ? " · needed by the TV" : "");
        TextView sub = text(a.pkg + "  ·  " + tag, 12, a.enabled ? DIM : BAD, false);
        col.addView(n);
        col.addView(sub);
        r.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
        r.setOnClickListener(v -> appActions(a));
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(r);
        View line = new View(this);
        line.setBackgroundColor(LINE);
        box.addView(line, new LinearLayout.LayoutParams(-1, 1));
        return box;
    }

    private void appActions(Tv.Pkg a) {
        List<String> items = new ArrayList<>();
        if (a.launchable && a.enabled) items.add("Open on the TV");
        if (a.launchable && a.enabled) items.add("Add to the home screen");
        if (!Tv.isProtected(a.pkg)) {
            items.add(a.enabled ? "Turn off" : "Turn on");
            items.add("Stop it now");
            if (a.userApp) items.add("Uninstall");
        }
        if (items.isEmpty()) {
            toast(appName(a.pkg) + " is needed by the TV, so it can't be changed here.");
            return;
        }
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle(appName(a.pkg))
                .setItems(items.toArray(new String[0]), (d, which) -> {
                    String pick = items.get(which);
                    if (pick.startsWith("Open")) {
                        work("Opening...", () -> tv.launch(a.pkg), null);
                    } else if (pick.startsWith("Add")) {
                        List<String> dock = list(merged(), "dock", DEFAULT_DOCK);
                        if (!dock.contains(a.pkg)) dock.add(a.pkg);
                        putList("dock", dock);
                        status(appName(a.pkg) + " added to the home screen.", GOOD);
                    } else if (pick.startsWith("Turn")) {
                        work("Working...", () -> tv.setEnabled(a.pkg, !a.enabled), this::refreshTab);
                    } else if (pick.startsWith("Stop")) {
                        work("Stopping...", () -> tv.stop(a.pkg), null);
                    } else {
                        confirm("Uninstall " + appName(a.pkg) + "?", "It can come back only from the Play Store.",
                                () -> work("Uninstalling...", () -> tv.uninstall(a.pkg), this::refreshTab));
                    }
                }).show();
    }

    // ------------------------------------------------------------------ System tab

    private void buildSystem() {
        LinearLayout c = card("Connection");
        EditText ip = edit(prefs.getString("ip", ""), "TV address, e.g. 192.168.1.20");
        c.addView(ip);
        c.addView(note(connected ? "Connected." : "The TV and phone must be on the same Wi-Fi, with network debugging on in "
                + "the TV's Developer options. The first time, the TV asks to Allow this phone."));
        LinearLayout r = row();
        r.addView(button(connected ? "Reconnect" : "Connect", true, v -> connect(ip.getText().toString().trim())));
        c.addView(r);
        page.addView(c);

        LinearLayout rk = card("Remote buttons");
        rk.addView(note("Give a button on the TV remote a new job. Xiaomi remotes: the PatchWall button."));
        rk.addView(label("PatchWall button opens"));
        JSONObject keys = merged().optJSONObject("remote_keys");
        String curKey = keys == null ? "" : keys.optString("KEYCODE_BUTTON_5", "");
        LinearLayout kr = row();
        List<String[]> opts = new ArrayList<>();
        opts.add(new String[]{"YouTube", "com.google.android.youtube.tv"});
        for (String p : list(merged(), "dock", DEFAULT_DOCK)) {
            if (p.equals("com.google.android.youtube.tv")) continue;
            Tv.App a = app(p);
            opts.add(new String[]{a != null ? a.label : Names.of(p), p});
        }
        opts.add(new String[]{"Nothing", ""});
        for (String[] o : opts) {
            TextView ch = chip(o[0], o[1].equals(curKey));
            ch.setOnClickListener(v -> {
                try {
                    JSONObject m = new JSONObject();
                    if (!o[1].isEmpty()) m.put("KEYCODE_BUTTON_5", o[1]);
                    put("remote_keys", m);
                } catch (JSONException ignored) { }
                status(o[1].isEmpty() ? "The PatchWall button does nothing now." : "The PatchWall button opens " + o[0] + ".", GOOD);
                refreshTab();
            });
            kr.addView(ch);
        }
        rk.addView(scrollRow(kr));
        page.addView(rk);

        LinearLayout s = card("Sending");
        s.addView(switchRow("Send each change to the TV straight away", prefs.getBoolean("autosend", true),
                on -> prefs.edit().putBoolean("autosend", on).apply()));
        LinearLayout sr = row();
        sr.addView(button("Send now", true, v -> sendNow(true)));
        sr.addView(button("Get the TV's settings", false, v -> {
            if (!connected) { status("Connect first.", BAD); return; }
            confirm("Replace the settings on this phone with the TV's?", null, () -> {
                dirty = false;
                styleSend();
                syncFromTv(true);
            });
        }));
        s.addView(sr);
        LinearLayout sr2 = row();
        sr2.addView(button("Refresh apps and wallpapers", false, v -> {
            if (connected) syncFromTv(false);
            else status("Connect first.", BAD);
        }));
        s.addView(sr2);
        page.addView(s);

        LinearLayout ac = card("Close when I press Home");
        ac.addView(note("These apps are closed a few seconds after you go back to the home screen, so they "
                + "don't keep using RAM. They open normally next time (from the start)."));
        LinearLayout acList = new LinearLayout(this);
        acList.setOrientation(LinearLayout.VERTICAL);
        ac.addView(acList);
        Runnable[] fillAc = new Runnable[1];
        fillAc[0] = () -> {
            acList.removeAllViews();
            List<String> cur = list(merged(), "auto_close");
            if (cur.isEmpty()) acList.addView(note("None: apps stay open in the background."));
            for (String p : cur) {
                LinearLayout r2 = row();
                r2.setGravity(Gravity.CENTER_VERTICAL);
                r2.addView(text(p.equals("com.android.tv") ? "Live TV / HDMI app" : appName(p), 15, TEXT, false),
                        new LinearLayout.LayoutParams(0, -2, 1f));
                r2.addView(small("✕", v -> {
                    List<String> l = list(merged(), "auto_close");
                    l.remove(p);
                    putList("auto_close", l);
                    fillAc[0].run();
                }));
                acList.addView(r2);
            }
        };
        fillAc[0].run();
        LinearLayout acr = row();
        acr.addView(button("Add apps", false, v -> {
            List<String> cur = list(merged(), "auto_close");
            List<Tv.App> choices = new ArrayList<>();
            for (Tv.App a : homeApps) if (!cur.contains(a.pkg)) choices.add(a);
            if (choices.isEmpty()) { toast("Connect to the TV first to see its apps."); return; }
            String[] labels = new String[choices.size()];
            boolean[] on = new boolean[choices.size()];
            for (int i = 0; i < labels.length; i++) labels[i] = choices.get(i).label;
            new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle("Close these when I press Home")
                    .setMultiChoiceItems(labels, on, (d, w, ch) -> on[w] = ch)
                    .setPositiveButton("Add", (d, w) -> {
                        List<String> l = list(merged(), "auto_close");
                        for (int i = 0; i < on.length; i++) if (on[i]) l.add(choices.get(i).pkg);
                        putList("auto_close", l);
                        fillAc[0].run();
                    }).setNegativeButton("Cancel", null).show();
        }));
        acr.addView(button("Default list", false, v -> {
            remove("auto_close");
            fillAc[0].run();
        }));
        ac.addView(acr);
        page.addView(ac);

        if (!connected) return;
        LinearLayout h = card("Home screen on the TV");
        TextView mode = text("...", 16, TEXT, true);
        h.addView(mode);
        LinearLayout hr = row();
        hr.addView(button("Glass Home", true, v -> work("Switching...", () -> tv.switchTo("glass"), this::refreshTab)));
        hr.addView(button("Old clean", false, v -> work("Switching...", () -> tv.switchTo("clean"), this::refreshTab)));
        hr.addView(button("Original", false, v -> confirm("Go back to the original Google TV home?",
                "PatchWall and the Google TV home are turned back on.", () -> work("Switching...", () -> tv.switchTo("original"), this::refreshTab))));
        h.addView(hr);
        LinearLayout hr2 = row();
        hr2.addView(button("Restart Glass Home", false, v -> work("Restarting...", () -> { tv.restartHome(); return "Restarted."; }, null)));
        h.addView(hr2);
        page.addView(h);

        LinearLayout m = card("Memory");
        TextView ram = text("RAM ...", 24, TEXT, true), storage = text("Storage ...", 16, DIM, true);
        m.addView(ram);
        m.addView(storage);
        LinearLayout runBox = new LinearLayout(this);
        runBox.setOrientation(LinearLayout.VERTICAL);
        m.addView(runBox);
        LinearLayout mr = row();
        mr.addView(button("Close all apps", true, v -> work("Closing apps...", tv::closeAll, this::refreshTab)));
        mr.addView(button("Clear caches", false, v -> work("Clearing caches...", tv::clearCaches, this::refreshTab)));
        m.addView(mr);
        page.addView(m);

        LinearLayout info = card("TV");
        TextView about = text("", 14, DIM, false);
        info.addView(about);
        page.addView(info);

        worker.execute(() -> {
            try {
                Tv.Status st = tv.status();
                homeNow = st.home;
                List<String> running = tv.running();
                main.post(() -> {
                    ram.setText("RAM " + st.ramUsedPct + "% used  ·  " + st.ramFreeMb + " MB free");
                    storage.setText("Storage " + st.storageUsedPct + "% used  ·  " + gb(st.storageFreeMb) + " free");
                    String home = st.home.startsWith(Tv.GLASS) ? "Glass Home" : st.home.startsWith(Tv.CLEAN) ? "Old clean home"
                            : st.home.startsWith(Tv.STOCK) ? "Original Google TV home" : st.home;
                    mode.setText("Now: " + home);
                    mode.setTextColor(st.home.startsWith(Tv.GLASS) ? GOOD : ACCENT);
                    about.setText(st.model + "  ·  Android " + st.android + "  ·  " + st.ramTotalMb + " MB RAM\nOn screen: "
                            + st.focus.replaceAll(".*\\{[^ ]+ [^ ]+ ", "").replace("}", ""));
                    runBox.removeAllViews();
                    if (!running.isEmpty()) runBox.addView(label("Open now (tap to stop)"));
                    for (String line : running) {
                        String pkg = line.split(" ")[0];
                        TextView t = text(appName(pkg) + "  ·  " + line.substring(pkg.length()).trim(), 14, TEXT, false);
                        t.setPadding(0, px(6), 0, px(6));
                        t.setOnClickListener(v -> work("Stopping...", () -> tv.stop(pkg), this::refreshTab));
                        runBox.addView(t);
                    }
                });
            } catch (IOException e) {
                main.post(() -> status(e.getMessage(), BAD));
            }
        });
    }

    // ------------------------------------------------------------------ installing apps from the phone

    /** An APK handed to FreeTV with Share or Open with, or null. */
    private static Uri sharedApk(Intent i) {
        if (i == null) return null;
        if (Intent.ACTION_SEND.equals(i.getAction())) {
            Object u = i.getParcelableExtra(Intent.EXTRA_STREAM);
            return u instanceof Uri ? (Uri) u : null;
        }
        if (Intent.ACTION_VIEW.equals(i.getAction())) return i.getData();
        return null;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Uri u = sharedApk(intent);
        if (u != null) {
            prepareApk(u);
            sub = "install";
            showTab(5);
        }
    }

    private void pickApk() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/vnd.android.package-archive",
                "application/octet-stream", "application/zip", "application/x-zip-compressed"});
        try {
            startActivityForResult(i, PICK_APK);
        } catch (RuntimeException e) {
            toast("No file picker on this phone.");
        }
    }

    private static String fileName(android.content.ContentResolver cr, Uri u) {
        String name = null;
        try (android.database.Cursor c = cr.query(u, new String[]{android.provider.OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) name = c.getString(0);
        } catch (RuntimeException ignored) { }
        if (name == null) name = u.getLastPathSegment();
        return name == null ? "app.apk" : name;
    }

    /** Copies the chosen file into the app, reads its name, icon and version, then installs when connected. */
    private void prepareApk(Uri uri) {
        if (installing) {
            status("Wait for the app being installed now to finish.", BAD);
            return;
        }
        apkFile = null;
        installOk = null;
        installK = -1f;
        apkIcon = null;
        apkLabel = apkPkg = apkVersion = "";
        apkName = fileName(getContentResolver(), uri);
        installMsg = "Reading " + apkName + "...";
        new Thread(() -> {
            File dir = new File(getCacheDir(), "apk");
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
            File[] old = dir.listFiles();
            if (old != null) for (File o : old) //noinspection ResultOfMethodCallIgnored
                o.delete();
            File f = new File(dir, "send.bin");
            String problem = null;
            try (java.io.InputStream in = getContentResolver().openInputStream(uri); OutputStream o = new FileOutputStream(f)) {
                if (in == null) throw new IOException("can't open it");
                byte[] b = new byte[256 * 1024];
                int n;
                while ((n = in.read(b)) > 0) o.write(b, 0, n);
            } catch (Exception e) {
                problem = "Could not read the file: " + e.getMessage();
            }
            List<java.util.zip.ZipEntry> parts = problem == null ? Tv.splits(f) : null;
            File info = f;
            if (problem == null && parts != null) {
                // a bundle: read the name and icon from its base part
                java.util.zip.ZipEntry best = parts.get(0);
                for (java.util.zip.ZipEntry e : parts) {
                    String n = e.getName().toLowerCase(Locale.ROOT);
                    if (n.endsWith("base.apk") || (!best.getName().toLowerCase(Locale.ROOT).endsWith("base.apk") && e.getSize() > best.getSize())) best = e;
                }
                info = new File(dir, "base.apk");
                try (java.util.zip.ZipFile z = new java.util.zip.ZipFile(f); java.io.InputStream in = z.getInputStream(best);
                     OutputStream o = new FileOutputStream(info)) {
                    byte[] b = new byte[256 * 1024];
                    int n;
                    while ((n = in.read(b)) > 0) o.write(b, 0, n);
                } catch (Exception ignored) { }
            }
            android.content.pm.PackageInfo pi = problem == null ? getPackageManager().getPackageArchiveInfo(info.getPath(), 0) : null;
            if (problem == null && pi == null) problem = apkName + " isn't an app (APK) FreeTV can read.";
            final String why = problem;
            final boolean bundle = parts != null;
            final int partCount = parts == null ? 1 : parts.size();
            String label = "", pkg = "", ver = "";
            android.graphics.drawable.Drawable icon = null;
            if (pi != null) {
                pi.applicationInfo.sourceDir = info.getPath();
                pi.applicationInfo.publicSourceDir = info.getPath();
                label = String.valueOf(getPackageManager().getApplicationLabel(pi.applicationInfo));
                pkg = pi.packageName;
                ver = pi.versionName == null ? "" : pi.versionName;
                try {
                    icon = pi.applicationInfo.loadIcon(getPackageManager());
                } catch (RuntimeException ignored) { }
            }
            final String l = label, p = pkg, v = ver;
            final android.graphics.drawable.Drawable ic = icon;
            main.post(() -> {
                if (why != null) {
                    installOk = false;
                    installMsg = why;
                } else {
                    apkFile = f;
                    apkBundle = bundle;
                    apkLabel = l;
                    apkPkg = p;
                    apkVersion = v;
                    apkIcon = ic;
                    installMsg = connected ? "" : "Connect to the TV to send it (it starts by itself once connected).";
                    if (bundle) apkVersion = v + "  ·  " + partCount + " parts";
                }
                if (currentTab == 5 && "install".equals(sub)) refreshTab();
                if (why == null && connected) startInstall();
            });
        }, "apk-read").start();
    }

    private void startInstall() {
        if (apkFile == null || installing) return;
        if (!connected) {
            status("Connect to the TV first (More > TV and connection).", BAD);
            return;
        }
        installing = true;
        installOk = null;
        installK = 0f;
        installMsg = "Sending to the TV...";
        if (currentTab == 5 && "install".equals(sub)) refreshTab();
        final File f = apkFile;
        final long size = f.length();
        worker.execute(() -> {
            String result;
            try {
                long[] last = {0};
                result = tv.installApk(f, (done, total) -> {
                    long now = System.currentTimeMillis();
                    if (now - last[0] < 120 && done < total) return;
                    last[0] = now;
                    float k = total > 0 ? Math.min(1f, done / (float) total) : 0f;
                    main.post(() -> showInstall(k, "Sending to the TV...  " + gb(done / 1048576) + " of " + gb(Math.max(1, total / 1048576))));
                });
            } catch (Exception e) {
                result = "Copy failed: " + (e.getMessage() == null ? e.toString() : e.getMessage());
                if (link != null && !link.isConnected()) connected = false;
            }
            String problem = Tv.installProblem(result);
            main.post(() -> {
                installing = false;
                installOk = problem == null;
                installMsg = problem == null ? (apkLabel.isEmpty() ? "Installed" : apkLabel) + " is on the TV." : problem;
                if (problem == null) {
                    remember(apkLabel, apkPkg);
                    status(installMsg, GOOD);
                    syncFromTv(false);
                } else {
                    status(problem, BAD);
                }
                if (currentTab == 5 && "install".equals(sub)) refreshTab();
            });
            if (size > 0 && problem == null) {
                //noinspection ResultOfMethodCallIgnored
                f.delete();
            }
        });
    }

    private void showInstall(float k, String text) {
        installK = k;
        installMsg = text;
        if (installFill != null) installFill.setScaleX(Math.max(0.02f, k));
        if (installText != null) installText.setText(text);
    }

    private void remember(String label, String pkg) {
        try {
            JSONArray h = new JSONArray(prefs.getString("installed", "[]"));
            JSONArray out = new JSONArray();
            out.put(new JSONObject().put("label", label).put("pkg", pkg).put("at", System.currentTimeMillis()));
            for (int i = 0; i < h.length() && out.length() < 8; i++) {
                JSONObject o = h.optJSONObject(i);
                if (o != null && !pkg.equals(o.optString("pkg"))) out.put(o);
            }
            prefs.edit().putString("installed", out.toString()).apply();
        } catch (JSONException ignored) { }
    }

    private void buildInstall() {
        installFill = null;
        installText = null;
        LinearLayout top = card(null);
        LinearLayout tr = row();
        tr.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout ib = new FrameLayout(this);
        ib.setBackground(Fx.brand(px(18)));
        ImageView ii = new ImageView(this);
        ii.setImageResource(R.drawable.f_send);
        ii.setImageTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
        FrameLayout.LayoutParams il = new FrameLayout.LayoutParams(px(26), px(26));
        il.gravity = Gravity.CENTER;
        ib.addView(ii, il);
        tr.addView(ib, new LinearLayout.LayoutParams(px(52), px(52)));
        LinearLayout tc = new LinearLayout(this);
        tc.setOrientation(LinearLayout.VERTICAL);
        tc.setPadding(px(14), 0, 0, 0);
        TextView tt = text("Send an app to your TV", 19, TEXT, true);
        tt.setTypeface(Fx.font(700));
        tc.addView(tt);
        tc.addView(text("Over your Wi-Fi. No USB stick, no Downloader app.", 13, DIM, false));
        tr.addView(tc, new LinearLayout.LayoutParams(0, -2, 1f));
        top.addView(tr);
        LinearLayout br = row();
        br.addView(button(apkFile == null ? "Choose an APK on this phone" : "Choose another", true, v -> pickApk()));
        top.addView(br);
        top.addView(note("Works with .apk files and split-app bundles (.apks, .xapk). Tip: in your browser's downloads "
                + "or the Files app, tap Share on an APK and pick FreeTV."));
        page.addView(top);

        if (apkFile != null || !installMsg.isEmpty() || installOk != null) {
            LinearLayout c = card(null);
            LinearLayout ar = row();
            ar.setGravity(Gravity.CENTER_VERTICAL);
            ImageView icon = new ImageView(this);
            if (apkIcon != null) icon.setImageDrawable(apkIcon);
            else icon.setImageResource(R.drawable.f_apps);
            ar.addView(icon, new LinearLayout.LayoutParams(px(56), px(56)));
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setPadding(px(14), 0, 0, 0);
            TextView n = text(apkLabel.isEmpty() ? apkName : apkLabel, 17, TEXT, true);
            n.setTypeface(Fx.font(700));
            col.addView(n);
            String meta = (apkPkg.isEmpty() ? "" : apkPkg) + (apkVersion.isEmpty() ? "" : "  ·  v" + apkVersion)
                    + (apkFile != null ? "  ·  " + gb(Math.max(1, apkFile.length() / 1048576)) : "");
            if (!meta.trim().isEmpty()) col.addView(text(meta.trim(), 12, DIM, false));
            ar.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
            c.addView(ar);
            if (installing || installK >= 0f) {
                FrameLayout track = new FrameLayout(this);
                track.setBackground(Fx.pill(0x26FFFFFF, px(5), 0, 0));
                View fillBar = new View(this);
                fillBar.setBackground(Fx.brand(px(5)));
                fillBar.setPivotX(0f);
                fillBar.setScaleX(Math.max(0.02f, installOk != null ? 1f : installK));
                track.addView(fillBar, new FrameLayout.LayoutParams(-1, -1));
                LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-1, px(10));
                tl.setMargins(0, px(16), 0, px(4));
                c.addView(track, tl);
                installFill = fillBar;
            }
            installText = text(installMsg, 14, installOk == null ? TEXT : installOk ? GOOD : BAD, installOk != null);
            installText.setPadding(0, px(8), 0, px(4));
            if (!installMsg.isEmpty()) c.addView(installText);
            LinearLayout act = row();
            if (Boolean.TRUE.equals(installOk) && !apkPkg.isEmpty()) {
                String pkg = apkPkg;
                act.addView(button("Open on the TV", true, v -> work("Opening...", () -> tv.launch(pkg), null)));
                List<String> dock = list(merged(), "dock", DEFAULT_DOCK);
                if (!dock.contains(pkg)) {
                    act.addView(button("Add to the TV home", false, v -> {
                        List<String> d = list(merged(), "dock", DEFAULT_DOCK);
                        d.add(pkg);
                        putList("dock", d);
                        status((apkLabel.isEmpty() ? "It" : apkLabel) + " is on the TV's home screen.", GOOD);
                        refreshTab();
                    }));
                }
            } else if (Boolean.FALSE.equals(installOk) && apkFile != null) {
                act.addView(button("Try again", true, v -> startInstall()));
            } else if (apkFile != null && !installing && !connected) {
                act.addView(button("Connect a TV", true, v -> { sub = "system"; showTab(5); }));
            }
            if (act.getChildCount() > 0) c.addView(act);
            page.addView(c);
        }

        try {
            JSONArray h = new JSONArray(prefs.getString("installed", "[]"));
            if (h.length() > 0) {
                LinearLayout hc = card("Sent before");
                for (int i = 0; i < h.length(); i++) {
                    JSONObject o = h.optJSONObject(i);
                    if (o == null) continue;
                    String pkg = o.optString("pkg");
                    LinearLayout r = row();
                    r.setGravity(Gravity.CENTER_VERTICAL);
                    LinearLayout cc = new LinearLayout(this);
                    cc.setOrientation(LinearLayout.VERTICAL);
                    cc.addView(text(o.optString("label", pkg), 15, TEXT, true));
                    cc.addView(text(pkg, 11.5f, DIM, false));
                    r.addView(cc, new LinearLayout.LayoutParams(0, -2, 1f));
                    r.addView(small("Open", v -> work("Opening...", () -> tv.launch(pkg), null)));
                    hc.addView(r);
                }
                page.addView(hc);
            }
        } catch (JSONException ignored) { }
    }

    // ------------------------------------------------------------------ More

    private void buildMore() {
        page.addView(menuRow(R.drawable.f_tv, "TV and connection", connected
                ? "Connected to " + prefs.getString("tv_name", "your TV") + " · home screen, memory, sending"
                : "Connect, switch home screens, memory", () -> { sub = "system"; showTab(5); }));
        page.addView(menuRow(R.drawable.f_wand, "Set up a TV", "Install FreeTV's home screen on any Android TV",
                () -> { sub = "setup"; setupStep = 0; showTab(5); }));
        page.addView(menuRow(R.drawable.f_send, "Install apps on the TV", "Send an APK from this phone to the TV",
                () -> { sub = "install"; showTab(5); }));
        page.addView(menuRow(R.drawable.f_apps, "Apps on the TV", "Turn apps off, stop them, open or uninstall",
                () -> { sub = "apps"; showTab(5); }));
        page.addView(menuRow(R.drawable.f_bookmark, "Presets and backup", "Save looks, back up and restore the TV",
                () -> { sub = "presets"; showTab(5); }));
        page.addView(menuRow(R.drawable.f_refresh, "Update the TV's home screen", "Put the Glass Home version inside "
                + "FreeTV (v" + bundledVersion() + ") on this TV", () -> {
            if (!connected) {
                status("Connect to the TV first.", BAD);
                return;
            }
            confirm("Update Glass Home on the TV?", "Your settings and wallpapers stay.", () -> work("Updating...", () -> {
                byte[] apk = bundledApk();
                if (apk == null) return "This FreeTV has no home screen inside.";
                return tv.installGlass(apk, t -> main.post(() -> status(t, DIM)));
            }, null));
        }));
        page.addView(menuRow(R.drawable.f_info, "About FreeTV", "Version, credits and licences",
                () -> { sub = "about"; showTab(5); }));
    }

    private View menuRow(int icon, String title, String sub2, Runnable go) {
        GlassCard c = new GlassCard(this, px(24), CARD);
        c.setOrientation(LinearLayout.HORIZONTAL);
        c.setGravity(Gravity.CENTER_VERTICAL);
        c.setPadding(px(16), px(16), px(12), px(16));
        FrameLayout ib = new FrameLayout(this);
        ib.setBackground(Fx.brand(px(16)));
        ImageView ic = new ImageView(this);
        ic.setImageResource(icon);
        ic.setImageTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
        FrameLayout.LayoutParams il = new FrameLayout.LayoutParams(px(22), px(22));
        il.gravity = Gravity.CENTER;
        ib.addView(ic, il);
        c.addView(ib, new LinearLayout.LayoutParams(px(44), px(44)));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(px(14), 0, px(8), 0);
        TextView t = text(title, 16.5f, TEXT, true);
        col.addView(t);
        TextView st = text(sub2, 12.5f, DIM, false);
        st.setPadding(0, px(2), 0, 0);
        col.addView(st);
        c.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
        ImageView ch = new ImageView(this);
        ch.setImageResource(R.drawable.f_chevron);
        ch.setAlpha(0.6f);
        c.addView(ch, new LinearLayout.LayoutParams(px(22), px(22)));
        c.setOnClickListener(v -> go.run());
        Fx.press(c);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, px(10), 0, 0);
        c.setLayoutParams(lp);
        return c;
    }

    // ------------------------------------------------------------------ the home screen FreeTV carries

    private byte[] bundledApk() {
        try (java.io.InputStream in = getAssets().open("glasshome.apk")) {
            return AdbLink.readAll(in);
        } catch (IOException e) {
            return null;
        }
    }

    private int bundled = -2;

    private int bundledVersion() {
        if (bundled != -2) return bundled;
        bundled = -1;
        try {
            byte[] apk = bundledApk();
            if (apk == null) return bundled;
            File f = new File(getCacheDir(), "glasshome.apk");
            write(f, apk);
            android.content.pm.PackageInfo pi = getPackageManager().getPackageArchiveInfo(f.getPath(), 0);
            if (pi != null) bundled = pi.versionCode;
        } catch (Exception ignored) { }
        return bundled;
    }

    // ------------------------------------------------------------------ setting up a TV

    private boolean scanning, setupBusy, setupOff;
    private final List<String> setupLog = new ArrayList<>();

    private void buildSetup() {
        LinearLayout dots = row();
        dots.setGravity(Gravity.CENTER);
        for (int i = 0; i < 6; i++) {
            View d = new View(this);
            d.setBackground(i == setupStep ? Fx.brand(px(4)) : Fx.pill(i < setupStep ? 0xB3FFFFFF : 0x33FFFFFF, px(4), 0, 0));
            LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(i == setupStep ? px(26) : px(8), px(8));
            dl.setMargins(px(4), px(6), px(4), px(6));
            dots.addView(d, dl);
        }
        page.addView(dots);
        switch (setupStep) {
            case 0: setupWelcome(); break;
            case 1: setupDebugging(); break;
            case 2: setupFind(); break;
            case 3: setupAllow(); break;
            case 4: setupInstall(); break;
            default: setupLook(); break;
        }
    }

    private void step(int to) {
        setupStep = to;
        showTab(5);
    }

    private View hero(String title, String body) {
        GlassCard c = (GlassCard) card(null);
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        c.setPadding(px(22), px(26), px(22), px(22));
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.mipmap.ic_launcher);
        c.addView(logo, new LinearLayout.LayoutParams(px(88), px(88)));
        android.animation.ObjectAnimator f = android.animation.ObjectAnimator.ofFloat(logo, View.TRANSLATION_Y, 0f, -px(8));
        f.setDuration(1600);
        f.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        f.setRepeatMode(android.animation.ValueAnimator.REVERSE);
        f.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
        f.start();
        TextView t = text(title, 26, TEXT, true);
        t.setTypeface(Fx.font(800));
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, px(14), 0, px(6));
        c.addView(t);
        TextView b = text(body, 15, DIM, false);
        b.setGravity(Gravity.CENTER);
        b.setLineSpacing(px(3), 1f);
        c.addView(b);
        return c;
    }

    private void setupWelcome() {
        page.addView(hero("Welcome to FreeTV", "Turn any Android TV into a clean, fast, beautiful home screen, "
                + "then design every detail of it from here: themes, icon packs, wallpapers, screensavers, "
                + "and a remote with a live picture of the TV."));
        LinearLayout r = row();
        r.addView(button("Set up a TV", true, v -> step(1)));
        page.addView(r);
        LinearLayout r2 = row();
        r2.addView(button(prefs.getString("ip", "").isEmpty() ? "Skip for now" : "My TV is already set up", false, v -> {
            prefs.edit().putBoolean("welcomed", true).apply();
            sub = "";
            showTab(prefs.getString("ip", "").isEmpty() ? 1 : 0);
        }));
        page.addView(r2);
    }

    private void setupDebugging() {
        LinearLayout c = card("1  ·  Let the phone reach the TV");
        c.addView(note("Do this once on the TV with its remote. Phone and TV must be on the same Wi-Fi."));
        String[][] kinds = {
                {"Google TV", "Settings > System > About > tap Android TV OS build 7 times. Then Settings > System > "
                        + "Developer options > turn on USB debugging and Network debugging (or Wireless debugging)."},
                {"Android TV", "Settings > Device Preferences > About > tap Build 7 times. Then Settings > Device "
                        + "Preferences > Developer options > turn on USB debugging and Network debugging."},
                {"Xiaomi / Mi TV", "Settings > Device Preferences > About > tap Build 7 times. Then Device Preferences > "
                        + "Developer options > turn on USB debugging and Network debugging."},
        };
        String pick = prefs.getString("tv_kind", kinds[0][0]);
        LinearLayout chips = row();
        TextView how = text("", 15, TEXT, false);
        how.setLineSpacing(px(4), 1f);
        how.setPadding(0, px(8), 0, px(4));
        List<TextView> all = new ArrayList<>();
        for (String[] k : kinds) {
            TextView ch = chip(k[0], k[0].equals(pick));
            ch.setOnClickListener(v -> {
                prefs.edit().putString("tv_kind", k[0]).apply();
                how.setText(k[1]);
                for (TextView x : all) styleChip(x, x == ch);
            });
            all.add(ch);
            chips.addView(ch);
            if (k[0].equals(pick)) how.setText(k[1]);
        }
        c.addView(scrollRow(chips));
        c.addView(how);
        c.addView(note("Some TVs call it \"ADB debugging\". Nothing is changed on the TV until you say so."));
        page.addView(c);
        LinearLayout r = row();
        r.addView(button("Back", false, v -> step(0)));
        r.addView(button("It's on", true, v -> step(2)));
        page.addView(r);
    }

    private void setupFind() {
        LinearLayout c = card("2  ·  Find your TV");
        c.addView(note("FreeTV looks for TVs with network debugging on, on this Wi-Fi."));
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        c.addView(list);
        Runnable fill = () -> {
            list.removeAllViews();
            if (found.isEmpty()) list.addView(note(scanning ? "Looking..." : "No TV found yet."));
            for (String ip : new ArrayList<>(found)) {
                LinearLayout r = row();
                r.setGravity(Gravity.CENTER_VERTICAL);
                ImageView ic = new ImageView(this);
                ic.setImageResource(R.drawable.f_tv);
                r.addView(ic, new LinearLayout.LayoutParams(px(24), px(24)));
                TextView t = text("TV at " + ip, 16, TEXT, true);
                t.setPadding(px(12), 0, 0, 0);
                r.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
                r.addView(small("Use", v -> {
                    prefs.edit().putString("ip", ip).apply();
                    step(3);
                }));
                list.addView(r);
            }
        };
        fill.run();
        LinearLayout sr = row();
        sr.addView(button(scanning ? "Looking..." : "Look again", true, v -> {
            if (scanning) return;
            scanning = true;
            found.clear();
            fill.run();
            new Thread(() -> {
                Tv.scan(ip -> main.post(() -> {
                    if (!found.contains(ip)) found.add(ip);
                    fill.run();
                }));
                scanning = false;
                main.post(() -> {
                    fill.run();
                    if (found.isEmpty()) status("No TV answered. Check network debugging, or type its address below.", BAD);
                });
            }, "scan").start();
        }));
        c.addView(sr);
        c.addView(label("Or type its address"));
        EditText ip = edit(prefs.getString("ip", ""), "e.g. 192.168.1.20 (TV: Settings > Network)");
        ip.setInputType(InputType.TYPE_CLASS_PHONE);
        c.addView(ip);
        LinearLayout ir = row();
        ir.addView(button("Use this address", false, v -> {
            String a = ip.getText().toString().trim();
            if (a.isEmpty()) return;
            prefs.edit().putString("ip", a).apply();
            step(3);
        }));
        c.addView(ir);
        page.addView(c);
        LinearLayout r = row();
        r.addView(button("Back", false, v -> step(1)));
        page.addView(r);
        if (found.isEmpty() && !scanning) sr.getChildAt(0).performClick();
    }

    private void setupAllow() {
        page.addView(hero("Press Allow on the TV", "The TV now asks \"Allow USB debugging?\" for this phone. "
                + "Tick \"Always allow from this computer\" and press Allow."));
        TextView st = text("Connecting to " + prefs.getString("ip", "") + "...", 14, DIM, false);
        st.setGravity(Gravity.CENTER);
        page.addView(st);
        LinearLayout r = row();
        r.addView(button("Back", false, v -> step(2)));
        r.addView(button("Try again", true, v -> step(3)));
        page.addView(r);
        connect(prefs.getString("ip", ""), () -> step(4));
    }

    private void setupInstall() {
        LinearLayout c = card("3  ·  Put FreeTV's home screen on the TV");
        TextView now = text("Checking the TV...", 15, TEXT, false);
        c.addView(now);
        c.addView(switchRow("Also turn off the TV's own home screen", setupOff, on -> setupOff = on));
        c.addView(note("Recommended on Google TV and PatchWall TVs so Home always comes back here. It can be "
                + "turned back on any time in More > TV and connection > Original."));
        LinearLayout logBox = new LinearLayout(this);
        logBox.setOrientation(LinearLayout.VERTICAL);
        c.addView(logBox);
        Runnable showLog = () -> {
            logBox.removeAllViews();
            for (String l : setupLog) {
                TextView t = text(l, 14, l.startsWith("✓") ? GOOD : TEXT, false);
                t.setPadding(0, px(3), 0, px(3));
                logBox.addView(t);
            }
        };
        showLog.run();
        LinearLayout r = row();
        Button go = button("Install", true, null);
        r.addView(go);
        c.addView(r);
        page.addView(c);
        LinearLayout r2 = row();
        r2.addView(button("Back", false, v -> step(2)));
        r2.addView(button("Next", false, v -> step(5)));
        page.addView(r2);
        int have = bundledVersion();
        worker.execute(() -> {
            int on;
            try {
                on = tv.glassVersion();
            } catch (IOException e) {
                on = -1;
            }
            final int v = on;
            main.post(() -> now.setText(v < 0 ? "Not on this TV yet. FreeTV carries version " + have + "."
                    : v >= have ? "Already on this TV (version " + v + "). You can reinstall it or go on."
                    : "Version " + v + " is on the TV; FreeTV carries a newer one (" + have + ")."));
            main.post(() -> go.setText(v < 0 ? "Install" : v >= have ? "Reinstall" : "Update"));
        });
        go.setOnClickListener(v -> {
            if (setupBusy) return;
            setupBusy = true;
            setupLog.clear();
            showLog.run();
            byte[] apk = bundledApk();
            work("Setting up the TV...", () -> {
                if (apk == null) return "This FreeTV has no home screen inside it.";
                String res = tv.installGlass(apk, t -> main.post(() -> {
                    if (!setupLog.isEmpty() && !setupLog.get(setupLog.size() - 1).startsWith("✓")) {
                        setupLog.set(setupLog.size() - 1, "✓  " + setupLog.get(setupLog.size() - 1));
                    }
                    setupLog.add(t);
                    showLog.run();
                }));
                if (setupOff) tv.disableStockHomes();
                if (tv.wallpaperCount() == 0) {
                    main.post(() -> { setupLog.add("Making starter wallpapers..."); showLog.run(); });
                    for (int i = 0; i < Starter.count(); i++) {
                        tv.addWall(Starter.make(i, 1920, 1080), String.format(Locale.ROOT, "freetv-%02d.jpg", i + 1));
                    }
                }
                tv.restartHome();
                return res;
            }, () -> {
                setupBusy = false;
                for (int i = 0; i < setupLog.size(); i++) if (!setupLog.get(i).startsWith("✓")) setupLog.set(i, "✓  " + setupLog.get(i));
                setupLog.add("✓  Done");
                showLog.run();
                syncFromTv(false);
                main.postDelayed(() -> step(5), 1200);
            });
        });
    }

    private void setupLook() {
        page.addView(hero("Pick your look", "Choose a theme to start with. Everything can be changed later in "
                + "Themes and Design."));
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout line = null;
        for (int i = 0; i < Themes.IDS.length; i++) {
            if (i % 2 == 0) {
                line = row();
                grid.addView(line);
            }
            String id = Themes.IDS[i];
            GlassCard c = new GlassCard(this, px(18), CARD);
            c.setPadding(px(8), px(8), px(8), px(10));
            HomePreview p = new HomePreview(this, logos);
            JSONObject m = themeLook(id);
            p.set(m, previewWall(m), dockEntries(m));
            p.setClickable(false);
            c.addView(p);
            TextView t = text(THEME_NAMES[i], 14, TEXT, true);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, px(6), 0, 0);
            c.addView(t);
            c.setOnClickListener(v -> {
                for (String k : STYLE_KEYS) raw.remove(k);
                raw.remove("faces");
                try {
                    raw.put("theme", id);
                } catch (JSONException ignored) { }
                changed();
                prefs.edit().putBoolean("welcomed", true).apply();
                status("All set. Enjoy FreeTV!", GOOD);
                sub = "";
                showTab(1);
            });
            Fx.press(c);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
            lp.setMargins(px(5), px(5), px(5), px(5));
            line.addView(c, lp);
        }
        if (line != null && line.getChildCount() == 1) line.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        page.addView(grid);
    }

    // ------------------------------------------------------------------ About

    private void buildAbout() {
        String version = "1.0";
        try {
            version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception ignored) { }
        page.addView(hero("FreeTV " + version, "A free home screen for Android TV, and the phone app to design it. "
                + "Made by Arige Sri Ram Teja."));
        LinearLayout c = card("Inside");
        c.addView(note("Home screen for the TV: Glass Home v" + bundledVersion() + " (carried inside FreeTV and "
                + "installed over your Wi-Fi)."));
        c.addView(note("FreeTV talks only to your TV, over your home Wi-Fi. It has no accounts, no ads and no tracking."));
        c.addView(note("Temperature on the TV: weather data by Open-Meteo.com (CC BY 4.0)."));
        c.addView(note("Lettering: Outfit, by the Outfit Project Authors, under the SIL Open Font License 1.1."));
        LinearLayout r = row();
        r.addView(button("Font licence", false, v -> {
            String lic;
            try (java.io.InputStream in = getAssets().open("OFL.txt")) {
                lic = new String(AdbLink.readAll(in), StandardCharsets.UTF_8);
            } catch (IOException e) {
                lic = "SIL Open Font License 1.1";
            }
            new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle("Outfit font licence")
                    .setMessage(lic).setPositiveButton("OK", null).show();
        }));
        c.addView(r);
        page.addView(c);
    }

    // ------------------------------------------------------------------ Presets tab

    private JSONObject presets() {
        try {
            return new JSONObject(prefs.getString("presets", "{}"));
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    private void buildPresets() {
        LinearLayout c = card("Save this setup");
        EditText name = edit("", "Name, e.g. Movie night");
        c.addView(name);
        LinearLayout r = row();
        r.addView(button("Save", true, v -> {
            String n = name.getText().toString().trim();
            if (n.isEmpty()) n = "Setup " + new SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()).format(new Date());
            savePreset(n, raw);
            status("Saved '" + n + "'.", GOOD);
            refreshTab();
        }));
        c.addView(r);
        page.addView(c);

        JSONObject all = presets();
        LinearLayout list = card("Saved (" + all.length() + ")");
        Iterator<String> it = all.keys();
        if (!it.hasNext()) list.addView(note("Nothing saved yet."));
        while (it.hasNext()) {
            String n = it.next();
            JSONObject p = all.optJSONObject(n);
            if (p == null) continue;
            LinearLayout pr = new LinearLayout(this);
            pr.setOrientation(LinearLayout.VERTICAL);
            HomePreview hp = new HomePreview(this, logos);
            JSONObject m = Themes.merge(p);
            hp.set(m, previewWall(m), dockEntries(m));
            pr.addView(hp);
            LinearLayout row = row();
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(text(n, 16, TEXT, true), new LinearLayout.LayoutParams(0, -2, 1f));
            row.addView(small("Use", v -> {
                try {
                    raw = new JSONObject(p.toString());
                } catch (JSONException ignored) { }
                changed();
                status("'" + n + "' loaded.", GOOD);
                refreshTab();
            }));
            row.addView(small("Delete", v -> confirm("Delete '" + n + "'?", null, () -> {
                JSONObject a = presets();
                a.remove(n);
                prefs.edit().putString("presets", a.toString()).apply();
                refreshTab();
            })));
            pr.addView(row);
            pr.setPadding(0, px(6), 0, px(10));
            list.addView(pr);
        }
        page.addView(list);

        LinearLayout b = card("Backup and reset");
        LinearLayout br = row();
        br.addView(button("Back up the TV's settings", false, v -> work("Reading the TV...", () -> {
            JSONObject t = tv.readConfig();
            String n = "TV backup " + new SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()).format(new Date());
            main.post(() -> {
                savePreset(n, t);
                refreshTab();
            });
            return "Saved as '" + n + "'.";
        }, null)));
        b.addView(br);
        LinearLayout br2 = row();
        br2.addView(button("Reset look to the theme", false, v -> confirm("Reset every detail to the theme's own look?",
                "Your apps and wallpapers stay.", () -> {
                    for (String k : STYLE_KEYS) if (!k.equals("screensaver")) raw.remove(k);
                    raw.remove("faces");
                    changed();
                    status("Back to the theme's own look.", GOOD);
                })));
        b.addView(br2);
        page.addView(b);
    }

    private void savePreset(String name, JSONObject cfg) {
        JSONObject all = presets();
        try {
            all.put(name, new JSONObject(cfg.toString()));
        } catch (JSONException ignored) { }
        prefs.edit().putString("presets", all.toString()).apply();
    }

    // ------------------------------------------------------------------ setting widgets (liquid glass)

    private View reset(String... keys) {
        LinearLayout r = row();
        r.setGravity(Gravity.END);
        TextView t = small("↺  Reset", v -> {
            remove(keys);
            refreshTab();
        });
        r.addView(t);
        return r;
    }

    /** A setting row: words on the left, the glass switch on the right. */
    private View switchRow(String label, boolean on, Widgets.OnToggle change) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, px(10), 0, px(10));
        TextView t = text(label, 15.5f, TEXT, false);
        r.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        Widgets.Switch sw = new Widgets.Switch(this, on);
        sw.setListener(change);
        r.addView(sw);
        r.setOnClickListener(v -> sw.set(!sw.isOn(), true));
        return r;
    }

    private View toggle(String label, String key) {
        return switchRow(label, merged().optBoolean(key, false), on -> put(key, on));
    }

    /** Chips; rebuild = the page changes shape with this setting, so redraw it after a pick. */
    private View choices(String key, String[] labels, Object[] values, boolean rebuild) {
        LinearLayout r = row();
        Object cur = merged().opt(key);
        List<TextView> chips = new ArrayList<>();
        for (int i = 0; i < labels.length; i++) {
            final Object value = values[i];
            TextView c = chip(labels[i], String.valueOf(value).equals(String.valueOf(cur)));
            c.setOnClickListener(v -> {
                put(key, value);
                if (rebuild) refreshTab();
                else for (TextView t : chips) styleChip(t, t == c);
            });
            chips.add(c);
            r.addView(c);
        }
        return scrollRow(r);
    }

    /** A gradient slider with its value in a little glass bubble. */
    private SeekBar glassSeek(int max, int progress) {
        SeekBar s = new SeekBar(this);
        s.setMax(max);
        s.setProgress(progress);
        android.graphics.drawable.GradientDrawable track = Fx.pill(0x26FFFFFF, px(4), 0, 0);
        android.graphics.drawable.GradientDrawable fillD = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT, new int[]{Fx.VIOLET, Fx.CYAN});
        fillD.setCornerRadius(px(4));
        android.graphics.drawable.ClipDrawable clip = new android.graphics.drawable.ClipDrawable(fillD, Gravity.START,
                android.graphics.drawable.ClipDrawable.HORIZONTAL);
        android.graphics.drawable.LayerDrawable layers = new android.graphics.drawable.LayerDrawable(
                new android.graphics.drawable.Drawable[]{track, clip});
        layers.setId(0, android.R.id.background);
        layers.setId(1, android.R.id.progress);
        layers.setLayerHeight(0, px(6));
        layers.setLayerHeight(1, px(6));
        layers.setLayerGravity(0, Gravity.CENTER_VERTICAL);
        layers.setLayerGravity(1, Gravity.CENTER_VERTICAL);
        s.setProgressDrawable(layers);
        android.graphics.drawable.GradientDrawable thumb = new android.graphics.drawable.GradientDrawable();
        thumb.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        thumb.setColor(0xFFFFFFFF);
        thumb.setStroke(px(3), Fx.VIOLET);
        thumb.setSize(px(22), px(22));
        s.setThumb(thumb);
        s.setSplitTrack(false);
        s.setPadding(px(12), px(10), px(12), px(10));
        return s;
    }

    private TextView valueBubble(String v) {
        TextView t = text(v, 13, TEXT, true);
        t.setGravity(Gravity.CENTER);
        t.setMinWidth(px(58));
        t.setPadding(px(10), px(5), px(10), px(5));
        t.setBackground(Fx.pill(0x1FFFFFFF, px(14), LINE, 1));
        return t;
    }

    private View slider(String key, int min, int max, String unit, Runnable after) {
        LinearLayout r = row();
        r.setGravity(Gravity.CENTER_VERTICAL);
        int cur = (int) Math.round(merged().optDouble(key, min));
        cur = Math.max(min, Math.min(max, cur));
        SeekBar s = glassSeek(max - min, cur - min);
        TextView v = valueBubble(cur + unit);
        s.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar b, int p, boolean user) {
                v.setText((p + min) + unit);
                if (user) {
                    try {
                        raw.put(key, p + min);
                    } catch (JSONException ignored) { }
                    for (Runnable x : new ArrayList<>(onChange)) x.run();
                }
            }
            @Override public void onStartTrackingTouch(SeekBar b) { Fx.tick(b); }
            @Override public void onStopTrackingTouch(SeekBar b) {
                put(key, b.getProgress() + min);
                if (after != null) after.run();
            }
        });
        r.addView(s, new LinearLayout.LayoutParams(0, -2, 1f));
        r.addView(v);
        return r;
    }

    private View swatch(String hex, boolean on) {
        View d = new View(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(32), px(32));
        lp.setMargins(0, px(4), px(10), px(4));
        d.setLayoutParams(lp);
        d.setBackground(round(Color.parseColor(hex), px(16), on ? 0xFFFFFFFF : 0x40FFFFFF, px(on ? 3 : 1)));
        if (on) d.setElevation(px(6));
        Fx.press(d);
        return d;
    }

    /** Named choices (e.g. Wallpaper colour / White) followed by colour dots and a custom colour. */
    private View colors(String key, String[] names, String[] values, Runnable after) {
        return colors(key, names, values, after, SWATCHES);
    }

    private View colors(String key, String[] names, String[] values, Runnable after, String[] palette) {
        LinearLayout r = row();
        r.setGravity(Gravity.CENTER_VERTICAL);
        String cur = merged().optString(key, values.length > 0 ? values[0] : "");
        for (int i = 0; i < names.length; i++) {
            final String val = values[i];
            TextView c = chip(names[i], val.equals(cur));
            c.setOnClickListener(v -> {
                put(key, val);
                refreshTab();
            });
            r.addView(c);
        }
        for (String hex : palette) {
            View dot = swatch(hex, hex.equalsIgnoreCase(cur));
            dot.setOnClickListener(v -> {
                put(key, hex);
                refreshTab();
            });
            r.addView(dot);
        }
        TextView custom = chip("Custom", false);
        custom.setOnClickListener(v -> {
            EditText e = edit(cur.startsWith("#") ? cur : "#", "#RRGGBB");
            new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle("Colour").setView(e)
                    .setPositiveButton("OK", (d, w) -> {
                String hex = e.getText().toString().trim();
                if (!hex.startsWith("#")) hex = "#" + hex;
                try {
                    Color.parseColor(hex);
                    put(key, hex.toUpperCase(Locale.ROOT));
                    refreshTab();
                } catch (IllegalArgumentException x) {
                    toast("Not a colour. Use #RRGGBB, e.g. #FF7A00.");
                }
            }).setNegativeButton("Cancel", null).show();
        });
        r.addView(custom);
        return scrollRow(r);
    }

    // ------------------------------------------------------------------ view helpers

    private int px(float v) {
        return Math.round(v * dp);
    }

    private TextView text(String s, float size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setTypeface(Fx.font(bold ? 600 : 400));
        return t;
    }

    private TextView note(String s) {
        TextView t = text(s, 13.5f, DIM, false);
        t.setLineSpacing(px(2), 1f);
        t.setPadding(0, px(4), 0, px(8));
        return t;
    }

    private TextView label(String s) {
        TextView t = text(s.toUpperCase(Locale.getDefault()), 11.5f, 0x8CFFFFFF, true);
        t.setLetterSpacing(0.12f);
        t.setPadding(0, px(14), 0, px(2));
        return t;
    }

    private LinearLayout card(String title) {
        GlassCard c = new GlassCard(this, px(26), CARD);
        c.setPadding(px(18), px(16), px(18), px(16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, px(12), 0, 0);
        c.setLayoutParams(lp);
        if (title != null && !title.isEmpty()) {
            TextView t = text(title, 18, TEXT, true);
            t.setTypeface(Fx.font(700));
            t.setPadding(0, 0, 0, px(6));
            c.addView(t);
        }
        return c;
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setPadding(0, px(4), 0, px(4));
        return r;
    }

    private View scrollRow(LinearLayout r) {
        HorizontalScrollView s = new GlassCard.HScroll(this);
        s.addView(r);
        return s;
    }

    /** Primary: the glowing brand gradient. Secondary: a frosted glass pill. */
    private Button button(String label, boolean primary, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setTypeface(Fx.font(600));
        b.setTextColor(TEXT);
        b.setStateListAnimator(null);
        b.setBackground(primary ? Fx.brand(px(24)) : Fx.pill(0x1FFFFFFF, px(24), 0x4DFFFFFF, 1));
        if (primary) {
            b.setElevation(px(8));
            if (Build.VERSION.SDK_INT >= 28) {
                b.setOutlineSpotShadowColor(Fx.VIOLET);
                b.setOutlineAmbientShadowColor(Fx.CYAN);
            }
        }
        b.setPadding(px(14), 0, px(14), 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, px(50), 1f);
        lp.setMargins(px(4), px(4), px(4), px(4));
        b.setLayoutParams(lp);
        b.setOnClickListener(l);
        Fx.press(b);
        return b;
    }

    private TextView small(String label, View.OnClickListener l) {
        TextView t = chip(label, false);
        t.setOnClickListener(l);
        return t;
    }

    private TextView chip(String label, boolean on) {
        TextView t = text(label, 14, TEXT, true);
        t.setPadding(px(16), px(9), px(16), px(9));
        t.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(0, px(3), px(8), px(3));
        t.setLayoutParams(lp);
        styleChip(t, on);
        Fx.press(t);
        return t;
    }

    private void styleChip(TextView t, boolean on) {
        t.setBackground(on ? Fx.brand(px(20)) : Fx.pill(0x14FFFFFF, px(20), 0x40FFFFFF, 1));
        t.setTextColor(on ? 0xFFFFFFFF : 0xD9FFFFFF);
        t.setElevation(on ? px(4) : 0);
    }

    private EditText edit(String value, String hint) {
        EditText e = new EditText(this);
        e.setText(value);
        e.setHint(hint);
        e.setHintTextColor(0x66FFFFFF);
        e.setTextColor(TEXT);
        e.setTypeface(Fx.font(500));
        e.setSingleLine(true);
        e.setInputType(InputType.TYPE_CLASS_TEXT);
        e.setBackground(Fx.pill(0x14FFFFFF, px(16), 0x40FFFFFF, 1));
        e.setPadding(px(16), px(13), px(16), px(13));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, px(6), 0, px(6));
        e.setLayoutParams(lp);
        return e;
    }

    private static GradientDrawable round(int fill, int radius, int stroke, int strokeWidth) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(radius);
        if (strokeWidth > 0) g.setStroke(strokeWidth, stroke);
        return g;
    }

    private void confirm(String title, String message, Runnable yes) {
        AlertDialog.Builder b = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle(title)
                .setPositiveButton("Yes", (d, w) -> yes.run()).setNegativeButton("No", null);
        if (message != null) b.setMessage(message);
        b.show();
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }

    @SuppressWarnings("unused")
    private static ViewGroup.LayoutParams wrap() {
        return new ViewGroup.LayoutParams(-2, -2);
    }
}
