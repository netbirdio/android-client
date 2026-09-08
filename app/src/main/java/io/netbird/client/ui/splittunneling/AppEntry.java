package io.netbird.client.ui.splittunneling;

import android.graphics.drawable.Drawable;

/** One installed application, as shown in the split tunnelling list. */
public class AppEntry {

    private final String packageName;
    private final String label;
    private final Drawable icon;

    public AppEntry(String packageName, String label, Drawable icon) {
        this.packageName = packageName;
        this.label = label;
        this.icon = icon;
    }

    public String getPackageName() {
        return packageName;
    }

    public String getLabel() {
        return label;
    }

    public Drawable getIcon() {
        return icon;
    }
}
