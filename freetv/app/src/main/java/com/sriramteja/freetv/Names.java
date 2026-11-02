package com.sriramteja.freetv;

import java.util.HashMap;
import java.util.Map;

/** Friendly names for the packages on this TV; anything else shows its package name. */
final class Names {
    private Names() { }

    private static final Map<String, String> N = new HashMap<>();
    static {
        String[] pairs = {
                "com.google.android.youtube.tv", "YouTube",
                "com.netflix.ninja", "Netflix",
                "com.amazon.amazonvideo.livingroom", "Prime Video",
                "in.startv.hotstar", "JioHotstar",
                "org.videolan.vlc", "VLC",
                "com.gathin.tvhome", "Clean home screen",
                "com.google.android.tvlauncher", "Google TV home (original)",
                "com.mitv.tvhome.atv", "PatchWall",
                "com.android.vending", "Play Store",
                "com.google.android.gms", "Google Play services",
                "com.google.android.katniss", "Google Assistant / voice search",
                "com.google.android.inputmethod.latin", "Gboard keyboard",
                "com.android.tv", "Live TV and inputs",
                "com.android.tv.settings", "TV Settings",
                "com.google.android.apps.mediashell", "Chromecast built-in",
                "com.google.android.backdrop", "Google screensaver (Ambient)",
                "com.google.android.play.games", "Play Games",
                "com.google.android.videos", "Play Movies & TV",
                "com.google.android.youtube.tvmusic", "YouTube Music",
                "com.google.android.tvrecommendations", "Sponsored rows",
                "com.google.android.feedback", "Google feedback",
                "com.google.android.marvin.talkback", "TalkBack",
                "com.google.android.tts", "Text-to-speech",
                "com.google.android.tv.remote.service", "Phone remote",
                "com.google.android.tungsten.setupwraith", "Setup wizard",
                "com.google.android.webview", "WebView",
                "com.xiaomi.mitv.updateservice", "Xiaomi updates",
                "com.xiaomi.mitv.smartshare", "Mi screen mirroring",
                "com.xiaomi.mitv.tvmanager", "Xiaomi TV Manager",
                "com.xiaomi.mitv.mediaexplorer", "Mi media explorer (USB)",
                "com.mitv.videoplayer", "Mi video player (USB)",
                "com.mitv.gallery", "Mi gallery (USB)",
                "com.mitv.dream", "Mi screensaver",
                "com.mitv.milinkservice", "Mi Cast",
                "com.mitv.tvlock", "Xiaomi child lock",
                "com.mitv.tvhome.mitvplus", "Mi TV+",
                "com.xiaomi.mimusic2", "Mi Music",
                "com.miui.tv.analytics", "Xiaomi analytics",
                "com.xiaomi.statistic", "Xiaomi statistics",
                "com.xm.webcontent", "PatchWall web content",
                "com.sonyliv", "SonyLIV",
                "org.courville.nova", "Nova Player",
                "com.rz.night.player", "Night Player",
                "com.xiaomi.mitv.handbook", "Mi TV Handbook",
                "com.jio.media.stb.ondemand", "JioCinema / JioTV",
                "com.graymatrix.did", "ZEE5",
                "com.mxtech.videoplayer.television", "MX Player",
                "com.lionlev.tv.capacity", "Capacity Stats",
                "com.android.bluetooth", "Bluetooth",
                "com.mi.bluetooth", "Remote (Bluetooth)",
                "com.droidlogic.tvinput", "HDMI inputs",
                "com.android.systemui", "System UI",
        };
        for (int i = 0; i < pairs.length; i += 2) N.put(pairs[i], pairs[i + 1]);
    }

    static String of(String pkg) {
        String n = N.get(pkg);
        return n != null ? n : pkg;
    }

    static boolean known(String pkg) {
        return N.containsKey(pkg);
    }
}
