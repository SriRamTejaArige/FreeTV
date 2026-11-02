package com.sriramteja.freetv;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

/** Phone home-screen widget: next look, screensaver, Home and free RAM, with a status line. */
public class QuickWidget extends AppWidgetProvider {

    @Override
    public void onUpdate(Context ctx, AppWidgetManager manager, int[] ids) {
        for (int id : ids) manager.updateAppWidget(id, views(ctx));
    }

    static RemoteViews views(Context ctx) {
        RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.w_quick);
        String status = ctx.getSharedPreferences("freetv", Context.MODE_PRIVATE).getString("w_status", "Your TV, one tap away");
        v.setTextViewText(R.id.w_status, status);
        v.setOnClickPendingIntent(R.id.w_theme, WidgetAction.pending(ctx, "theme", 200));
        v.setOnClickPendingIntent(R.id.w_saver, WidgetAction.pending(ctx, "saver", 201));
        v.setOnClickPendingIntent(R.id.w_qhome, WidgetAction.pending(ctx, "key:KEYCODE_HOME", 202));
        v.setOnClickPendingIntent(R.id.w_free, WidgetAction.pending(ctx, "free", 203));
        Intent open = new Intent(ctx, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        v.setOnClickPendingIntent(R.id.w_open, PendingIntent.getActivity(ctx, 204, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        return v;
    }

    /** Shows a new status line on every quick widget. */
    static void say(Context ctx, String status) {
        ctx.getSharedPreferences("freetv", Context.MODE_PRIVATE).edit().putString("w_status", status).apply();
        AppWidgetManager m = AppWidgetManager.getInstance(ctx);
        int[] ids = m.getAppWidgetIds(new ComponentName(ctx, QuickWidget.class));
        for (int id : ids) m.updateAppWidget(id, views(ctx));
    }
}
