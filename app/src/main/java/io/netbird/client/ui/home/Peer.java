package io.netbird.client.ui.home;

public class Peer {
   private final Status status;
   private final String ip;
   private final String ipv6;
   private final String fqdn;

   public Peer(Status status, String ip, String ipv6, String fqdn) {
      this.status = status;
      this.ip = ip;
      this.ipv6 = ipv6;
      this.fqdn = fqdn;
   }

   public Status getStatus() {
      return status;
   }

   public String getIp() {
      return ip;
   }

   public String getIpv6() {
      return ipv6;
   }

   public String getFqdn() {
      return fqdn;
   }
}
