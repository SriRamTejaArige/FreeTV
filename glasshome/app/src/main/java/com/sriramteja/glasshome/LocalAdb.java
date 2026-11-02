package com.sriramteja.glasshome;

import android.content.Context;

import java.io.IOException;

/**
 * The TV's own adb, reached from inside the TV (127.0.0.1:5555, open because network debugging is on).
 * It can do what an app may not: a real force-stop and a list of the processes running now. The first
 * connection makes the TV ask once to allow Glass Home ("Always allow"). Call off the main thread.
 */
final class LocalAdb {
    private LocalAdb() { }

    private static AdbLink link;

    static synchronized String shell(Context ctx, String command, AdbLink.Prompt prompt) throws IOException {
        try {
            if (link == null) link = new AdbLink(ctx.getFilesDir());
            if (!link.isConnected()) link.connect("127.0.0.1", 5555, prompt);
            String out = link.shell(command);
            ctx.getSharedPreferences("glass", Context.MODE_PRIVATE).edit().putBoolean("adb_ok", true).apply();
            return out;
        } catch (IOException e) {
            if (link != null) link.close();
            throw e;
        } catch (Exception e) {
            if (link != null) link.close();
            throw new IOException(e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }
}
