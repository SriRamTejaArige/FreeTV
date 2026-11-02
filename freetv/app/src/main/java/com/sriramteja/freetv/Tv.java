package com.sriramteja.freetv;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Everything the app asks of the TV, as adb commands over AdbLink. Call off the main thread. */
final class Tv {

    static final String GLASS = "com.sriramteja.glasshome";
    static final String GLASS_HOME = GLASS + "/.HomeActivity";
    static final String GLASS_DREAM = GLASS + "/.GlassDream";
    static final String CLEAN = "com.gathin.tvhome";
    static final String CLEAN_HOME = CLEAN + "/.MainActivity";
    static final String STOCK = "com.google.android.tvlauncher";
    static final String PATCHWALL = "com.mitv.tvhome.atv";
    static final String FILES = "/sdcard/Android/data/" + GLASS + "/files";
    static final String PICS = "/sdcard/Pictures/GlassHome";
    static final String WALLS = PICS + "/wallpapers";
    /** Where Glass Home leaves apps.json, icons, thumbnails for this app (its own folder as the fallback). */
    static final String STUDIO = PICS + "/.studio";
    private static final String TMP = "/data/local/tmp/freetv-up";
    private static final String HOME_Q =
            "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME";

    /** Never switched off or stopped from this app: the TV needs them. */
    static final String[] PROTECTED = {
            "android", "com.android.systemui", "com.android.settings", "com.android.tv.settings",
            "com.android.vending", "com.google.android.gms", "com.google.android.gsf",
            "com.google.android.inputmethod", "com.google.android.leanback.ime", "com.android.bluetooth",
            "com.mi.bluetooth", "com.android.tv", "com.droidlogic", "mitv.service", "xiaomi.tvservice",
            "com.xiaomi.android.tvsetup.partnercustomizer", "com.android.providers", "com.android.shell",
            "com.android.se", "com.google.android.ext.services", "com.google.android.permissioncontroller",
            "com.google.android.packageinstaller", "com.android.networkstack", "com.android.wifi",
            "com.google.android.webview", GLASS, CLEAN, "com.sriramteja.tvstudio", "com.sriramteja.freetv",
    };

    final AdbLink link;

    Tv(AdbLink link) {
        this.link = link;
    }

    String sh(String cmd) throws IOException {
        return link.shell(cmd).trim();
    }

    static boolean isProtected(String pkg) {
        for (String p : PROTECTED) if (pkg.equals(p) || pkg.startsWith(p + ".")) return true;
        return false;
    }

    static String q(String s) {
        return "'" + s.replace("'", "'\\''") + "'";
    }

    // ------------------------------------------------------------------ status

    static final class Status {
        int ramUsedPct = -1, storageUsedPct = -1;
        long ramFreeMb = -1, ramTotalMb = -1, storageFreeMb = -1;
        String home = "", model = "", android = "", focus = "";
        boolean glassInstalled, cleanInstalled;
    }

    Status status() throws IOException {
        Status s = new Status();
        String out = sh("grep -E 'MemTotal|MemAvailable' /proc/meminfo; echo ===; df /data | tail -1; echo ===; "
                + HOME_Q + " | tail -n 1; echo ===; pm list packages " + GLASS + "; pm list packages " + CLEAN
                + "; echo ===; getprop ro.product.model; echo ===; getprop ro.build.version.release; echo ===; "
                + "dumpsys window | grep -m1 mCurrentFocus");
        String[] part = out.split("===");
        long total = num(part[0], "MemTotal:\\s+(\\d+)"), avail = num(part[0], "MemAvailable:\\s+(\\d+)");
        if (total > 0) {
            s.ramUsedPct = (int) Math.round(100.0 * (total - avail) / total);
            s.ramFreeMb = avail / 1024;
            s.ramTotalMb = total / 1024;
        }
        if (part.length > 1) {
            String[] df = part[1].trim().split("\\s+");
            if (df.length >= 4) {
                try {
                    long size = Long.parseLong(df[1]), free = Long.parseLong(df[3]);
                    s.storageUsedPct = (int) Math.round(100.0 * (size - free) / size);
                    s.storageFreeMb = free / 1024;
                } catch (NumberFormatException ignored) { }
            }
        }
        if (part.length > 2) s.home = part[2].trim();
        if (part.length > 3) {
            Set<String> got = packages(part[3]);
            s.glassInstalled = got.contains(GLASS);
            s.cleanInstalled = got.contains(CLEAN);
        }
        if (part.length > 4) s.model = part[4].trim();
        if (part.length > 5) s.android = part[5].trim();
        if (part.length > 6) s.focus = part[6].trim();
        return s;
    }

