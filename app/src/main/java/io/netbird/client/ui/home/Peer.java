package io.netbird.client.ui.home;

import java.util.List;
import java.util.Objects;

public class Peer {
   /** Go's zero time, as formatted by the Android binding. Means "no value yet". */
   private static final String ZERO_TIME = "0001-01-01 00:00:00";

   private final Status status;
   private final String ip;
   private final String ipv6;
   private final String fqdn;

   private final String pubKey;
   private final String latency;
   private final long latencyMs;
   private final long bytesRx;
   private final long bytesTx;
   private final String connStatusUpdate;
   private final boolean relayed;
   private final boolean rosenpassEnabled;
   private final String lastWireguardHandshake;
   private final String localIceCandidateType;
   private final String remoteIceCandidateType;
   private final String localIceCandidateEndpoint;
   private final String remoteIceCandidateEndpoint;
   private final List<String> routes;

   public Peer(Status status, String ip, String ipv6, String fqdn,
               String pubKey, String latency, long latencyMs, long bytesRx, long bytesTx,
               String connStatusUpdate, boolean relayed, boolean rosenpassEnabled,
               String lastWireguardHandshake, String localIceCandidateType,
               String remoteIceCandidateType, String localIceCandidateEndpoint,
               String remoteIceCandidateEndpoint, List<String> routes) {
      this.status = status;
      this.ip = ip;
      this.ipv6 = ipv6;
      this.fqdn = fqdn;
      this.pubKey = pubKey;
      this.latency = latency;
      this.latencyMs = latencyMs;
      this.bytesRx = bytesRx;
      this.bytesTx = bytesTx;
      this.connStatusUpdate = connStatusUpdate;
      this.relayed = relayed;
      this.rosenpassEnabled = rosenpassEnabled;
      this.lastWireguardHandshake = lastWireguardHandshake;
      this.localIceCandidateType = localIceCandidateType;
      this.remoteIceCandidateType = remoteIceCandidateType;
      this.localIceCandidateEndpoint = localIceCandidateEndpoint;
      this.remoteIceCandidateEndpoint = remoteIceCandidateEndpoint;
      this.routes = routes;
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

   public String getPubKey() {
      return pubKey;
   }

   public String getLatency() {
      return latency;
   }

   /** Latency in milliseconds; 0 when unknown. Drives the colour coding. */
   public long getLatencyMs() {
      return latencyMs;
   }

   public long getBytesRx() {
      return bytesRx;
   }

   public long getBytesTx() {
      return bytesTx;
   }

   /** UTC timestamp as "yyyy-MM-dd HH:mm:ss", or the zero time if never set. */
   public String getConnStatusUpdate() {
      return connStatusUpdate;
   }

   public boolean isRelayed() {
      return relayed;
   }

   public boolean isRosenpassEnabled() {
      return rosenpassEnabled;
   }

   /** UTC timestamp as "yyyy-MM-dd HH:mm:ss", or the zero time if it never happened. */
   public String getLastWireguardHandshake() {
      return lastWireguardHandshake;
   }

   public String getLocalIceCandidateType() {
      return localIceCandidateType;
   }

   public String getRemoteIceCandidateType() {
      return remoteIceCandidateType;
   }

   public String getLocalIceCandidateEndpoint() {
      return localIceCandidateEndpoint;
   }

   public String getRemoteIceCandidateEndpoint() {
      return remoteIceCandidateEndpoint;
   }

   public List<String> getRoutes() {
      return routes;
   }

   /** True when the timestamp is Go's zero value, i.e. the event never happened. */
   public static boolean isNever(String timestamp) {
      return timestamp == null || timestamp.isEmpty() || ZERO_TIME.equals(timestamp);
   }

   /**
    * Value equality over every displayed field. The detail screen polls for fresh
    * transfer counters, so it needs to tell "same data again" from a real change
    * and skip rebuilding its rows when nothing moved.
    */
   @Override
   public boolean equals(Object o) {
      if (this == o) {
         return true;
      }
      if (!(o instanceof Peer)) {
         return false;
      }
      Peer other = (Peer) o;
      return status == other.status
              && latencyMs == other.latencyMs
              && bytesRx == other.bytesRx
              && bytesTx == other.bytesTx
              && relayed == other.relayed
              && rosenpassEnabled == other.rosenpassEnabled
              && Objects.equals(ip, other.ip)
              && Objects.equals(ipv6, other.ipv6)
              && Objects.equals(fqdn, other.fqdn)
              && Objects.equals(pubKey, other.pubKey)
              && Objects.equals(latency, other.latency)
              && Objects.equals(connStatusUpdate, other.connStatusUpdate)
              && Objects.equals(lastWireguardHandshake, other.lastWireguardHandshake)
              && Objects.equals(localIceCandidateType, other.localIceCandidateType)
              && Objects.equals(remoteIceCandidateType, other.remoteIceCandidateType)
              && Objects.equals(localIceCandidateEndpoint, other.localIceCandidateEndpoint)
              && Objects.equals(remoteIceCandidateEndpoint, other.remoteIceCandidateEndpoint)
              && Objects.equals(routes, other.routes);
   }

   @Override
   public int hashCode() {
      return Objects.hash(status, ip, ipv6, fqdn, pubKey, latency, latencyMs, bytesRx, bytesTx,
              connStatusUpdate, relayed, rosenpassEnabled, lastWireguardHandshake,
              localIceCandidateType, remoteIceCandidateType, localIceCandidateEndpoint,
              remoteIceCandidateEndpoint, routes);
   }
}
