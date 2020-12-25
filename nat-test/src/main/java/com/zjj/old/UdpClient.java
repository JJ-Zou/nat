package com.zjj.old;

import com.zjj.utils.InetUtils;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.util.CharsetUtil;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;

public class UdpClient {
    private static final String SERVE_IP = "stun.xten.com";
    private static final String LOCAL_IP = "192.168.0.108";
    private static final int LOCAL_PORT = 10001;
    private static final int SERVER_PORT = 3478;
    private static final String ID = "zz";
    private static Channel channel;
    private static final InetSocketAddress SERVER_ADDRESS = new InetSocketAddress(SERVE_IP, SERVER_PORT);
    private static final String OTHER_ID = "com/zjj";
    private static final Map<String, String> ADDRESS_MAP = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        NioEventLoopGroup group = new NioEventLoopGroup();
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(group)
                .channel(NioDatagramChannel.class)
                .option(ChannelOption.SO_BROADCAST, true)
                .handler(new ChannelInitializer<DatagramChannel>() {
                    @Override
                    protected void initChannel(DatagramChannel ch) throws Exception {
                        ch.pipeline().addLast(new UdpChannelHandler());
                    }
                });
        try {
            ChannelFuture future = bootstrap.bind(LOCAL_PORT).sync();
            channel = future.channel();
            System.out.println("客户端绑定成功！" + InetUtils.toAddressString((InetSocketAddress) channel.localAddress()));
            Scanner scanner = new Scanner(System.in);
            while (true) {
                String input = scanner.nextLine();
                if ("q!".equals(input)) {
                    break;
                }
                sendMessage(input);
            }
            channel.close().syncUninterruptibly();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            group.shutdownGracefully();
        }
    }


    private static void sendMessage(String message) {
        DatagramPacket packet = toDatagramPacket(ID + "@" + OTHER_ID + ":" + message);
        channel.writeAndFlush(packet).addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                System.out.println(packet + " send success!");
            } else {
                System.err.println(packet + " send fail!");
            }
        });
    }

    private static DatagramPacket toDatagramPacket(String message) {
        ByteBuf byteBuf = Unpooled.copiedBuffer(message.getBytes());
        if (ADDRESS_MAP.containsKey(OTHER_ID)) {
            System.out.println("已知对方地址");
            String address = ADDRESS_MAP.get(OTHER_ID);
            return new DatagramPacket(byteBuf, InetUtils.toInetSocketAddress(address));
        }
        System.out.println("未知对方地址");
        return new DatagramPacket(byteBuf, SERVER_ADDRESS);
    }

    private static class UdpChannelHandler extends SimpleChannelInboundHandler<DatagramPacket> {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket msg) throws Exception {
            String addressString = InetUtils.toAddressString(msg.sender());
            ByteBuf content = msg.content();
            String message = content.toString(CharsetUtil.UTF_8);
            System.out.println("接收到" + addressString + "消息： " + message);
            if (addressString.equals(InetUtils.toAddressString(SERVER_ADDRESS))) {
                String[] split = message.split("#");
                if (OTHER_ID.equals(split[0]) && !ADDRESS_MAP.containsKey(split[0])) {
                    System.out.println("将 " + split[0] + " 的地址放入 ADDRESS_MAP");
                    ADDRESS_MAP.put(split[0], split[1]);
                    System.out.println("ADDRESS_MAP: " + ADDRESS_MAP);
                }
            } else {
                String id = message.split(":")[0];
                String[] split = id.split("@");
                if (!addressString.equals(ADDRESS_MAP.get(split[0]))) {
                    System.out.println("将 " + split[0] + " 的地址放入 ADDRESS_MAP");
                    ADDRESS_MAP.put(split[0], addressString);
                    System.out.println("ADDRESS_MAP: " + ADDRESS_MAP);
                }
            }
        }
    }
}
