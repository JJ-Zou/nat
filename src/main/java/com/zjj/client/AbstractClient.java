package com.zjj.client;

import cn.hutool.core.net.NetUtil;
import cn.hutool.core.util.RandomUtil;
import com.zjj.utils.InetUtils;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class AbstractClient implements NettyClient {

    protected static final String SERVE_IP = "39.105.65.104";
    protected static final int SERVER_PORT = 20000;

    private String localId;
    private InetSocketAddress localAddress;
    private InetSocketAddress serverAddress;

    private AtomicBoolean through = new AtomicBoolean(false);

    public boolean getThrough() {
        return through.get();
    }

    public void setThrough() {
        through.compareAndSet(false, true);
    }

    @Override
    public synchronized InetSocketAddress getLocalAddress() {
        if (localAddress == null) {
            localAddress = new InetSocketAddress(InetUtils.getLocalAddress(), NetUtil.getUsableLocalPort());
        }
        return localAddress;
    }

    @Override
    public synchronized InetSocketAddress getServerAddress() {
        if (serverAddress == null) {
            serverAddress = new InetSocketAddress(SERVE_IP, SERVER_PORT);
        }
        return serverAddress;
    }


    @Override
    public synchronized String getLocalId() {
        if (localId == null) {
            localId = RandomUtil.randomString(8);
        }
        return localId;
    }
}
