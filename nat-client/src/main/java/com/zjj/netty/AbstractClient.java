package com.zjj.netty;

import java.net.InetSocketAddress;

public abstract class AbstractClient implements NettyClient {

    private String localId;
    private InetSocketAddress localAddress;
    private InetSocketAddress serverAddress;

    public AbstractClient(String localId, String localIp, int localPort, String serverIp, int serverPort) {
        this.localId = localId;
        this.localAddress = new InetSocketAddress(localIp, localPort);
        this.serverAddress = new InetSocketAddress(serverIp, serverPort);
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
