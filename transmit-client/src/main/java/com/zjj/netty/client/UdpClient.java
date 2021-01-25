package com.zjj.netty.client;

import com.zjj.config.NettyProperties;
import com.zjj.constant.Constants;
import com.zjj.netty.AbstractClient;
import com.zjj.netty.IpAddrHolder;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.LockSupport;

@Slf4j
@Component()
public class UdpClient extends AbstractClient {
    private static final Map<String, Thread> THREAD_MAP = new ConcurrentHashMap<>();
    private final NioEventLoopGroup group = new NioEventLoopGroup();
    private Channel channel;
    @Resource(name = "natThroughProcessor")
    private IpAddrHolder ipAddrHolder;

    @Resource
    private UdpClientChannelHandler udpClientChannelHandler;

    @Autowired
    public UdpClient(NettyProperties nettyProperties) {
        super(nettyProperties);
    }

    public void doBind() {
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(group)
                .channel(NioDatagramChannel.class)
                .option(ChannelOption.SO_BROADCAST, true)
                .handler(new ChannelInitializer<DatagramChannel>() {
                    @Override
                    protected void initChannel(DatagramChannel ch) throws Exception {
                        ch.pipeline()
                                .addLast(udpClientChannelHandler)
                        ;
                    }
                });
        ChannelFuture channelFuture = bootstrap.bind(getLocalAddress()).syncUninterruptibly();
        channel = channelFuture.channel();
        udpClientChannelHandler.sendPrivateAddr();
    }

    public void doClose() {
        log.debug("关闭 {}", group);
        group.shutdownGracefully().syncUninterruptibly();
    }

    @Override
    public Channel getChannel() {
        return channel;
    }

    @Override
    public void attemptNatConnect(String oppositeId) {
        ipAddrHolder.setThrough(oppositeId, Constants.NONE);
        ipAddrHolder.attemptNatConnect(this, oppositeId);
        long start = System.currentTimeMillis();
        putThread(oppositeId, Thread.currentThread());
        LockSupport.parkNanos(3_000_000_000L);
        if (System.currentTimeMillis() - start >= 3000L) {
            log.info("UDP穿透失败!");
        } else {
            log.info("UDP穿透成功!");
        }
        removeThread(oppositeId);
    }

    @Override
    public void putThread(String id, Thread t) {
        THREAD_MAP.put(id, t);
    }

    @Override
    public Thread getThread(String id) {
        return THREAD_MAP.get(id);
    }

    @Override
    public void removeThread(String id) {
        THREAD_MAP.remove(id);
    }
}
