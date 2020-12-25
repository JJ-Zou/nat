package com.zjj.client;

import cn.hutool.core.net.NetUtil;
import cn.hutool.core.util.RandomUtil;
import com.zjj.utils.InetUtils;

import java.net.InetSocketAddress;

public abstract class AbstractClient implements NettyClient {

    protected static final String SERVE_IP = "39.105.65.104";
    protected static final int SERVER_PORT = 20000;

    public AbstractClient() {
        this.localId = RandomUtil.randomString(8);
        this.localAddress = new InetSocketAddress(InetUtils.getLocalAddress(), NetUtil.getUsableLocalPort());
        this.serverAddress = new InetSocketAddress(SERVE_IP, SERVER_PORT);
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
    public synchronized String getLocalId() {
        return localId;
    }
}
