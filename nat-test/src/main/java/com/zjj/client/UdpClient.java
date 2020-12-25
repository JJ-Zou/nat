package com.zjj.client;

import com.zjj.constant.Constants;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class UdpClient extends AbstractClient {
    private final NioEventLoopGroup group = new NioEventLoopGroup();

    private Channel channel;

    private IpAddrHolder ipAddrHolder;

    private UdpClientChannelHandler udpClientChannelHandler;

    public UdpClient(IpAddrHolder ipAddrHolder) {
        this.ipAddrHolder = ipAddrHolder;
    }

    public void doBind() {
        udpClientChannelHandler = new UdpClientChannelHandler(this, ipAddrHolder);
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
    }

    public void doClose() {
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
    }

    public void sendPrivateAddr() {
        udpClientChannelHandler.sendPrivateAddr();
    }

}
