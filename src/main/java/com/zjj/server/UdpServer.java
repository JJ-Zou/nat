package com.zjj.server;

import com.zjj.utils.InetUtils;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.util.CharsetUtil;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class UdpServer {
    private static final String SERVE_IP = "127.0.0.1";
    private static final int PORT = 10000;
    private static final Map<String, String> ADDRESS_MAP = new ConcurrentHashMap<>();
    private static final InetSocketAddress SERVER_ADDRESS = new InetSocketAddress(SERVE_IP, PORT);

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
                                .addLast(new UdpChannelHandler());
                    }
                });
        try {
            ChannelFuture future = bootstrap.bind(PORT).sync();
            Channel channel = future.channel();
            System.out.println("服务端绑定成功！" + InetUtils.toAddressString((InetSocketAddress) channel.localAddress()));
            channel.closeFuture().syncUninterruptibly();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            group.shutdownGracefully();
        }
    }

    private static class UdpChannelHandler extends SimpleChannelInboundHandler<DatagramPacket> {
        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            System.out.println(ctx.channel() + " active");
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket msg) throws Exception {
            Channel channel = ctx.channel();
            String addressString = InetUtils.toAddressString(msg.sender());
            ByteBuf content = msg.content();
            String message = content.toString(CharsetUtil.UTF_8);
            System.out.println("接收到消息： " + message);
            String id = message.split(":")[0];
            String[] split = id.split("@");
            if (!ADDRESS_MAP.containsKey(split[0])) {
                System.out.println("将 " + split[0] + " 的地址放入 ADDRESS_MAP");
                ADDRESS_MAP.put(split[0], addressString);
                System.out.println("ADDRESS_MAP: " + ADDRESS_MAP);
            }
            if (ADDRESS_MAP.containsKey(split[1])) {
                System.out.println("向 " + split[0] + " 发送 " + split[1] + " 的UDP地址");
                sendIpAddress(channel, addressString, split[1] + "#" + ADDRESS_MAP.get(split[1]));
            }
        }

        private void sendIpAddress(Channel channel, String addressString, String message) {
            ByteBuf byteBuf = Unpooled.copiedBuffer(message.getBytes());
            DatagramPacket packet = new DatagramPacket(byteBuf, InetUtils.toInetSocketAddress(addressString));
            channel.writeAndFlush(packet).addListener((ChannelFutureListener) f -> {
                if (f.isSuccess()) {
                    System.out.println(packet + " send success!");
                } else {
                    System.err.println(packet + " send fail!");
                }
            });
        }

    }
}
