package com.zjj.old;

import com.zjj.utils.InetUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import static com.zjj.proto.CtrlMessage.*;

@ChannelHandler.Sharable
@Slf4j
public class MultiMessageHandler extends SimpleChannelInboundHandler<MultiMessage> {
    private static final Map<String, String> ADDRESS_MAP = new ConcurrentHashMap<>();
    private static final Map<String, String> ACTUAL_ADDRESS_MAP = new ConcurrentHashMap<>();
    private final String addressString;

    public MultiMessageHandler(String addressString) {
        this.addressString = addressString;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, MultiMessage multiMessage) throws Exception {
        switch (multiMessage.getMultiType()) {
            case CTRL_INFO:
                processCtrlInfo(multiMessage.getCtrlInfo(), addressString, ctx.channel());
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
        ByteBuf byteBuf = Unpooled.wrappedBuffer(message.toByteArray());
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
        ByteBuf byteBuf = Unpooled.wrappedBuffer(ack.toByteArray());
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
        ByteBuf byteBuf = Unpooled.wrappedBuffer(ack.toByteArray());
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

