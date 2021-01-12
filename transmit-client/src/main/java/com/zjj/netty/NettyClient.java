package com.zjj.netty;

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

    void putThread(String id, Thread t);

    Thread getThread(String id);

    void removeThread(String id);
}
