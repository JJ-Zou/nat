package com.zjj.server;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class UdpServer {
    private static final int PORT = 20000;
    private static final int PORT2 = 30000;

    public static void main(String[] args) {
        NioEventLoopGroup group = new NioEventLoopGroup();
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(group)
                .channel(NioDatagramChannel.class)
                .option(ChannelOption.SO_BROADCAST, true)
                .handler(new ChannelInitializer<NioDatagramChannel>() {
                    @Override
                    protected void initChannel(NioDatagramChannel ch) throws Exception {
                        ch.pipeline()
                                .addLast(new UdpServerChannelHandler());
                    }
                });
        try {
            ChannelFuture future = bootstrap.bind(PORT).syncUninterruptibly();
            ChannelFuture future1 = bootstrap.bind(PORT2).syncUninterruptibly();
            Channel channel = future.channel();
            Channel channel1 = future1.channel();
            channel.closeFuture().syncUninterruptibly();
            channel1.closeFuture().syncUninterruptibly();
        } finally {
            group.shutdownGracefully();
        }
    }
}
