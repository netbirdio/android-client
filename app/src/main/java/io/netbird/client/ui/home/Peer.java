package io.netbird.client.ui.home;

public class Peer {
   private final Status status;
   private final String ip;
   private final String fqdn;

   public Peer(Status status, String ip, String fqdn) {
      this.status = status;
      this.ip = ip;
      this.fqdn = fqdn;
   }

   public Status getStatus() {
      return status;
   }

   public String getIp() {
      return ip;
   }

   public String getFqdn() {
      return fqdn;
   }
}