package com.zjj.utils;

import cn.hutool.core.net.NetUtil;

import java.net.*;
import java.util.Collection;
import java.util.List;

public class InetUtils {

    private InetUtils() {
    }

    public static String toAddressString(InetSocketAddress address) {
        return address.getAddress().getHostAddress() + ":" + address.getPort();
    }

    public static InetSocketAddress toInetSocketAddress(String addressString) {
        String[] split = addressString.split(":");
        return new InetSocketAddress(split[0], Integer.parseInt(split[1]));
    }

    public static String getLocalAddress() {
        InetAddress ret = null;
        Collection<NetworkInterface> networkInterfaces = NetUtil.getNetworkInterfaces();
        for (NetworkInterface networkInterface : networkInterfaces) {
            try {
                if (networkInterface.isUp()
                        && !networkInterface.isVirtual()
                        && !networkInterface.isLoopback()
                        && !networkInterface.getName().contains("docker")
                        && !networkInterface.getName().contains("lo")) {
                    List<InterfaceAddress> interfaceAddresses = networkInterface.getInterfaceAddresses();
                    for (InterfaceAddress interfaceAddress : interfaceAddresses) {
                        InetAddress address = interfaceAddress.getAddress();
                        if (address.isSiteLocalAddress()) {
                            return address.getHostAddress();
                        } else {
                            ret = address;
                        }
                    }
                }
            } catch (SocketException e) {
                e.printStackTrace();
            }
        }
        if (ret != null) {
            return ret.getHostAddress();
        }
        return NetUtil.getLocalhost().getHostAddress();
    }

}