    private static long num(String text, String re) {
        Matcher m = Pattern.compile(re).matcher(text);
        return m.find() ? Long.parseLong(m.group(1)) : -1;
    }

    static Set<String> packages(String listing) {
        Set<String> set = new LinkedHashSet<>();
        for (String line : listing.split("\n")) {
            line = line.trim();
            if (line.startsWith("package:")) set.add(line.substring(8).trim());
        }
        return set;
    }

    // ------------------------------------------------------------------ screen and remote

    byte[] screenshot() throws IOException {
        return link.exec("screencap -p");
    }

    void key(String code) throws IOException {
        sh("input keyevent " + code);
    }

    void longKey(String code) throws IOException {
        sh("input keyevent --longpress " + code);
    }

    void type(String text) throws IOException {
        sh("input text " + q(text.replace(" ", "%s")));
    }

    // ------------------------------------------------------------------ home switch

    String switchTo(String which) throws IOException {
        switch (which) {
            case "glass":
                if (!sh("pm list packages " + GLASS).contains("package:" + GLASS)) return "Glass Home is not installed on the TV.";
                sh("pm disable-user --user 0 " + STOCK + " 2>/dev/null; pm disable-user --user 0 " + PATCHWALL
                        + " 2>/dev/null; cmd package set-home-activity --user 0 " + GLASS_HOME + "; input keyevent KEYCODE_HOME");
                return sh(HOME_Q + " | tail -n 1").startsWith(GLASS) ? "The TV is on Glass Home." : "Press Home on the remote to finish.";
            case "clean":
                if (!sh("pm list packages " + CLEAN).contains("package:" + CLEAN)) return "The old Clean home is not installed.";
                sh("pm disable-user --user 0 " + STOCK + " 2>/dev/null; cmd package set-home-activity --user 0 "
                        + CLEAN_HOME + "; input keyevent KEYCODE_HOME");
                return "The TV is on the old Clean home.";
            default:
                sh("pm enable " + STOCK + "; pm enable " + PATCHWALL + " 2>/dev/null; cmd package set-home-activity --user 0 $("
                        + HOME_Q + " " + STOCK + " | tail -n 1); input keyevent KEYCODE_HOME");
                return sh(HOME_Q + " | tail -n 1").startsWith(STOCK)
                        ? "The TV is on the original Google TV home." : "Press Home on the remote to finish.";
        }
    }

    /** Glass Home as the home screen with its own screensaver. */
    String useGlass() throws IOException {
        String r = switchTo("glass");
        sh("settings put secure screensaver_components " + GLASS_DREAM);
        return r;
    }

    /** The first launcher (cleanyourtv) as the home screen, with its own clock screensaver. */
    String useClassic() throws IOException {
        String r = switchTo("clean");
        sh("settings put secure screensaver_components " + CLEAN + "/.ClockDream");
        return r;
    }

    void restartHome() throws IOException {
        sh("am force-stop " + GLASS + "; am start -a android.intent.action.MAIN -c android.intent.category.HOME >/dev/null");
    }

    // ------------------------------------------------------------------ Glass Home settings

    byte[] pull(String path) throws IOException {
        return link.exec("cat " + q(path) + " 2>/dev/null");
    }

    /** A file Glass Home exported for this app (relative name, e.g. "icons/x.png"). */
    byte[] export(String rel) throws IOException {
        byte[] b = pull(STUDIO + "/" + rel);
        return b.length > 0 ? b : pull(FILES + "/" + rel);
    }

