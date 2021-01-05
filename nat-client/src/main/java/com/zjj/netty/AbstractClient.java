package com.zjj.netty;

import com.zjj.config.NettyProperties;

import java.net.InetSocketAddress;

public abstract class AbstractClient implements NettyClient {

    private String localId;
    private InetSocketAddress localAddress;
    private InetSocketAddress serverAddress;

    public AbstractClient(NettyProperties nettyProperties) {
        NettyProperties.ClientProperties clientProperties = nettyProperties.getClientProperties();
        NettyProperties.ServerProperties serverProperties = nettyProperties.getServerProperties();
        this.localId = clientProperties.getId();
        this.localAddress = new InetSocketAddress(clientProperties.getIp(), clientProperties.getPort());
        this.serverAddress = new InetSocketAddress(serverProperties.getIp(), serverProperties.getPort());
    }

    @Override
    public InetSocketAddress getLocalAddress() {
        return localAddress;
    }

    @Override
    public InetSocketAddress getServerAddress() {
        return serverAddress;
    }

    @Override
    public String getLocalId() {
        return localId;
    }


}
