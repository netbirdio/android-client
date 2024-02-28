package io.netbird.client.tool;

import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.List;

class IFaceDiscover implements io.netbird.gomobile.android.IFaceDiscover{
    @Override
    public String iFaces() throws Exception {
        List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());

        StringBuilder sb = new StringBuilder("");
        for (NetworkInterface nif : interfaces) {
            try {
                sb.append(String.format("%s %d %d %b %b %b %b %b |",
                        nif.getName(),
                        nif.getIndex(),
                        nif.getMTU(),
                        nif.isUp(),
                        nif.supportsMulticast(),
                        nif.isLoopback(),
                        nif.isPointToPoint(),
                        nif.supportsMulticast()));

                for (InterfaceAddress ia : nif.getInterfaceAddresses()) {
                    sb.append(String.format("%s/%d ", ia.getAddress().getHostAddress(), ia.getNetworkPrefixLength()));
                }
            } catch (Exception e) {
                continue;
            }
            sb.append("\n");
        }
        return sb.toString();

    }
}
