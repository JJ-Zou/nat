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
import java.util.concurrent.atomic.AtomicBoolean;

import static com.zjj.proto.CtrlMessage.*;

@Slf4j
@ChannelHandler.Sharable
public class UdpClientChannelHandler extends SimpleChannelInboundHandler<DatagramPacket> {
    private final String localId;
    private InetSocketAddress serverAddress;

    public UdpClientChannelHandler(String localId, InetSocketAddress serverAddress) {
        this.localId = localId;
        this.serverAddress = serverAddress;
        through = new AtomicBoolean(false);
    }

    static final Map<String, String> PUBLIC_ADDR_MAP = new ConcurrentHashMap<>();
    static final Map<String, String> PRIVATE_ADDR_MAP = new ConcurrentHashMap<>();
    private volatile AtomicBoolean through;

    public boolean getThrough() {
        return through.get();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        if (log.isInfoEnabled()) {
            log.info("监听本地地址 {}",
                    InetUtils.toAddressString((InetSocketAddress) ctx.channel().localAddress()));
        }
        PRIVATE_ADDR_MAP.put(localId, InetUtils.toAddressString(serverAddress));
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
                        if (log.isInfoEnabled()) {
                            log.info("收到 {} 的私网地址 {} 加入缓存", id, privateInetAddr);
                        }
                        PRIVATE_ADDR_MAP.put(id, privateInetAddr);
                        break;
                    case PUBLIC:
                        String id1 = inetCommand.getClientId();
                        String publicInetAddr = inetCommand.getHost() + ":" + inetCommand.getPort();
                        if (log.isInfoEnabled()) {
                            log.info("收到 {} 的公网地址 {} 加入缓存", id1, publicInetAddr);
                        }
                        PUBLIC_ADDR_MAP.put(id1, publicInetAddr);
                        break;
                    case UNRECOGNIZED:
                    default:
                        break;
                }
                break;
            case SYN:
                Syn syn = multiMessage.getSyn();
                if (log.isInfoEnabled()) {
                    log.info("收到 {} 的连接请求!", syn.getFrom());
                }
                DatagramPacket synAckPacket
                        = new DatagramPacket(Unpooled.wrappedBuffer(ProtoUtils.createMultiSynAck(syn.getTo(), syn.getFrom()).toByteArray()),
                        InetUtils.toInetSocketAddress(addressString));
                channel.writeAndFlush(synAckPacket).addListener(f -> {
                    if (f.isSuccess()) {
                        if (log.isInfoEnabled()) {
                            log.info("给 {} 返回 SYN_ACK", syn.getFrom());
                        }
                    } else {
                        log.error("SYN_ACK 发送失败");
                    }
                });
                break;
            case SYN_ACK:
                SynAck synAck = multiMessage.getSynAck();
                if (log.isInfoEnabled()) {
                    log.info("{} 到 {} 的连接请求被回复", synAck.getTo(), synAck.getFrom());
                }
                DatagramPacket ackPacket
                        = new DatagramPacket(Unpooled.wrappedBuffer(ProtoUtils.createMultiAck(synAck.getTo(), synAck.getFrom()).toByteArray()),
                        InetUtils.toInetSocketAddress(addressString));
                channel.writeAndFlush(ackPacket).addListener(f -> {
                    if (f.isSuccess()) {
                        if (log.isInfoEnabled()) {
                            log.info("给 {} 返回 ACK", synAck.getFrom());
                        }
                    } else {
                        log.error("ACK 发送失败");
                    }
                });
                break;
            case ACK:
                Ack ack = multiMessage.getAck();
                if (log.isInfoEnabled()) {
                    log.info("收到 {} 的回复, {} 与 {} 穿透成功!", ack.getFrom(), ack.getFrom(), ack.getTo());
                }
                through.compareAndSet(false, true);
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
                        if (log.isInfoEnabled()) {
                            log.info("请求与 {} 的地址 {} 建立连接", from, peerAddrStr);
                        }
                    } else {
                        log.error("请求发送失败");
                    }
                });
                break;
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
        if (Objects.equals(addressString, PUBLIC_ADDR_MAP.get(id))) {
            log.info("{} 的地址未变化", id);
            return;
        }
        if (log.isInfoEnabled()) {
            log.info("缓存用户 {} 的通信地址 {}", localId, addressString);
        }
        PUBLIC_ADDR_MAP.put(id, addressString);
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
                String localAddrStr = InetUtils.toAddressString((InetSocketAddress) channel.localAddress());
                if (log.isInfoEnabled()) {
                    log.info("本机 {} 经过NAT后的公网地址: {}", localAddrStr, serverAck.getMessage());
                    log.info("收到 {} 的公网地址 {} 加入缓存", localId, serverAck.getMessage());
                }
                PUBLIC_ADDR_MAP.put(localId, serverAck.getMessage());
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
//        DatagramPacket packet = new DatagramPacket(byteBuf, InetUtils.toInetSocketAddress(ADDRESS_MAP.get(id)), InetUtils.toInetSocketAddress(ADDRESS_MAP.get(localId)));
        DatagramPacket packet = new DatagramPacket(byteBuf, InetUtils.toInetSocketAddress(PUBLIC_ADDR_MAP.get(id)));
        channel.writeAndFlush(packet).addListener(f -> {
            if (f.isSuccess()) {
                if (log.isInfoEnabled()) {
//                    log.info("通过 {} 的NAT {} 给 {} 的NAT {} 发送消息", localId, ADDRESS_MAP.get(localId), id, ADDRESS_MAP.get(id));
                    log.info("通过 {} 给 {} 的NAT {} 发送消息", localId, id, PUBLIC_ADDR_MAP.get(id));
                }
            } else {
                log.info("{} 发送消息失败", localId);
            }
        });
    }


    private String processAckAddr(String addr) {
        String[] split = addr.split("@");
        if (Objects.equals(split[1], PUBLIC_ADDR_MAP.get(split[0]))) {
            if (log.isInfoEnabled()) {
                log.info("{} 的地址已缓存", split[0]);
            }
        } else {
            if (log.isInfoEnabled()) {
                log.info("收到 {} 的地址 {} 加入缓存", split[0], split[1]);
            }
            PUBLIC_ADDR_MAP.put(split[0], split[1]);
        }
        return split[0];

    }
}

