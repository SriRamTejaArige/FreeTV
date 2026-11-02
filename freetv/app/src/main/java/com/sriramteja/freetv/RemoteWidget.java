package com.sriramteja.freetv;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.widget.RemoteViews;

/** Phone home-screen widget: the TV's arrows, OK, Back, Home and volume. */
public class RemoteWidget extends AppWidgetProvider {

    private static final int[] IDS = {R.id.w_up, R.id.w_down, R.id.w_left, R.id.w_right, R.id.w_ok, R.id.w_back,
            R.id.w_home, R.id.w_voldown, R.id.w_volup};
    private static final String[] KEYS = {"KEYCODE_DPAD_UP", "KEYCODE_DPAD_DOWN", "KEYCODE_DPAD_LEFT",
            "KEYCODE_DPAD_RIGHT", "KEYCODE_DPAD_CENTER", "KEYCODE_BACK", "KEYCODE_HOME", "KEYCODE_VOLUME_DOWN",
            "KEYCODE_VOLUME_UP"};

    @Override
    public void onUpdate(Context ctx, AppWidgetManager manager, int[] ids) {
        for (int id : ids) manager.updateAppWidget(id, views(ctx));
    }

    static RemoteViews views(Context ctx) {
        RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.w_remote);
        for (int i = 0; i < IDS.length; i++) v.setOnClickPendingIntent(IDS[i], WidgetAction.pending(ctx, "key:" + KEYS[i], 100 + i));
        return v;
    }
}
