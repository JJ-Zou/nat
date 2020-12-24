package com.zjj.client;

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
public class UdpClientRemote extends AbstractClient {
    private final NioEventLoopGroup group = new NioEventLoopGroup();

    private Channel channel;


    public void doBind() {
        final UdpClientChannelHandler udpClientChannelHandler = new UdpClientChannelHandler(this);
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
}
