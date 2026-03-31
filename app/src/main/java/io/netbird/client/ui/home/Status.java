package io.netbird.client.ui.home;


import io.netbird.gomobile.android.Android;

import java.util.Locale;

public enum Status {
   IDLE,
   CONNECTING,
   CONNECTED,
   UNKNOWN;

   @Override
   public String toString() {
      switch (this) {
         case IDLE:
            return "idle";
         case CONNECTING:
            return "connecting";
         case CONNECTED:
            return "connected";
         default:
            return super.toString();
      }
   }

   public static Status fromLong(long status) {
      if (status == Android.ConnStatusIdle) {
         return IDLE;
      } else if (status == Android.ConnStatusConnecting) {
         return CONNECTING;
      } else if (status == Android.ConnStatusConnected) {
         return CONNECTED;
      }
      return UNKNOWN;
   }

   public static Status fromString(String status) {
      if (status == null) {
         throw new IllegalArgumentException("Status string cannot be null");
      }

      switch (status.toLowerCase(Locale.ROOT)) {
         case "idle":
            return IDLE;
         case "connecting":
            return CONNECTING;
         case "connected":
            return CONNECTED;
         default:
            throw new IllegalArgumentException("Unknown status: " + status.toLowerCase(Locale.ROOT));
      }
   }
}

