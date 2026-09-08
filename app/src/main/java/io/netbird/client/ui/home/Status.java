package io.netbird.client.ui.home;


import androidx.annotation.StringRes;

import io.netbird.client.R;
import io.netbird.gomobile.android.Android;

import java.util.Locale;

public enum Status {
   IDLE,
   CONNECTING,
   CONNECTED,
   UNKNOWN;

   /** Translated label for display. toString() stays the wire-format value. */
   @StringRes
   public int labelRes() {
      switch (this) {
         case IDLE:
            return R.string.peer_status_idle;
         case CONNECTING:
            return R.string.peer_status_connecting;
         case CONNECTED:
            return R.string.peer_status_connected;
         default:
            return R.string.peer_status_unknown;
      }
   }

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

