package com.couchcrypt.lobby;

import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class LanAddress {
    private LanAddress() {}
    public static List<String> find() {
        List<String> addresses = new ArrayList<>();
        try {
            for (NetworkInterface iface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!iface.isUp() || iface.isLoopback() || iface.isVirtual()
                    || iface.getName().startsWith("utun")) continue;
                for (var address : Collections.list(iface.getInetAddresses())) {
                    if (address instanceof Inet4Address && address.isSiteLocalAddress())
                        addresses.add(address.getHostAddress());
                }
            }
        } catch (Exception ignored) { /* UI explicitly handles no local address. */ }
        return addresses;
    }
}