    JSONObject readConfig() throws IOException {
        for (String p : new String[]{PICS + "/config.json", FILES + "/config.json"}) {
            String t = new String(pull(p), StandardCharsets.UTF_8).trim();
            int i = t.indexOf('{');
            if (i < 0) continue;
            try {
                return new JSONObject(t.substring(i));
            } catch (Exception ignored) { }
        }
        return new JSONObject();
    }

    /**
     * Sends the settings. Glass Home picks them up the next time it shows; when it is on screen now it is
     * nudged with a Home press so the change shows straight away.
     */
    String sendConfig(JSONObject cfg, boolean showNow) throws IOException {
        String text;
        try {
            text = cfg.toString(1);
        } catch (Exception e) {
            text = cfg.toString();
        }
        byte[] data = text.getBytes(StandardCharsets.UTF_8);
        sh("mkdir -p " + PICS + " 2>/dev/null; true");
        link.push(data, TMP);
        sh("cp " + TMP + " " + PICS + "/config.json; [ -d " + FILES + " ] && cp " + TMP + " " + FILES + "/config.json; rm -f " + TMP
                + "; appops set " + GLASS + " MANAGE_EXTERNAL_STORAGE allow 2>/dev/null; true");
        if (showNow) {
            String focus = sh("dumpsys window | grep -m1 mCurrentFocus");
            if (focus.contains(GLASS)) {
                sh("am start -a android.intent.action.MAIN -c android.intent.category.HOME >/dev/null");
                return "Sent. The TV shows it now.";
            }
            return "Sent. You'll see it when the TV is on the home screen.";
        }
        return "Sent.";
    }

    static final class App {
        String pkg, label;
        boolean tv;
    }

