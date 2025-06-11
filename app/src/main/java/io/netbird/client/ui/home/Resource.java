package io.netbird.client.ui.home;

public class Resource {
   private final String name;
   private final String address;
   private final String peer;

   public Resource(String name, String address, String peer) {
      this.name = name;
      this.address = address;
      this.peer = peer;
   }


   public String getName() {
      return name;
   }

   public String getAddress() {
      return address;
   }

    public String getPeer() {
        return peer;
    }
}