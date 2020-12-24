package com.zjj.client;

import com.google.protobuf.InvalidProtocolBufferException;
import com.zjj.utils.InetUtils;
import com.zjj.utils.ProtoUtils;
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
    private NettyClient nettyClient;

    public UdpClientChannelHandler(NettyClient nettyClient) {
        this.nettyClient = nettyClient;
    }

    static final Map<String, String> PUBLIC_ADDR_MAP = new ConcurrentHashMap<>();
    static final Map<String, String> PRIVATE_ADDR_MAP = new ConcurrentHashMap<>();


    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        log.debug("监听本地地址 {}",
                InetUtils.toAddressString((InetSocketAddress) ctx.channel().localAddress()));
        PRIVATE_ADDR_MAP.put(nettyClient.getLocalId(), InetUtils.toAddressString((InetSocketAddress) ctx.channel().localAddress()));
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
            case INET_COMMAND:
                InetCommand inetCommand = multiMessage.getInetCommand();
                switch (inetCommand.getInetType()) {
                    case PRIVATE:
                        String id = inetCommand.getClientId();
                        String privateInetAddr = inetCommand.getHost() + ":" + inetCommand.getPort();
                        log.debug("收到来自id: {} 公网为 {} 的私网地址 {} 加入缓存", id, addressString, privateInetAddr);
                        PRIVATE_ADDR_MAP.put(id, privateInetAddr);
                        break;
                    case PUBLIC:
                        String id1 = inetCommand.getClientId();
                        String publicInetAddr = inetCommand.getHost() + ":" + inetCommand.getPort();
                        log.debug("收到来自id: {} 公网为 {} 的公网地址 {} 加入缓存", id1, addressString, publicInetAddr);
                        PUBLIC_ADDR_MAP.put(id1, publicInetAddr);
                        break;
                    case UNRECOGNIZED:
                    default:
                        break;
                }
                break;
            case SYN:
                Syn syn = multiMessage.getSyn();
                log.debug("收到来自id: {} 公网为 {} 对 {} 的连接请求", syn.getFrom(), addressString, syn.getTo());
                DatagramPacket synAckPacket
                        = new DatagramPacket(Unpooled.wrappedBuffer(ProtoUtils.createMultiSynAck(syn.getTo(), syn.getFrom()).toByteArray()),
                        InetUtils.toInetSocketAddress(addressString));
                channel.writeAndFlush(synAckPacket).addListener(f -> {
                    if (f.isSuccess()) {
                        log.debug("给id: {} 公网为 {} 返回 SYN_ACK", syn.getFrom(), addressString);
                    } else {
                        log.error("SYN_ACK 发送失败");
                    }
                });
                break;
            case SYN_ACK:
                SynAck synAck = multiMessage.getSynAck();
                log.debug("收到来自id: {} 公网为 {} 对id: {} 的连接请求回复", synAck.getFrom(), addressString, synAck.getTo());
                if (nettyClient.setThrough()) {
                    log.debug("{} 到 {} 穿透成功!", synAck.getTo(), synAck.getFrom());
                    log.debug("穿透成功的对方id: {} 的穿透地址为 {}", synAck.getFrom(), addressString);
                }
                DatagramPacket ackPacket
                        = new DatagramPacket(Unpooled.wrappedBuffer(ProtoUtils.createMultiAck(synAck.getTo(), synAck.getFrom()).toByteArray()),
                        InetUtils.toInetSocketAddress(addressString));
                channel.writeAndFlush(ackPacket).addListener(f -> {
                    if (f.isSuccess()) {
                        log.debug("给id: {} 公网为 {} 返回 ACK", synAck.getFrom(), addressString);
                    } else {
                        log.error("ACK 发送失败");
                    }
                });
                break;
            case ACK:
                Ack ack = multiMessage.getAck();
                log.debug("收到id: {} 公网为 {} 的回复", ack.getFrom(), addressString);
                if (nettyClient.getThrough() || nettyClient.setThrough()) {
                    log.debug("{} 到 {} 穿透成功!", ack.getFrom(), ack.getTo());
                    log.debug("穿透成功的对方id: {} 的穿透地址为 {}", ack.getFrom(), addressString);
                }
                break;
            case REQ_REDIRECT:
                ReqRedirect reqRedirect = multiMessage.getReqRedirect();
                String from = reqRedirect.getFrom();
                String to = reqRedirect.getTo();
                String peerAddrStr = reqRedirect.getFromAddr();
                DatagramPacket privatePacket
                        = new DatagramPacket(Unpooled.wrappedBuffer(ProtoUtils.createMultiSyn(to, from).toByteArray()),
                        InetUtils.toInetSocketAddress(peerAddrStr));
                channel.writeAndFlush(privatePacket).addListener(f -> {
                    if (f.isSuccess()) {
                        log.debug("服务器转发id: {} 的请求 id: {} 与 id{} 其公网地址 {} 建立连接", from, to, from, peerAddrStr);
                    } else {
                        log.error("请求发送失败");
                    }
                });
                break;
            case CTRL_INFO:
                break;
            case SERVER_ACK:
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
                log.debug("UDP穿透成功！");
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
        log.debug(p2PMessage.getMessage());
    }

    private void processSaveAddr(String id, String addressString, Channel channel) {
        if (Objects.equals(addressString, PUBLIC_ADDR_MAP.get(id))) {
            log.debug("{} 的地址未变化", id);
            return;
        }
        log.debug("缓存用户 {} 的通信地址 {}", nettyClient.getLocalId(), addressString);
        PUBLIC_ADDR_MAP.put(id, addressString);
        CtrlInfo ctrlInfo = CtrlInfo.newBuilder()
                .setType(CtrlInfo.CtrlType.UPDATE_ADDR)
                .setLocalId(nettyClient.getLocalId())
                .setOppositeId(id)
                .setMessage(addressString)
                .build();
        MultiMessage ctrl = MultiMessage.newBuilder()
                .setMultiType(MultiMessage.MultiType.CTRL_INFO)
                .setCtrlInfo(ctrlInfo)
                .build();
        ByteBuf byteBuf = Unpooled.wrappedBuffer(ctrl.toByteArray());
        DatagramPacket packet = new DatagramPacket(byteBuf, nettyClient.getServerAddress());
        channel.writeAndFlush(packet).addListener(f -> {
            if (f.isSuccess()) {
                log.debug("请求服务器更新 {} 的地址为 {}", id, addressString);
            } else {
                log.error("请求服务器更新地址发送失败。");
            }
        });
    }

    private void processServerAck(ServerAck serverAck, Channel channel) {
        switch (serverAck.getType()) {
            case OK:
                String localAddrStr = InetUtils.toAddressString((InetSocketAddress) channel.localAddress());
                log.debug("本机 {} 经过NAT后的公网地址: {}", localAddrStr, serverAck.getMessage());
                log.debug("收到 {} 的公网地址 {} 加入缓存", nettyClient.getLocalId(), serverAck.getMessage());
                PUBLIC_ADDR_MAP.put(nettyClient.getLocalId(), serverAck.getMessage());
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
                .setLocalId(nettyClient.getLocalId())
                .setOppositeId(id)
                .build();
        MultiMessage ctrl = MultiMessage.newBuilder()
                .setMultiType(MultiMessage.MultiType.CTRL_INFO)
                .setCtrlInfo(ctrlInfo)
                .build();
        ByteBuf byteBuf = Unpooled.wrappedBuffer(ctrl.toByteArray());
        DatagramPacket packet = new DatagramPacket(byteBuf, nettyClient.getServerAddress());
        channel.writeAndFlush(packet).addListener(f -> {
            if (f.isSuccess()) {
                log.debug("请求服务器发送一条消息让 {} 给本机 {} 的NAT发送一条消息", id, nettyClient.getLocalId());
            } else {
                log.error("请求服务器发送失败。");
            }
        });
    }

    private void notifyNat(String id, Channel channel) {
        P2PMessage p2PMessage = P2PMessage.newBuilder()
                .setType(P2PMessage.MsgType.SAVE_ADDR)
                .setMessage(nettyClient.getLocalId())
                .build();
        MultiMessage saveAddr = MultiMessage.newBuilder()
                .setMultiType(MultiMessage.MultiType.PSP_MESSAGE)
                .setP2PMessage(p2PMessage)
                .build();
        ByteBuf byteBuf = Unpooled.wrappedBuffer(saveAddr.toByteArray());
//        DatagramPacket packet = new DatagramPacket(byteBuf, InetUtils.toInetSocketAddress(ADDRESS_MAP.get(id)), InetUtils.toInetSocketAddress(ADDRESS_MAP.get(localId)));
        DatagramPacket packet = new DatagramPacket(byteBuf, InetUtils.toInetSocketAddress(PUBLIC_ADDR_MAP.get(id)));
        channel.writeAndFlush(packet).addListener(f -> {
            if (f.isSuccess()) {
//                    log.debug("通过 {} 的NAT {} 给 {} 的NAT {} 发送消息", localId, ADDRESS_MAP.get(localId), id, ADDRESS_MAP.get(id));
                log.debug("通过 {} 给 {} 的NAT {} 发送消息", nettyClient.getLocalId(), id, PUBLIC_ADDR_MAP.get(id));
            } else {
                log.error("{} 发送消息失败", nettyClient.getLocalId());
            }
        });
    }


    private String processAckAddr(String addr) {
        String[] split = addr.split("@");
        if (Objects.equals(split[1], PUBLIC_ADDR_MAP.get(split[0]))) {
            log.debug("{} 的地址已缓存", split[0]);
        } else {
            log.error("收到 {} 的地址 {} 加入缓存", split[0], split[1]);
            PUBLIC_ADDR_MAP.put(split[0], split[1]);
        }
        return split[0];

    }
}

