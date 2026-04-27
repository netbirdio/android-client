package io.netbird.client;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import io.netbird.client.tool.Preferences;
import io.netbird.client.tool.VPNService;

public class NetbirdWidgetUpdater {
    private static final int REQUEST_TOGGLE_CONNECTION = 1001;
    private static final int REQUEST_TOGGLE_EXIT_NODE = 1002;

    public static void updateAllWidgets(Context context) {
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        ComponentName componentName = new ComponentName(context, NetbirdWidgetProvider.class);
        updateWidgets(context, appWidgetManager, appWidgetManager.getAppWidgetIds(componentName));
    }

    public static void updateWidgets(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            appWidgetManager.updateAppWidget(appWidgetId, createRemoteViews(context));
        }
    }

    private static RemoteViews createRemoteViews(Context context) {
        Preferences preferences = new Preferences(context);
        boolean vpnRunning = preferences.isWidgetVpnRunning();
        boolean exitNodeActive = preferences.isWidgetExitNodeActive();
        String exitNodeName = preferences.getWidgetExitNodeName();

        if (!vpnRunning && isEmpty(exitNodeName)) {
            exitNodeName = preferences.getLastExitNodeRoute();
        }
        boolean exitNodeAvailable = !isEmpty(exitNodeName);

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_netbird);
        views.setTextViewText(R.id.widget_connection_status,
                context.getString(vpnRunning ? R.string.widget_status_connected : R.string.main_status_disconnected));
        views.setContentDescription(
                R.id.widget_connection_switch,
                context.getString(vpnRunning
                        ? R.string.widget_connection_switch_connected
                        : R.string.widget_connection_switch_disconnected));

        int connectionColor = context.getColor(vpnRunning ? R.color.nb_orange : R.color.nb_button_inactive);
        views.setTextColor(R.id.widget_connection_status, connectionColor);
        views.setInt(R.id.widget_connection_icon, "setColorFilter",
                context.getColor(vpnRunning ? R.color.nb_orange : R.color.white));
        views.setInt(R.id.widget_connection_switch, "setBackgroundResource",
                vpnRunning ? R.drawable.widget_switch_on : R.drawable.widget_switch_off);

        if (!exitNodeAvailable) {
            views.setTextViewText(R.id.widget_exit_status, context.getString(R.string.widget_exit_node_unavailable));
        } else {
            views.setTextViewText(R.id.widget_exit_status, exitNodeName);
        }

        int exitColor = context.getColor(exitNodeActive ? R.color.nb_orange : R.color.nb_button_inactive);
        views.setTextColor(R.id.widget_exit_status, exitColor);
        views.setInt(R.id.widget_exit_switch, "setBackgroundResource",
                exitNodeActive ? R.drawable.widget_switch_on : R.drawable.widget_switch_off);
        views.setContentDescription(
                R.id.widget_exit_switch,
                context.getString(exitNodeAvailable
                        ? (exitNodeActive
                                ? R.string.widget_exit_switch_enabled
                                : R.string.widget_exit_switch_disabled)
                        : R.string.widget_exit_switch_unavailable));

        PendingIntent connectionIntent = servicePendingIntent(
                context,
                VPNService.ACTION_WIDGET_TOGGLE_CONNECTION,
                REQUEST_TOGGLE_CONNECTION);

        views.setOnClickPendingIntent(R.id.widget_connection_switch,
                connectionIntent);
        views.setOnClickPendingIntent(R.id.widget_connection_icon,
                connectionIntent);
        views.setOnClickPendingIntent(R.id.widget_connection_status,
                connectionIntent);

        if (exitNodeAvailable) {
            PendingIntent exitNodeIntent = servicePendingIntent(
                    context,
                    VPNService.ACTION_WIDGET_TOGGLE_EXIT_NODE,
                    REQUEST_TOGGLE_EXIT_NODE);
            views.setOnClickPendingIntent(R.id.widget_exit_switch,
                    exitNodeIntent);
            views.setOnClickPendingIntent(R.id.widget_exit_icon,
                    exitNodeIntent);
            views.setOnClickPendingIntent(R.id.widget_exit_status,
                    exitNodeIntent);
        }

        return views;
    }

    private static boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static PendingIntent servicePendingIntent(Context context, String action, int requestCode) {
        Intent intent = new Intent(context, VPNService.class);
        intent.setAction(action);
        return PendingIntent.getForegroundService(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }
}
