package com.zjj.client;

import java.net.InetSocketAddress;
import java.nio.channels.Channel;

public interface NettyClient {
    InetSocketAddress getLocalAddress();

    InetSocketAddress getServerAddress();

    Channel getChannel();

}
