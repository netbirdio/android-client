package io.netbird.client.tool;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

public class Version {

   public static String getVersionName(Context context) {
      try {
         PackageManager packageManager = context.getPackageManager();
         String packageName = context.getPackageName();
         PackageInfo packageInfo = packageManager.getPackageInfo(packageName, 0);
         return packageInfo.versionName;
      } catch (PackageManager.NameNotFoundException e) {
         e.printStackTrace();
         return "unknown";
      }
   }

   public static int getVersionCode(Context context) {
      try {
         PackageManager packageManager = context.getPackageManager();
         String packageName = context.getPackageName();
         PackageInfo packageInfo = packageManager.getPackageInfo(packageName, 0);
         return packageInfo.versionCode;
      } catch (PackageManager.NameNotFoundException e) {
         e.printStackTrace();
         return -1;
      }
   }

   public static boolean isDebuggable(Context context) {
      return (0 != (context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE));
   }
}