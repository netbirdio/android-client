package io.netbird.client.ui.home;


import java.util.Locale;

public enum Status {
   IDLE,
   CONNECTING,
   CONNECTED;

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

