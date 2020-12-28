package com.zjj.netty;

import java.net.InetSocketAddress;

public abstract class AbstractClient implements NettyClient {

    public AbstractClient(String localId, String localIp, int localPort, String serverIp, int serverPort) {
        this.localId = localId;
        this.localAddress = new InetSocketAddress(localIp, localPort);
        this.serverAddress = new InetSocketAddress(serverIp, serverPort);
    }

    private String localId;
    private InetSocketAddress localAddress;
    private InetSocketAddress serverAddress;


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
