package com.zjj.server;

import com.zjj.utils.InetUtils;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import static com.zjj.proto.CtrlMessage.*;

public class UdpServer {
    //    private static final String SERVE_IP = "127.0.0.1";
    private static final int PORT = 20000;
    private static final int PORT2 = 30000;
    private static final Map<String, String> ADDRESS_MAP = new ConcurrentHashMap<>();
    private static final Map<Integer, Channel> CHANNEL_MAP = new ConcurrentHashMap<>();
//    private static final InetSocketAddress SERVER_ADDRESS = new InetSocketAddress(SERVE_IP, PORT);

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
            ChannelFuture future1 = bootstrap.bind(PORT2).sync();
            Channel channel = future.channel();
            Channel channel1 = future1.channel();
            channel.closeFuture().syncUninterruptibly();
            channel1.closeFuture().syncUninterruptibly();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            group.shutdownGracefully();
        }
    }

    private static class UdpChannelHandler extends SimpleChannelInboundHandler<DatagramPacket> {
        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            InetSocketAddress localAddress = (InetSocketAddress) ctx.channel().localAddress();
            System.out.println("服务端绑定成功！" + InetUtils.toAddressString(localAddress));
            CHANNEL_MAP.put(localAddress.getPort(), ctx.channel());
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket msg) throws Exception {
            Channel channel = ctx.channel();
            String addressString = InetUtils.toAddressString(msg.sender());
            ByteBuf content = msg.content();
            MultiMessage multiMessage = MultiMessage.parseFrom(content.nioBuffer());
            switch (multiMessage.getMultiType()) {
                case CTRL_INFO:
                    processCtrlInfo(multiMessage.getCtrlInfo(), addressString, channel);
                    break;
                case SERVER_ACK:
                    break;
                case PSP_MESSAGE:
                    break;
                case UNRECOGNIZED:
                    break;
                default:
                    break;
            }
        }

        private void processCtrlInfo(CtrlInfo ctrlInfo, String addressString, Channel channel) {
            switch (ctrlInfo.getType()) {
                case REGISTER:
                    registerHandler(addressString, ctrlInfo.getLocalId());
                    serverAckPublicAddr(addressString, channel);
//                    routeHandler(addressString, ctrlInfo.getLocalId(), ctrlInfo.getLocalId(), channel);
                    break;
                case REQ_ADDR:
                    routeHandler(addressString, ctrlInfo.getOppositeId(), ctrlInfo.getLocalId(), channel);
                    routeHandler(ADDRESS_MAP.get(ctrlInfo.getOppositeId()), ctrlInfo.getLocalId(), ctrlInfo.getOppositeId(), channel);
                    break;
                case UPDATE_ADDR:
                    updateAddrHandler(ctrlInfo);
                    break;
                case NOTIFY_ACK:
                    notifyClientSendMsg(ctrlInfo, channel);
                    break;
                case UNRECOGNIZED:
                    break;
                default:
                    break;
            }
        }

        private void serverAckPublicAddr(String addressString, Channel channel) {
            ServerAck build = ServerAck.newBuilder()
                    .setType(ServerAck.AckType.OK)
                    .setMessage(addressString)
                    .build();
            MultiMessage message = MultiMessage.newBuilder()
                    .setMultiType(MultiMessage.MultiType.SERVER_ACK)
                    .setServerAck(build)
                    .build();
            byte[] bytes = message.toByteArray();
            ByteBuf byteBuf = Unpooled.copiedBuffer(bytes);
            DatagramPacket packet = new DatagramPacket(byteBuf, InetUtils.toInetSocketAddress(addressString));
            channel.writeAndFlush(packet);
        }

        private void notifyClientSendMsg(CtrlInfo ctrlInfo, Channel channel) {
            String receiveId = ctrlInfo.getLocalId();
            String sendId = ctrlInfo.getOppositeId();
            ServerAck serverAck = ServerAck.newBuilder()
                    .setType(ServerAck.AckType.NOTIFY_SEND)
                    .setMessage(sendId + "@" + receiveId)
                    .build();
            MultiMessage ack = MultiMessage.newBuilder()
                    .setMultiType(MultiMessage.MultiType.SERVER_ACK)
                    .setServerAck(serverAck)
                    .build();
            byte[] bytes = ack.toByteArray();
            ByteBuf byteBuf = Unpooled.copiedBuffer(bytes);
            DatagramPacket packet = new DatagramPacket(byteBuf, InetUtils.toInetSocketAddress(ADDRESS_MAP.get(sendId)));
            channel.writeAndFlush(packet).addListener((ChannelFutureListener) f -> {
                if (f.isSuccess()) {
                    System.out.println("请求 " + sendId + " 给 " + receiveId + " 的Nat发送一条消息!");
                } else {
                    System.out.println(packet + " 发送失败");
                }
            });
        }

        private void updateAddrHandler(CtrlInfo ctrlInfo) {
            String oppositeId = ctrlInfo.getOppositeId();
            String address = ctrlInfo.getMessage();
            if (!Objects.equals(address, ADDRESS_MAP.get(oppositeId))) {
                System.out.println("更新用户 " + oppositeId + " 的通信地址 " + address);
                ADDRESS_MAP.put(oppositeId, address);
            }
        }

        private void routeHandler(String addressString, String oppositeId, String localId, Channel channel) {
            if (!ADDRESS_MAP.containsKey(oppositeId)) {
                System.out.println("对方用户未注册");
                return;
            }
            ServerAck serverAck = ServerAck.newBuilder()
                    .setType(ServerAck.AckType.ACK_ADDR)
                    .setMessage(oppositeId + "@" + ADDRESS_MAP.get(oppositeId))
                    .build();
            MultiMessage ack = MultiMessage.newBuilder()
                    .setMultiType(MultiMessage.MultiType.SERVER_ACK)
                    .setServerAck(serverAck)
                    .build();
            byte[] bytes = ack.toByteArray();
            ByteBuf byteBuf = Unpooled.copiedBuffer(bytes);
            DatagramPacket packet = new DatagramPacket(byteBuf, InetUtils.toInetSocketAddress(addressString));
            channel.writeAndFlush(packet).addListener((ChannelFutureListener) f -> {
                if (f.isSuccess()) {
                    System.out.println("将 " + oppositeId + " 的地址 " + ADDRESS_MAP.get(oppositeId) + " 发送给 " + localId + "@" + addressString);
                } else {
                    System.err.println("向 " + localId + " 发送地址失败");
                }
            });
        }

        private void registerHandler(String addressString, String localId) {
            if (Objects.equals(addressString, ADDRESS_MAP.get(localId))) {
                System.out.println(localId + " 的地址为变化");
                return;
            }
            System.out.println("缓存用户 " + localId + " 的通信地址" + addressString);
            ADDRESS_MAP.put(localId, addressString);
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
