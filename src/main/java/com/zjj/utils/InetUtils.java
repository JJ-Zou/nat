package com.zjj.utils;

import java.net.InetSocketAddress;

public class InetUtils {
    public static String toAddressString(InetSocketAddress address) {
        return address.getAddress().getHostAddress() + ":" + address.getPort();
    }

    public static InetSocketAddress toInetSocketAddress(String addressString) {
        String[] split = addressString.split(":");
        return new InetSocketAddress(split[0], Integer.parseInt(split[1]));
    }
}
