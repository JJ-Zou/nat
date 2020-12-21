package com.zjj.server;

import com.google.protobuf.InvalidProtocolBufferException;
import com.zjj.utils.InetUtils;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.util.CharsetUtil;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import static com.zjj.proto.CtrlMessage.*;

@Slf4j
public class UdpServer {
    private static final int PORT = 20000;
    private static final int PORT2 = 30000;
    private static final Map<String, String> ADDRESS_MAP = new ConcurrentHashMap<>();
    private static final Map<String, String> ACTUAL_ADDRESS_MAP = new ConcurrentHashMap<>();
    private static final Map<Integer, Channel> CHANNEL_MAP = new ConcurrentHashMap<>();

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

    private static class UdpChannelHandler extends SimpleChannelInboundHandler<DatagramPacket> {
        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            InetSocketAddress localAddress = (InetSocketAddress) ctx.channel().localAddress();
            if (log.isInfoEnabled()) {
                log.info("服务端绑定成功！{}", InetUtils.toAddressString(localAddress));
            }
            CHANNEL_MAP.put(localAddress.getPort(), ctx.channel());
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket msg) throws Exception {
            Channel channel = ctx.channel();
            String addressString = InetUtils.toAddressString(msg.sender());
            ByteBuf content = msg.content();
            MultiMessage multiMessage;
            try {
                multiMessage = MultiMessage.parseFrom(content.nioBuffer());
            } catch (InvalidProtocolBufferException e) {
                log.error("非protoBuf消息: {}", content.toString(CharsetUtil.UTF_8));
                return;
            }
            switch (multiMessage.getMultiType()) {
                case CTRL_INFO:
                    processCtrlInfo(multiMessage.getCtrlInfo(), addressString, channel);
                    break;
                case SERVER_ACK:
                    break;
                case PSP_MESSAGE:
                    break;
                case UNRECOGNIZED:
                    log.info("UNRECOGNIZED: {}", multiMessage);
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
            channel.writeAndFlush(packet).addListener(f -> {
                if (f.isSuccess()) {
                    if (log.isInfoEnabled()) {
                        log.info("请求 {} 给 {} 的Nat发送一条消息!", sendId, receiveId);
                    }
                } else {
                    log.error("{} 发送失败", packet);
                }
            });
        }

        private void updateAddrHandler(CtrlInfo ctrlInfo) {
            String oppositeId = ctrlInfo.getOppositeId();
            String address = ctrlInfo.getMessage();
            if (!Objects.equals(address, ACTUAL_ADDRESS_MAP.get(oppositeId))) {
                if (log.isInfoEnabled()) {
                    log.info("更新用户 {} 的实际地址 {}", oppositeId, address);
                }
                ACTUAL_ADDRESS_MAP.put(oppositeId, address);
            }
            if (log.isInfoEnabled()) {
                log.info("用户注册地址: {}", ADDRESS_MAP.get(oppositeId));
                log.info("用户P2P地址: {}", ACTUAL_ADDRESS_MAP.get(oppositeId));
            }
        }

        private void routeHandler(String addressString, String oppositeId, String localId, Channel channel) {
            if (!ADDRESS_MAP.containsKey(oppositeId)) {
                log.error("对方用户未注册");
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
            channel.writeAndFlush(packet).addListener(f -> {
                if (f.isSuccess()) {
                    if (log.isInfoEnabled()) {
                        log.info("将 {} 的地址 {} 发送给 {}@{}", oppositeId, ADDRESS_MAP.get(oppositeId), localId, addressString);
                    }
                } else {
                    log.error("向 {} 发送地址失败", localId);
                }
            });
        }

        private void registerHandler(String addressString, String localId) {
            if (Objects.equals(addressString, ADDRESS_MAP.get(localId))) {
                if (log.isInfoEnabled()) {
                    log.info("{} 的地址未变化", localId);
                }
                return;
            }
            if (log.isInfoEnabled()) {
                log.info("缓存用户 {} 的通信地址 {}", localId, addressString);
            }
            ADDRESS_MAP.put(localId, addressString);
        }
    }
}
