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
}

