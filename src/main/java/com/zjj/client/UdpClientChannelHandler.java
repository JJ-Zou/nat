package com.zjj.client;

import com.google.protobuf.InvalidProtocolBufferException;
import com.zjj.utils.InetUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.CharsetUtil;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import static com.zjj.proto.CtrlMessage.*;

@Slf4j
@ChannelHandler.Sharable
public class UdpClientChannelHandler extends SimpleChannelInboundHandler<DatagramPacket> {
    private final String localId;
    private InetSocketAddress serverAddress;

    public UdpClientChannelHandler(String localId, InetSocketAddress serverAddress) {
        this.localId = localId;
        this.serverAddress = serverAddress;
    }

    static final Map<String, String> ADDRESS_MAP = new ConcurrentHashMap<>();

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        if (log.isInfoEnabled()) {
            log.info("监听本地地址 {}",
                    InetUtils.toAddressString((InetSocketAddress) ctx.channel().localAddress()));
        }
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
                break;
            case SERVER_ACK:
                this.serverAddress = msg.sender();
                processServerAck(multiMessage.getServerAck(), channel);
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
                if (log.isInfoEnabled()) {
                    log.info("UDP穿透成功！");
                }
                processSaveAddr(p2PMessage.getMessage(), addressString, channel);
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
        log.info(p2PMessage.getMessage());
    }

    private void processSaveAddr(String id, String addressString, Channel channel) {
        if (Objects.equals(addressString, ADDRESS_MAP.get(id))) {
            log.info("{} 的地址未变化", id);
            return;
        }
        if (log.isInfoEnabled()) {
            log.info("缓存用户 {} 的通信地址 {}", localId, addressString);
        }
        ADDRESS_MAP.put(id, addressString);
        CtrlInfo ctrlInfo = CtrlInfo.newBuilder()
                .setType(CtrlInfo.CtrlType.UPDATE_ADDR)
                .setLocalId(localId)
                .setOppositeId(id)
                .setMessage(addressString)
                .build();
        MultiMessage ctrl = MultiMessage.newBuilder()
                .setMultiType(MultiMessage.MultiType.CTRL_INFO)
                .setCtrlInfo(ctrlInfo)
                .build();
        ByteBuf byteBuf = Unpooled.wrappedBuffer(ctrl.toByteArray());
        DatagramPacket packet = new DatagramPacket(byteBuf, serverAddress);
        channel.writeAndFlush(packet).addListener(f -> {
            if (f.isSuccess()) {
                if (log.isInfoEnabled()) {
                    log.info("请求服务器更新 {} 的地址为 {}", id, addressString);
                }
            } else {
                log.error("请求服务器更新地址发送失败。");
            }
        });
    }

    private void processServerAck(ServerAck serverAck, Channel channel) {
        switch (serverAck.getType()) {
            case OK:
                if (log.isInfoEnabled()) {
                    log.info("本机 {} 经过NAT后的公网地址: {}",
                            InetUtils.toAddressString((InetSocketAddress) channel.localAddress()),
                            serverAck.getMessage());
                }
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
                .setLocalId(localId)
                .setOppositeId(id)
                .build();
        MultiMessage ctrl = MultiMessage.newBuilder()
                .setMultiType(MultiMessage.MultiType.CTRL_INFO)
                .setCtrlInfo(ctrlInfo)
                .build();
        ByteBuf byteBuf = Unpooled.wrappedBuffer(ctrl.toByteArray());
        DatagramPacket packet = new DatagramPacket(byteBuf, serverAddress);
        channel.writeAndFlush(packet).addListener(f -> {
            if (f.isSuccess()) {
                if (log.isInfoEnabled()) {
                    log.info("请求服务器发送一条消息让 {} 给本机 {} 的NAT发送一条消息", id, localId);
                }
            } else {
                log.error("请求服务器发送失败。");
            }
        });
    }

    private void notifyNat(String id, Channel channel) {
        P2PMessage p2PMessage = P2PMessage.newBuilder()
                .setType(P2PMessage.MsgType.SAVE_ADDR)
                .setMessage(localId)
                .build();
        MultiMessage saveAddr = MultiMessage.newBuilder()
                .setMultiType(MultiMessage.MultiType.PSP_MESSAGE)
                .setP2PMessage(p2PMessage)
                .build();
        ByteBuf byteBuf = Unpooled.wrappedBuffer(saveAddr.toByteArray());
        DatagramPacket packet = new DatagramPacket(byteBuf, InetUtils.toInetSocketAddress(ADDRESS_MAP.get(id)));
        channel.writeAndFlush(packet).addListener(f -> {
            if (f.isSuccess()) {
                if (log.isInfoEnabled()) {
                    log.info("给 {} 的NAT {} 发送消息", id, ADDRESS_MAP.get(id));
                }
            } else {
                log.error("给 {} 的NAT发送消息失败", id);
            }
        });
    }


    private String processAckAddr(String addr) {
        String[] split = addr.split("@");
        if (Objects.equals(split[1], ADDRESS_MAP.get(split[0]))) {
            if (log.isInfoEnabled()) {
                log.info("{} 的地址已缓存", split[0]);
            }
        } else {
            if (log.isInfoEnabled()) {
                log.info("收到 {} 的地址 {} 加入缓存", split[0], split[1]);
            }
            ADDRESS_MAP.put(split[0], split[1]);
        }
        return split[0];

    }
}
