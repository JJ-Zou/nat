package com.zjj.client;

import com.zjj.utils.InetUtils;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;

import static com.zjj.proto.CtrlMessage.*;

public class UdpClientRemote2 {
    private static final String SERVE_IP = "127.0.0.1";
    private static final String LOCAL_IP = "192.168.0.108";
    //    private static final String LOCAL_IP = "172.20.10.6";
    private static final int LOCAL_PORT = 10002;
    private static final int SERVER_PORT = 10000;
    private static final String ID = "test2";
    private static Channel channel;
    private static final InetSocketAddress SERVER_ADDRESS = new InetSocketAddress(SERVE_IP, SERVER_PORT);
    private static final InetSocketAddress LOCAL_ADDRESS = new InetSocketAddress(LOCAL_IP, LOCAL_PORT);
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
            register();
            Scanner scanner = new Scanner(System.in);
            while (true) {
                String input = scanner.nextLine();
                String[] split = input.split(" +");
                if ("q!".equals(input)) {
                    break;
                } else if ("nat".equals(split[0])) {
                    String oppositeId = split[1].substring(1);
                    requestForNat(oppositeId);
                } else if ("chat".equals(split[0])) {
                    sendMessage(split[1].substring(1), split[2]);
                }
            }
            channel.close().syncUninterruptibly();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            group.shutdownGracefully();
        }
    }

    private static void requestForNat(String oppositeId) {
        CtrlInfo ctrlInfo = CtrlInfo.newBuilder()
                .setType(CtrlInfo.CtrlType.REQ_ADDR)
                .setLocalId(ID)
                .setOppositeId(oppositeId)
                .build();
        MultiMessage ctrl = MultiMessage.newBuilder()
                .setMultiType(MultiMessage.MultiType.CTRL_INFO)
                .setCtrlInfo(ctrlInfo)
                .build();
        byte[] bytes = ctrl.toByteArray();
        ByteBuf byteBuf = Unpooled.copiedBuffer(bytes);
        DatagramPacket packet = new DatagramPacket(byteBuf, SERVER_ADDRESS);
        channel.writeAndFlush(packet).addListener((ChannelFutureListener) f -> {
            if (f.isSuccess()) {
                System.out.println("请求对方地址。");
            } else {
                System.err.println("请求对方地址失败。");
            }
        });
    }

    private static void register() {
        CtrlInfo ctrlInfo = CtrlInfo.newBuilder()
                .setType(CtrlInfo.CtrlType.REGISTER)
                .setLocalId(ID)
                .build();
        MultiMessage ctrl = MultiMessage.newBuilder()
                .setMultiType(MultiMessage.MultiType.CTRL_INFO)
                .setCtrlInfo(ctrlInfo)
                .build();
        byte[] bytes = ctrl.toByteArray();
        ByteBuf byteBuf = Unpooled.copiedBuffer(bytes);
        DatagramPacket packet = new DatagramPacket(byteBuf, SERVER_ADDRESS);
        channel.writeAndFlush(packet).addListener((ChannelFutureListener) f -> {
            if (f.isSuccess()) {
                System.out.println("发送注册消息。");
            } else {
                System.err.println("发送注册消息失败。");
            }
        });
    }

    private static void sendMessage(String oppositeId, String message) {
        P2PMessage pMessage = P2PMessage.newBuilder()
                .setType(P2PMessage.MsgType.CHAT)
                .setMessage(message)
                .build();
        MultiMessage multiMessage = MultiMessage.newBuilder()
                .setMultiType(MultiMessage.MultiType.PSP_MESSAGE)
                .setP2PMessage(pMessage)
                .build();
        byte[] bytes = multiMessage.toByteArray();
        ByteBuf byteBuf = Unpooled.copiedBuffer(bytes);
        DatagramPacket packet = new DatagramPacket(byteBuf, InetUtils.toInetSocketAddress(ADDRESS_MAP.get(oppositeId)));
        channel.writeAndFlush(packet).addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                System.out.println(message + " send to" + oppositeId + " success!");
            } else {
                System.err.println(message + " send fail!");
            }
        });
    }

    private static class UdpChannelHandler extends SimpleChannelInboundHandler<DatagramPacket> {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket msg) throws Exception {
            Channel channel = ctx.channel();
            String addressString = InetUtils.toAddressString(msg.sender());
            ByteBuf content = msg.content();
            MultiMessage multiMessage = MultiMessage.parseFrom(content.nioBuffer());
            switch (multiMessage.getMultiType()) {
                case CTRL_INFO:
                    break;
                case SERVER_ACK:
                    processServerAck(multiMessage.getServerAck(), addressString, channel);
                    break;
                case PSP_MESSAGE:
                    processP2pMessage(multiMessage.getP2PMessage(), addressString, channel);
                    break;
                case UNRECOGNIZED:
                    break;
                default:
                    break;
            }
        }

        private void processP2pMessage(P2PMessage p2PMessage, String addressString, Channel channel) {
            switch (p2PMessage.getType()) {
                case SAVE_ADDR:
                    processSaveAddr(p2PMessage.getMessage(), addressString);
                    break;
                case HEART_BEAT:
                    break;
                case CHAT:
                    displayChatMessage(p2PMessage);
                    break;
                case UNRECOGNIZED:
                    break;
                default:
                    break;
            }
        }

        private void displayChatMessage(P2PMessage p2PMessage) {
            System.out.println(p2PMessage.getMessage());
        }

        private void processSaveAddr(String id, String addressString) {
            System.out.println("收到" + id + "的地址" + addressString + ", 加入缓存");
            ADDRESS_MAP.put(id, addressString);
            CtrlInfo ctrlInfo = CtrlInfo.newBuilder()
                    .setType(CtrlInfo.CtrlType.UPDATE_ADDR)
                    .setLocalId(ID)
                    .setOppositeId(id)
                    .setMessage(addressString)
                    .build();
            MultiMessage ctrl = MultiMessage.newBuilder()
                    .setMultiType(MultiMessage.MultiType.CTRL_INFO)
                    .setCtrlInfo(ctrlInfo)
                    .build();
            byte[] bytes = ctrl.toByteArray();
            ByteBuf byteBuf = Unpooled.copiedBuffer(bytes);
            DatagramPacket packet = new DatagramPacket(byteBuf, SERVER_ADDRESS);
            channel.writeAndFlush(packet).addListener((ChannelFutureListener) f -> {
                if (f.isSuccess()) {
                    System.out.println("请求更新 " + id + " 的地址为 " + addressString + "。");
                } else {
                    System.err.println("请求更新地址发送失败。");
                }
            });
        }

        private void processServerAck(ServerAck serverAck, String addressString, Channel channel) {
            switch (serverAck.getType()) {
                case OK:
                    System.out.println(serverAck.getMessage());
                    break;
                case ACK_ADDR:
                    String oppositeId = processAckAddr(serverAck.getMessage());
                    notifyNat(oppositeId, channel);
                    notifyServer(oppositeId, channel);
                    break;
                case NOTIFY_SEND:
                    oppositeId = processNotifySend(serverAck.getMessage());
                    notifyNat(oppositeId, channel);
                    break;
                case UNRECOGNIZED:
                    break;
                default:
                    break;
            }
        }

        private String processNotifySend(String message) {
            return message.split("@")[1];
        }

        private void notifyServer(String id, Channel channel) {
            CtrlInfo ctrlInfo = CtrlInfo.newBuilder()
                    .setType(CtrlInfo.CtrlType.NOTIFY_ACK)
                    .setLocalId(ID)
                    .setOppositeId(id)
                    .build();
            MultiMessage ctrl = MultiMessage.newBuilder()
                    .setMultiType(MultiMessage.MultiType.CTRL_INFO)
                    .setCtrlInfo(ctrlInfo)
                    .build();
            byte[] bytes = ctrl.toByteArray();
            ByteBuf byteBuf = Unpooled.copiedBuffer(bytes);
            DatagramPacket packet = new DatagramPacket(byteBuf, SERVER_ADDRESS);
            channel.writeAndFlush(packet).addListener((ChannelFutureListener) f -> {
                if (f.isSuccess()) {
                    System.out.println("请求服务器让 " + id + " 给 " + ID + "的Nat发送一条消息。");
                } else {
                    System.err.println("请求服务器发送失败。");
                }
            });
        }

        private void notifyNat(String id, Channel channel) {
            P2PMessage saveAddr = P2PMessage.newBuilder()
                    .setType(P2PMessage.MsgType.SAVE_ADDR)
                    .setMessage(ID)
                    .build();
            MultiMessage message = MultiMessage.newBuilder()
                    .setMultiType(MultiMessage.MultiType.PSP_MESSAGE)
                    .setP2PMessage(saveAddr)
                    .build();
            byte[] bytes = message.toByteArray();
            ByteBuf byteBuf = Unpooled.copiedBuffer(bytes);
            DatagramPacket packet = new DatagramPacket(byteBuf, InetUtils.toInetSocketAddress(ADDRESS_MAP.get(id)));
            channel.writeAndFlush(packet).addListener((ChannelFutureListener) f -> {
                if (f.isSuccess()) {
                    System.out.println("给 " + id + "的Nat " + ADDRESS_MAP.get(id) + " 发送消息。");
                } else {
                    System.err.println("给 " + id + " 的Nat发送消息失败。");
                }
            });
        }


        private String processAckAddr(String addr) {
            String[] split = addr.split("@");
            System.out.println("收到" + split[0] + "的地址" + split[1] + ", 加入缓存");
            ADDRESS_MAP.put(split[0], split[1]);
            return split[0];
        }
    }
}
