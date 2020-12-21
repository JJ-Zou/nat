package com.zjj.client;

import com.zjj.utils.InetUtils;
import com.zjj.utils.ProtoUtils;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static com.zjj.proto.CtrlMessage.*;

@Slf4j
public class UdpClientRemote2 {
    private static final String ID;
    private static final int LOCAL_PORT;
    private static final String LOCAL_HOST_NAME;

    static {
        String className = UdpClientRemote2.class.getName();
        String reg = "[^0-9]";
        String num = Pattern.compile(reg).matcher(className).replaceAll("").trim();
        ID = "test" + num;
        LOCAL_PORT = Integer.parseInt("100" + num);
        LOCAL_HOST_NAME = InetUtils.getLocalAddress();
    }

    private static final String SERVE_IP = "39.105.65.104";
    private static final int SERVER_PORT = 20000;
    private static String oppositeId;
    private static Channel channel;
    private static final InetSocketAddress SERVER_ADDRESS = new InetSocketAddress(SERVE_IP, SERVER_PORT);
    private static final InetSocketAddress LOCAL_ADDRESS = new InetSocketAddress(LOCAL_HOST_NAME, LOCAL_PORT);

    @SneakyThrows
    public static void main(String[] args) {
        NioEventLoopGroup group = new NioEventLoopGroup();
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(group)
                .channel(NioDatagramChannel.class)
                .option(ChannelOption.SO_BROADCAST, true)
                .handler(new ChannelInitializer<DatagramChannel>() {
                    @Override
                    protected void initChannel(DatagramChannel ch) throws Exception {
                        ch.pipeline()
                                .addLast(new UdpClientChannelHandler(ID, SERVER_ADDRESS))
                        ;
                    }
                });
        try (Scanner scanner = new Scanner(System.in);) {
            ChannelFuture future = bootstrap.bind(LOCAL_ADDRESS).syncUninterruptibly();
            channel = future.channel();
            sendPrivateAddr();
            while (true) {
                String input = scanner.nextLine();
                String[] split = input.split(" +");
                if ("q!".equals(input)) {
                    break;
                } else if ("nat".equals(split[0])) {
                    oppositeId = split[1].substring(1);
                    requestForOppositeAddr();
                    attemptPrivateConnect();
//                    requestForNat();
                } else if ("chat".equals(split[0])) {
                    sendMessage(split[1].substring(1), split[2]);
                }
            }
            channel.close().syncUninterruptibly();
        } finally {
            group.shutdownGracefully();
        }
    }

    @SneakyThrows
    private static void attemptPrivateConnect() {
        while (!UdpClientChannelHandler.PRIVATE_ADDR_MAP.containsKey(oppositeId)) {
            TimeUnit.NANOSECONDS.sleep(1000);
        }
        sendReqToPeer();
        sendRedirectReqToServer();
    }

    private static void sendRedirectReqToServer() {
        DatagramPacket packet
                = new DatagramPacket(Unpooled.wrappedBuffer(ProtoUtils.createMultiReqRedirect(ID, oppositeId, InetUtils.toAddressString(LOCAL_ADDRESS)).toByteArray()),
                SERVER_ADDRESS);
        channel.writeAndFlush(packet).addListener(f -> {
            if (f.isSuccess()) {
                if (log.isInfoEnabled()) {
                    log.info("请求服务器转发消息让 {} 使用私网尝试与 {} 建立连接", oppositeId, ID);
                }
            } else {
                log.error("请求发送失败");
            }
        });
    }

    private static void sendReqToPeer() {
        String privateAddrStr = UdpClientChannelHandler.PRIVATE_ADDR_MAP.get(oppositeId);
        DatagramPacket packet
                = new DatagramPacket(Unpooled.wrappedBuffer(ProtoUtils.createMultiSyn(ID, oppositeId).toByteArray()),
                InetUtils.toInetSocketAddress(privateAddrStr));
        channel.writeAndFlush(packet).addListener(f -> {
            if (f.isSuccess()) {
                if (log.isInfoEnabled()) {
                    log.info("请求与 {} 的私网 {} 建立连接", oppositeId, privateAddrStr);
                }
            } else {
                log.error("请求发送失败");
            }
        });
    }

    private static void requestForOppositeAddr() {
        DatagramPacket packet
                = new DatagramPacket(Unpooled.wrappedBuffer(ProtoUtils.createMultiReqAddr(oppositeId).toByteArray()),
                SERVER_ADDRESS);
        channel.writeAndFlush(packet).addListener(f -> {
            if (f.isSuccess()) {
                if (log.isInfoEnabled()) {
                    log.info("请求 {} 的私网地址", oppositeId);
                }
            } else {
                log.error("请求地址失败");
            }
        });
    }

    private static void requestForNat() {
        CtrlInfo ctrlInfo = CtrlInfo.newBuilder()
                .setType(CtrlInfo.CtrlType.REQ_ADDR)
                .setLocalId(ID)
                .setOppositeId(oppositeId)
                .build();
        MultiMessage ctrl = MultiMessage.newBuilder()
                .setMultiType(MultiMessage.MultiType.CTRL_INFO)
                .setCtrlInfo(ctrlInfo)
                .build();
        ByteBuf byteBuf = Unpooled.wrappedBuffer(ctrl.toByteArray());
        DatagramPacket packet = new DatagramPacket(byteBuf, SERVER_ADDRESS);
        channel.writeAndFlush(packet).addListener(f -> {
            if (f.isSuccess()) {
                if (log.isInfoEnabled()) {
                    log.info("请求对方地址。");
                }
            } else {
                log.error("请求对方地址失败。");
            }
        });
    }

    private static void sendPrivateAddr() {
        DatagramPacket packet
                = new DatagramPacket(Unpooled.wrappedBuffer(ProtoUtils.createMultiInetCommand(ID, LOCAL_HOST_NAME, LOCAL_PORT, false).toByteArray()),
                SERVER_ADDRESS);
        channel.writeAndFlush(packet).addListener(f -> {
            if (f.isSuccess()) {
                if (log.isInfoEnabled()) {
                    log.info("给 {} 发送 {} 的私网地址 {}",
                            InetUtils.toAddressString(SERVER_ADDRESS),
                            ID,
                            InetUtils.toAddressString(LOCAL_ADDRESS));
                }
            } else {
                log.error("发送失败");
            }
        });
    }

    private static void sendMessage(String oppositeId, String message) {
        P2PMessage pMessage = P2PMessage.newBuilder()
                .setType(P2PMessage.MsgType.CHAT)
                .setMessage(message)
                .build();
        MultiMessage chat = MultiMessage.newBuilder()
                .setMultiType(MultiMessage.MultiType.PSP_MESSAGE)
                .setP2PMessage(pMessage)
                .build();
        ByteBuf byteBuf = Unpooled.wrappedBuffer(chat.toByteArray());
        DatagramPacket packet = new DatagramPacket(byteBuf,
                InetUtils.toInetSocketAddress(UdpClientChannelHandler.PUBLIC_ADDR_MAP.get(oppositeId)));
        channel.writeAndFlush(packet).addListener(future -> {
            if (future.isSuccess()) {
                if (log.isInfoEnabled()) {
                    log.info("{} @ {} \n {}", ID, oppositeId, message);
                }
            } else {
                log.error("{} 发送失败", message);
            }
        });
    }
}
