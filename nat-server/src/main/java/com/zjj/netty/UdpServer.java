package com.zjj.netty;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;

@Slf4j
@Component
public class UdpServer {

    @Value("${netty.udp.port}")
    private int port;

    private NioEventLoopGroup worker;

    @Resource
    private UdpServerChannelHandler udpServerChannelHandler;

    @PostConstruct
    public void doStart() {
        worker = new NioEventLoopGroup();
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(worker)
                .channel(NioDatagramChannel.class)
                .option(ChannelOption.SO_BROADCAST, true)
                .handler(new ChannelInitializer<NioDatagramChannel>() {
                    @Override
                    protected void initChannel(NioDatagramChannel ch) throws Exception {
                        ch.pipeline()
                                .addLast(udpServerChannelHandler)
                        ;
                    }
                });
        bootstrap.bind(port).syncUninterruptibly().addListener(f -> {
            if (f.isSuccess()) {
                log.debug("netty服务器监听端口: {}", port);
            } else {
                log.error("netty服务器启动失败");
            }
        });
    }

    @PreDestroy
    public void doClose() {
        worker.shutdownGracefully().syncUninterruptibly();
    }
}