    /** The TV's apps with their names, from Glass Home's apps.json (pm names as the fallback). */
    List<App> homeApps() throws IOException {
        List<App> out = new ArrayList<>();
        String t = new String(export("apps.json"), StandardCharsets.UTF_8).trim();
        try {
            JSONArray a = new JSONArray(t.substring(Math.max(0, t.indexOf('['))));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                App x = new App();
                x.pkg = o.optString("pkg");
                x.label = o.optString("label", Names.of(x.pkg));
                x.tv = o.optBoolean("tv");
                out.add(x);
            }
        } catch (Exception e) {
            for (Pkg p : apps()) {
                if (!p.launchable || !p.enabled) continue;
                App x = new App();
                x.pkg = p.pkg;
                x.label = Names.of(p.pkg);
                out.add(x);
            }
        }
        out.sort((x, y) -> x.label.compareToIgnoreCase(y.label));
        return out;
    }

    byte[] icon(String pkg) throws IOException {
        return export("icons/" + pkg + ".png");
    }

    // ------------------------------------------------------------------ wallpapers

    static final class Wall {
        String name;
        long bytes;
        int brightness = -1;
    }

    List<Wall> walls() throws IOException {
        List<Wall> out = new ArrayList<>();
        String t = new String(export("walls.json"), StandardCharsets.UTF_8).trim();
        java.util.Map<String, Integer> lum = new java.util.HashMap<>();
        try {
            JSONArray a = new JSONArray(t.substring(Math.max(0, t.indexOf('['))));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                lum.put(o.optString("name"), o.optInt("brightness", -1));
            }
        } catch (Exception ignored) { }
        String ls = sh("ls -l " + WALLS + " 2>/dev/null");
        for (String line : ls.split("\n")) {
            String[] f = line.trim().split("\\s+");
            if (f.length < 8) continue;
            String name = f[f.length - 1];
            String low = name.toLowerCase(Locale.ROOT);
            if (!(low.endsWith(".jpg") || low.endsWith(".jpeg") || low.endsWith(".png") || low.endsWith(".webp"))) continue;
            Wall w = new Wall();
            w.name = name;
            try {
                w.bytes = Long.parseLong(f[4]);
            } catch (RuntimeException ignored) { }
            Integer l = lum.get(name);
            w.brightness = l == null ? -1 : l;
            out.add(w);
        }
        out.sort((x, y) -> x.name.compareTo(y.name));
        return out;
    }

    byte[] thumb(String name) throws IOException {
        return export("thumbs/" + name + ".jpg");
    }

    byte[] wallpaper(String name) throws IOException {
        return pull(WALLS + "/" + name);
    }

    void addWall(byte[] jpg, String name) throws IOException {
        sh("mkdir -p " + WALLS);
        link.push(jpg, WALLS + "/" + name);
    }

    void deleteWall(String name) throws IOException {
        sh("rm -f " + q(WALLS + "/" + name) + " " + q(STUDIO + "/thumbs/" + name + ".jpg") + " " + q(FILES + "/thumbs/" + name + ".jpg"));
    }

    // ------------------------------------------------------------------ screensaver and power

    void testFace(String face) throws IOException {
        sh("am start -n " + GLASS + "/.FaceTest" + (face == null || face.isEmpty() ? "" : " --es face " + face));
    }

    void startScreensaver() throws IOException {
        sh("am start -n com.android.systemui/.Somnambulator 2>/dev/null || am start -n " + GLASS + "/.FaceTest");
    }

    static final class Power {
        boolean saverOn, ours, neverOff;
        int minutes = 5;
    }

    Power power() throws IOException {
        String out = sh("settings get secure screensaver_enabled; settings get secure screensaver_components; "
                + "settings get system screen_off_timeout; settings get secure sleep_timeout");
        String[] l = out.split("\n");
        Power p = new Power();
        p.saverOn = l.length > 0 && l[0].trim().equals("1");
        p.ours = l.length > 1 && l[1].contains(GLASS);
        try {
            long ms = Long.parseLong(l[2].trim());
            p.minutes = ms >= Integer.MAX_VALUE ? 0 : (int) Math.max(1, ms / 60000);
        } catch (RuntimeException ignored) { }
        p.neverOff = l.length > 3 && l[3].trim().equals("-1");
        return p;
    }

    String setPower(boolean saverOn, int minutes, boolean neverOff) throws IOException {
        long ms = Math.max(1, minutes) * 60_000L;
        StringBuilder c = new StringBuilder();
        if (saverOn) {
            c.append("settings put secure screensaver_components ").append(GLASS_DREAM).append("; ")
                    .append("settings put secure screensaver_enabled 1; settings put secure screensaver_activate_on_sleep 1; ")
                    .append("settings put secure screensaver_activate_on_dock 1; ");
        } else {
            c.append("settings put secure screensaver_enabled 0; ");
        }
        c.append("settings put system screen_off_timeout ").append(ms).append("; ");
        if (neverOff) c.append("settings put secure sleep_timeout -1; settings put secure attentive_timeout -1; ");
        else c.append("settings put secure sleep_timeout 1800000; settings delete secure attentive_timeout; ");
        c.append("true");
        sh(c.toString());
        return saverOn ? "The screensaver starts after " + Math.max(1, minutes) + " min idle"
                + (neverOff ? " and the TV never turns itself off." : ".")
                : "No screensaver; the screen turns off after " + Math.max(1, minutes) + " min idle.";
    }

    // ------------------------------------------------------------------ memory and storage

    long ramFreeMb() throws IOException {
        return num(sh("grep MemAvailable /proc/meminfo"), "MemAvailable:\\s+(\\d+)") / 1024;
    }

    String closeAll() throws IOException {
        long before = ramFreeMb();
        String running = sh("for p in $(ps -A -o NAME | grep -E '^[a-z]+\\.[a-z0-9_.]+$' | sed 's/:.*//' | sort -u); do "
                + "case $p in android|com.android.*|com.google.android.gms*|com.google.android.inputmethod*|"
                + "com.google.android.leanback.ime|com.mi.*|com.droidlogic*|mitv.*|xiaomi.*|" + GLASS + "|" + CLEAN
                + "|com.sriramteja.*) ;; *) echo $p ;; esac; done");
        StringBuilder c = new StringBuilder();
        int n = 0;
        for (String p : running.split("\n")) {
            p = p.trim();
            if (p.isEmpty() || isProtected(p)) continue;
            c.append("am force-stop ").append(p).append("; ");
            n++;
        }
        c.append("am kill-all; sleep 1");
        sh(c.toString());
        long after = ramFreeMb();
        return "Closed " + n + " apps. Freed " + Math.max(0, after - before) + " MB.";
    }

    String clearCaches() throws IOException {
        long before = storageFreeMb();
        sh("pm trim-caches 999G");
        long after = storageFreeMb();
        return "Cleared app caches. Freed " + Math.max(0, after - before) + " MB of storage.";
    }

    private long storageFreeMb() throws IOException {
        String[] df = sh("df /data | tail -1").split("\\s+");
        try {
            return Long.parseLong(df[3]) / 1024;
        } catch (RuntimeException e) {
            return -1;
        }
    }

    /** Apps open right now (not system parts), for the RAM list. */
    List<String> running() throws IOException {
        String out = sh("ps -A -o RSS,NAME | sort -rn | head -60");
        List<String> list = new ArrayList<>();
        for (String line : out.split("\n")) {
            String[] f = line.trim().split("\\s+");
            if (f.length < 2) continue;
            String name = f[1].replaceAll(":.*", "");
            if (!name.matches("[a-z][a-z0-9_]*(\\.[a-z0-9_]+)+") || isProtected(name) || list.contains(name)) continue;
            try {
                list.add(name + " " + (Long.parseLong(f[0]) / 1024) + " MB");
            } catch (NumberFormatException ignored) { }
        }
        return list;
    }

    interface Progress { void step(String text); }

    // ------------------------------------------------------------------ setting up a new TV

    /** The phone's Wi-Fi address prefix, e.g. "192.168.29.", or null. */
    static String lanPrefix() {
        try {
            for (java.net.NetworkInterface ni : java.util.Collections.list(java.net.NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (java.net.InetAddress a : java.util.Collections.list(ni.getInetAddresses())) {
                    if (a instanceof java.net.Inet4Address && a.isSiteLocalAddress()) {
                        String ip = a.getHostAddress();
                        return ip.substring(0, ip.lastIndexOf('.') + 1);
                    }
                }
            }
        } catch (Exception ignored) { }
        return null;
    }

    interface Found { void found(String ip); }

    /** Looks for devices with network debugging open (port 5555) on the phone's Wi-Fi; about 3 seconds. */
    static List<String> scan(Found each) {
        String prefix = lanPrefix();
        List<String> out = java.util.Collections.synchronizedList(new ArrayList<>());
        if (prefix == null) return out;
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(48);
        for (int i = 1; i < 255; i++) {
            final String ip = prefix + i;
            pool.execute(() -> {
                try (java.net.Socket s = new java.net.Socket()) {
                    s.connect(new java.net.InetSocketAddress(ip, 5555), 700);
                    out.add(ip);
                    if (each != null) each.found(ip);
                } catch (Exception ignored) { }
            });
        }
        pool.shutdown();
        try {
            pool.awaitTermination(8, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException ignored) { }
        return out;
    }

    /** Glass Home's version on the TV, or -1 when it is not installed. */
    int glassVersion() throws IOException {
        Matcher m = Pattern.compile("versionCode=(\\d+)").matcher(sh("dumpsys package " + GLASS + " | grep -m1 versionCode"));
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }

    /**
     * Installs (or updates) Glass Home from the APK FreeTV carries, then sets it up: permissions, home
     * screen, screensaver and its folders. Steps are reported as they go.
     */
    String installGlass(byte[] apk, Progress progress) throws IOException {
        String tmp = "/data/local/tmp/freetv-glasshome.apk";
        progress.step("Copying the home screen to the TV...");
        link.push(apk, tmp);
        progress.step("Installing it...");
        String r = sh("pm install -r -g " + tmp + " 2>&1; rm -f " + tmp);
        if (!r.contains("Success")) {
            if (r.contains("INSTALL_FAILED_UPDATE_INCOMPATIBLE")) {
                return "A different Glass Home is on the TV. Uninstall it on the TV first, then try again.";
            }
            return "The TV refused to install it: " + r.trim();
        }
        progress.step("Giving it its permissions...");
        sh("appops set " + GLASS + " MANAGE_EXTERNAL_STORAGE allow 2>/dev/null; "
                + "appops set " + GLASS + " GET_USAGE_STATS allow 2>/dev/null; "
                + "pm grant " + GLASS + " android.permission.ACCESS_FINE_LOCATION 2>/dev/null; "
                + "pm grant " + GLASS + " android.permission.ACCESS_COARSE_LOCATION 2>/dev/null; "
                + "pm grant " + GLASS + " android.permission.READ_EXTERNAL_STORAGE 2>/dev/null; "
                + "mkdir -p " + WALLS + "; true");
        progress.step("Making it the home screen...");
        sh("cmd package set-home-activity --user 0 " + GLASS_HOME + " 2>/dev/null; true");
        progress.step("Setting up the screensaver...");
        sh("settings put secure screensaver_components " + GLASS_DREAM + "; settings put secure screensaver_enabled 1; "
                + "settings put secure screensaver_activate_on_sleep 1; true");
        sh("input keyevent KEYCODE_WAKEUP; am start -a android.intent.action.MAIN -c android.intent.category.HOME >/dev/null");
        return "Glass Home is on the TV.";
    }

    /** The TV makers' own home screens FreeTV knows; switched off only when asked. */
    static final String[] STOCK_HOMES = {STOCK, "com.google.android.apps.tv.launcherx", PATCHWALL};

    String disableStockHomes() throws IOException {
        StringBuilder c = new StringBuilder();
        for (String p : STOCK_HOMES) c.append("pm disable-user --user 0 ").append(p).append(" 2>/dev/null; ");
        sh(c + "true");
        return "The TV's own home screen is off (it can be turned back on in More > TV and home).";
    }

    int wallpaperCount() throws IOException {
        try {
            return Integer.parseInt(sh("ls " + WALLS + " 2>/dev/null | grep -ciE '\\.(jpe?g|png|webp)$'").trim());
        } catch (RuntimeException e) {
            return 0;
        }
    }

    // ------------------------------------------------------------------ apps

    static final class Pkg {
        String pkg;
        boolean enabled, userApp, launchable;
    }

    List<Pkg> apps() throws IOException {
        String out = sh("pm list packages; echo ===; pm list packages -d; echo ===; pm list packages -3; echo ===; "
                + "cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LEANBACK_LAUNCHER; "
                + "cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER");
        String[] part = out.split("===");
        Set<String> all = packages(part[0]);
        Set<String> off = part.length > 1 ? packages(part[1]) : new LinkedHashSet<>();
        Set<String> user = part.length > 2 ? packages(part[2]) : new LinkedHashSet<>();
        Set<String> launch = new LinkedHashSet<>();
        if (part.length > 3) {
            for (String line : part[3].split("\n")) {
                line = line.trim();
                int slash = line.indexOf('/');
                if (slash > 0 && !line.contains(" ")) launch.add(line.substring(0, slash));
            }
        }
        List<Pkg> list = new ArrayList<>();
        for (String p : all) {
            Pkg a = new Pkg();
            a.pkg = p;
            a.enabled = !off.contains(p);
            a.userApp = user.contains(p);
            a.launchable = launch.contains(p);
            list.add(a);
        }
        list.sort((x, y) -> {
            if (x.launchable != y.launchable) return x.launchable ? -1 : 1;
            return Names.of(x.pkg).compareToIgnoreCase(Names.of(y.pkg));
        });
        return list;
    }

    String setEnabled(String pkg, boolean on) throws IOException {
        if (isProtected(pkg)) return Names.of(pkg) + " is needed by the TV, so it stays on.";
        String r = sh(on ? "pm enable " + pkg : "pm disable-user --user 0 " + pkg);
        return r.contains("new state") ? Names.of(pkg) + (on ? " is on." : " is off.") : r;
    }

    String uninstall(String pkg) throws IOException {
        if (isProtected(pkg)) return Names.of(pkg) + " is needed by the TV.";
        String r = sh("pm uninstall " + pkg);
        return r.contains("Success") ? Names.of(pkg) + " was uninstalled." : r;
    }

    String stop(String pkg) throws IOException {
        if (isProtected(pkg)) return Names.of(pkg) + " is needed by the TV.";
        sh("am force-stop " + pkg);
        return Names.of(pkg) + " was stopped.";
    }

    String launch(String pkg) throws IOException {
        String r = sh("monkey -p " + pkg + " -c android.intent.category.LEANBACK_LAUNCHER 1 2>&1 | tail -1; true");
        if (r.contains("No activities")) sh("monkey -p " + pkg + " -c android.intent.category.LAUNCHER 1");
        return "Opened " + Names.of(pkg) + " on the TV.";
    }

    // ------------------------------------------------------------------ installing apps from the phone

    /** The .apk files inside a split-app bundle (.apks / .xapk), or null when the file is one plain APK. */
    static List<java.util.zip.ZipEntry> splits(java.io.File f) {
        try (java.util.zip.ZipFile z = new java.util.zip.ZipFile(f)) {
            if (z.getEntry("AndroidManifest.xml") != null) return null;
            List<java.util.zip.ZipEntry> out = new ArrayList<>();
            java.util.Enumeration<? extends java.util.zip.ZipEntry> e = z.entries();
            while (e.hasMoreElements()) {
                java.util.zip.ZipEntry x = e.nextElement();
                if (!x.isDirectory() && x.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".apk")) out.add(x);
            }
            return out.isEmpty() ? null : out;
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Copies an app to the TV and installs it (or updates it). A bundle of split APKs is installed as one
     * app. Returns pm's answer ("Success" or the reason it refused).
     */
    String installApk(java.io.File apk, AdbLink.Sent progress) throws IOException {
        List<java.util.zip.ZipEntry> parts = splits(apk);
        if (parts == null) {
            String tmp = "/data/local/tmp/freetv-app.apk";
            try (java.io.InputStream in = new java.io.FileInputStream(apk)) {
                link.push(in, apk.length(), tmp, progress);
            }
            return sh("pm install -r -g " + tmp + " 2>&1; rm -f " + tmp);
        }
        long total = 0;
        for (java.util.zip.ZipEntry e : parts) total += Math.max(0, e.getSize());
        String made = sh("pm install-create -r -g -S " + total + " 2>&1");
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\[(\\d+)\\]").matcher(made);
        if (!m.find()) return made;
        String session = m.group(1);
        final long all = total;
        long[] before = {0};
        try (java.util.zip.ZipFile z = new java.util.zip.ZipFile(apk)) {
            int i = 0;
            for (java.util.zip.ZipEntry e : parts) {
                String tmp = "/data/local/tmp/freetv-split" + i + ".apk";
                long base = before[0];
                try (java.io.InputStream in = z.getInputStream(e)) {
                    link.push(in, e.getSize(), tmp, (d, t) -> {
                        if (progress != null) progress.bytes(base + d, all);
                    });
                }
                String w = sh("pm install-write -S " + e.getSize() + " " + session + " split" + i + ".apk " + tmp
                        + " 2>&1; rm -f " + tmp);
                if (!w.contains("Success")) {
                    sh("pm install-abandon " + session);
                    return w;
                }
                before[0] += e.getSize();
                i++;
            }
        }
        return sh("pm install-commit " + session + " 2>&1");
    }

    /** pm's refusal in plain words, or null when it worked. */
    static String installProblem(String r) {
        if (r != null && r.contains("Success")) return null;
        String s = r == null ? "" : r;
        if (s.contains("NO_MATCHING_ABIS")) return "This app is made for a different kind of processor than the TV's. Look for an ARM (armeabi-v7a) version.";
        if (s.contains("OLDER_SDK")) return "This app needs a newer Android version than the TV has.";
        if (s.contains("UPDATE_INCOMPATIBLE")) return "A copy of this app from someone else is on the TV. Uninstall it on the TV first, then try again.";
        if (s.contains("VERSION_DOWNGRADE")) return "A newer version of this app is already on the TV.";
        if (s.contains("INSUFFICIENT_STORAGE")) return "Not enough space on the TV. Free some in Apps on the TV, then try again.";
        if (s.contains("INVALID_APK") || s.contains("PARSE_FAILED") || s.contains("NOT_APK")) return "This file isn't a working app (APK).";
        if (s.contains("MISSING_SPLIT")) return "This app comes in parts and one is missing. Use the full .apks or .xapk file.";
        if (s.contains("USER_RESTRICTED") || s.contains("VERIFICATION")) return "The TV blocked it. Check Settings > Security on the TV.";
        return "The TV refused it: " + s.trim();
    }
}
