package io.netbird.client.ui.home;


public enum Status {
   CONNECTED,
   DISCONNECTED;

   @Override
   public String toString() {
      switch (this) {
         case CONNECTED:
            return "connected";
         case DISCONNECTED:
            return "disconnected";
         default:
            return super.toString();
      }
   }

   public static Status fromString(String status) {
      if (status == null) {
         throw new IllegalArgumentException("Status string cannot be null");
      }

      switch (status.toLowerCase()) {
         case "connected":
            return CONNECTED;
         case "disconnected":
            return DISCONNECTED;
         default:
            throw new IllegalArgumentException("Unknown status: " + status);
      }
   }
}

