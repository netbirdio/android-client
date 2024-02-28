package io.netbird.client.tool;

class Route {
   public String addr;
   public int prefixLength;
   Route(String route) throws Exception {
      String[] r = route.split("/");
      if(r.length != 2) {
         throw new Exception("invalid route");
      }
      addr = r[0];
      prefixLength = Integer.parseInt(r[1]);
   }
}