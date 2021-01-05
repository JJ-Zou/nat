package com.zjj.client;

import io.netty.channel.Channel;

import java.net.InetSocketAddress;

public interface NettyClient {

    void doBind();

    void doClose();

    InetSocketAddress getLocalAddress();

    InetSocketAddress getServerAddress();

    Channel getChannel();

    String getLocalId();

    void attemptNatConnect(String oppositeId);

}
