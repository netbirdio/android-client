package io.netbird.client;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;

import io.netbird.client.tool.VPNService;

public class NetbirdWidgetProvider extends AppWidgetProvider {
    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        NetbirdWidgetUpdater.updateWidgets(context, appWidgetManager, appWidgetIds);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (intent != null && VPNService.ACTION_WIDGET_REFRESH.equals(intent.getAction())) {
            NetbirdWidgetUpdater.updateAllWidgets(context);
        }
    }
}
