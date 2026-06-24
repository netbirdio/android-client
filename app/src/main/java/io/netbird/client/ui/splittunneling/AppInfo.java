package io.netbird.client.ui.splittunneling;

import android.graphics.drawable.Drawable;

public class AppInfo {
    public final String name;
    public final String packageName;
    public final Drawable icon;
    public boolean isSelected;

    public AppInfo(String name, String packageName, Drawable icon, boolean isSelected) {
        this.name = name;
        this.packageName = packageName;
        this.icon = icon;
        this.isSelected = isSelected;
    }
}
