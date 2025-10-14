package io.netbird.client.tool.networks;

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;


public class Constants {
    @IntDef(value = {NetworkType.NONE, NetworkType.WIFI, NetworkType.MOBILE})
    @Retention(RetentionPolicy.SOURCE)
    public @interface NetworkType {
        int NONE = 0;
        int WIFI = 1;
        int MOBILE = 2;
    }
}