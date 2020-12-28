package com.zjj.netty.client;

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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Slf4j
@Component()
public class UdpClient extends AbstractClient {
    private final NioEventLoopGroup group = new NioEventLoopGroup();

    private Channel channel;

    @Resource(name = "natThroughProcessor")
    private IpAddrHolder ipAddrHolder;

    @Resource
    private UdpClientChannelHandler udpClientChannelHandler;

    public UdpClient(@Value("${netty.client.id}") String localId,
                     @Value("${netty.client.ip}") String localIp,
                     @Value("${netty.client.port}") int localPort,
                     @Value("${netty.server.ip}") String serverIp,
                     @Value("${netty.server.port}") int serverPort) {
        super(localId, localIp, localPort, serverIp, serverPort);
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
    }

}
