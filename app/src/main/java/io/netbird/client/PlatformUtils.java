package io.netbird.client;

import android.app.UiModeManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.util.Base64;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.Enumeration;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

public final class PlatformUtils {

    private PlatformUtils() {
    }

    public static boolean isAndroidTV(Context context) {
        UiModeManager uiModeManager = (UiModeManager) context.getSystemService(Context.UI_MODE_SERVICE);
        if (uiModeManager != null) {
            return uiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION;
        }
        return false;
    }

    public static boolean isChromeOS(Context context) {
        PackageManager pm = context.getPackageManager();
        return pm.hasSystemFeature("org.chromium.arc")
                || pm.hasSystemFeature("org.chromium.arc.device_management");
    }

    public static boolean requiresDeviceCodeFlow(Context context) {
        return isAndroidTV(context) || isChromeOS(context);
    }

    public static byte[] getSystemAndUserCertificates() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidCAStore");
        keyStore.load(null, null);

        StringBuilder pemBuilder = new StringBuilder();
        Enumeration<String> aliases = keyStore.aliases();

        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            if (!alias.startsWith("user:")) {
                continue;
            }

            X509Certificate cert = (X509Certificate) keyStore.getCertificate(alias);
            if (cert != null) {
                String base64Cert = Base64.encodeToString(cert.getEncoded(), Base64.NO_WRAP);
                
                pemBuilder.append("-----BEGIN CERTIFICATE-----\n");
                for (int i = 0; i < base64Cert.length(); i += 64) {
                    int end = Math.min(i + 64, base64Cert.length());
                    pemBuilder.append(base64Cert.substring(i, end)).append("\n");
                }
                pemBuilder.append("-----END CERTIFICATE-----\n");
            }
        }

        return pemBuilder.toString().getBytes("UTF-8");
    }
}
