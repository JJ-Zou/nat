package com.zjj.netty;

import com.google.protobuf.InvalidProtocolBufferException;
import com.zjj.http.HttpReq;
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
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import static com.zjj.proto.CtrlMessage.*;

@Slf4j
@ChannelHandler.Sharable
@Component
public class UdpServerChannelHandler extends SimpleChannelInboundHandler<DatagramPacket> {

    private static final Map<String, String> PUBLIC_ADDR_MAP = new ConcurrentHashMap<>();
    private static final Map<String, String> ACTUAL_ADDRESS_MAP = new ConcurrentHashMap<>();
    private static final Map<String, String> PRIVATE_ADDR_MAP = new ConcurrentHashMap<>();
    private static final Map<Integer, Channel> CHANNEL_MAP = new ConcurrentHashMap<>();

    @Resource
    private HttpReq httpReq;

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        InetSocketAddress localAddress = (InetSocketAddress) ctx.channel().localAddress();
        log.debug("服务端绑定成功！{}", InetUtils.toAddressString(localAddress));
        CHANNEL_MAP.put(localAddress.getPort(), ctx.channel());
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket msg) throws Exception {
        Channel channel = ctx.channel();
        InetSocketAddress sender = msg.sender();
        String addressString = InetUtils.toAddressString(sender);
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
                        log.debug("收到id: {} 的私网地址 {} 加入缓存", id, privateInetAddr);
                        PRIVATE_ADDR_MAP.put(id,
                                privateInetAddr);
                        channel.eventLoop().parent().execute(() -> httpReq.addPrivateAddr(id, privateInetAddr));
                        log.debug("收到id: {} 的公网地址 {} 加入缓存", id, addressString);
                        PUBLIC_ADDR_MAP.put(id,
                                addressString);
                        channel.eventLoop().parent().execute(() -> httpReq.addPublicAddr(id, addressString));
                        DatagramPacket packet
                                = new DatagramPacket(Unpooled.wrappedBuffer(
                                ProtoUtils.createMultiInetCommand(id,
                                        sender.getHostString(),
                                        sender.getPort(),
                                        true).toByteArray()),
                                sender);
                        channel.writeAndFlush(packet).addListener(f -> {
                            if (f.isSuccess()) {
                                log.debug("给id: {} 发送id: {} 的公网地址 {}", id, id, addressString);
                            } else {
                                log.error("发送失败");
                            }
                        });
                        break;
                    case UNRECOGNIZED:
                    default:
                        break;
                }
                break;
            case REQ_ADDR:
                ReqAddr reqAddr = multiMessage.getReqAddr();
                String id = reqAddr.getId();
                String privateAddrStr = PRIVATE_ADDR_MAP.get(id);
                String[] privateAddr = privateAddrStr.split(":");
                MultiMessage privateInetAck = ProtoUtils.createMultiInetCommand(id, privateAddr[0], Integer.parseInt(privateAddr[1]), false);
                DatagramPacket privateInetAckPacket =
                        new DatagramPacket(Unpooled.wrappedBuffer(privateInetAck.toByteArray()), sender);
                channel.writeAndFlush(privateInetAckPacket).addListener(f -> {
                    if (f.isSuccess()) {
                        log.debug("回复id: {} 的私网地址 {}", id, privateAddrStr);
                    } else {
                        log.error("回复id: {} 的私网地址失败", id);
                    }
                });
                String publicAddrStr = PUBLIC_ADDR_MAP.get(id);
                String[] publicAddr = publicAddrStr.split(":");
                MultiMessage publicInetAck = ProtoUtils.createMultiInetCommand(id, publicAddr[0], Integer.parseInt(publicAddr[1]), true);
                DatagramPacket publicInetAckPacket =
                        new DatagramPacket(Unpooled.wrappedBuffer(publicInetAck.toByteArray()), sender);
                channel.writeAndFlush(publicInetAckPacket).addListener(f -> {
                    if (f.isSuccess()) {
                        log.debug("回复id: {} 的公网地址 {}", id, publicAddrStr);
                    } else {
                        log.error("回复id: {} 的公网地址失败", id);
                    }
                });
                break;
            case REQ_REDIRECT:
                ReqRedirect reqRedirect = multiMessage.getReqRedirect();
                String to = reqRedirect.getTo();
                String toInetAddrStr = PUBLIC_ADDR_MAP.get(to);
                DatagramPacket reqRedirectPacket = new DatagramPacket(Unpooled.wrappedBuffer(multiMessage.toByteArray()),
                        InetUtils.toInetSocketAddress(toInetAddrStr));
                channel.writeAndFlush(reqRedirectPacket).addListener(f -> {
                    if (f.isSuccess()) {
                        log.debug("转发消息, 让id: {} 尝试和id: {} 建立连接", to, reqRedirect.getFrom());
                    } else {
                        log.error("转发消息失败");
                    }
                });
                break;
            case PLOT_TRACE_REDIRECT:
                PlotTraceRedirect plotTraceRedirect = multiMessage.getPlotTraceRedirect();
                String plotRedirectToId = plotTraceRedirect.getTo();
                String plotRedirectToIdAddrStr = PUBLIC_ADDR_MAP.get(plotRedirectToId);
                DatagramPacket plotRedirectPacket = new DatagramPacket(Unpooled.wrappedBuffer(multiMessage.toByteArray()),
                        InetUtils.toInetSocketAddress(plotRedirectToIdAddrStr));
                channel.writeAndFlush(plotRedirectPacket).addListener(f -> {
                    if (f.isSuccess()) {
                        log.debug("转发点迹给id: {} 地址为 {}", plotRedirectToId, plotRedirectToIdAddrStr);
                    } else {
                        log.error("转发点迹失败");
                    }
                });
                break;
            case TRACK_TRACE_REDIRECT:
                TrackTraceRedirect trackTraceRedirect = multiMessage.getTrackTraceRedirect();
                String trackRedirectToId = trackTraceRedirect.getTo();
                String trackRectToIdAddrStr = PUBLIC_ADDR_MAP.get(trackRedirectToId);
                DatagramPacket trackRedirectPacket = new DatagramPacket(Unpooled.wrappedBuffer(multiMessage.toByteArray()),
                        InetUtils.toInetSocketAddress(trackRectToIdAddrStr));
                channel.writeAndFlush(trackRedirectPacket).addListener(f -> {
                    if (f.isSuccess()) {
                        log.debug("转发航迹给id: {} 地址为 {}", trackRedirectToId, trackRectToIdAddrStr);
                    } else {
                        log.error("转发航迹失败");
                    }
                });
                break;
            case CTRL_INFO:
                processCtrlInfo(multiMessage.getCtrlInfo(), addressString, channel);
                break;
            case SERVER_ACK:
                break;
            case PSP_MESSAGE:
                break;
            case UNRECOGNIZED:
                log.debug("UNRECOGNIZED: {}", multiMessage);
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
                routeHandler(PUBLIC_ADDR_MAP.get(ctrlInfo.getOppositeId()), ctrlInfo.getLocalId(), ctrlInfo.getOppositeId(), channel);
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
        DatagramPacket packet = new DatagramPacket(byteBuf,
                InetUtils.toInetSocketAddress(addressString));
        channel.writeAndFlush(packet).addListener(f -> {
            if (f.isSuccess()) {
                log.debug("发送OK");
            } else {
                log.error("发送失败");
            }
        });
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
        DatagramPacket packet = new DatagramPacket(byteBuf,
                InetUtils.toInetSocketAddress(PUBLIC_ADDR_MAP.get(sendId)));
        channel.writeAndFlush(packet).addListener(f -> {
            if (f.isSuccess()) {
                log.debug("请求 {} 给 {} 的Nat发送一条消息!", sendId, receiveId);
            } else {
                log.error("{} 发送失败", packet);
            }
        });
    }

    private void updateAddrHandler(CtrlInfo ctrlInfo) {
        String oppositeId = ctrlInfo.getOppositeId();
        String address = ctrlInfo.getMessage();
        if (!Objects.equals(address, ACTUAL_ADDRESS_MAP.get(oppositeId))) {
            log.debug("更新id: {} 的实际地址 {}", oppositeId, address);
            ACTUAL_ADDRESS_MAP.put(oppositeId, address);
        }
        log.debug("用户注册地址: {}", PUBLIC_ADDR_MAP.get(oppositeId));
        log.debug("用户P2P地址: {}", ACTUAL_ADDRESS_MAP.get(oppositeId));
    }

    private void routeHandler(String addressString, String oppositeId, String localId, Channel channel) {
        if (!PUBLIC_ADDR_MAP.containsKey(oppositeId)) {
            log.error("对方用户未注册");
            return;
        }
        ServerAck serverAck = ServerAck.newBuilder()
                .setType(ServerAck.AckType.ACK_ADDR)
                .setMessage(oppositeId + "@" + PUBLIC_ADDR_MAP.get(oppositeId))
                .build();
        MultiMessage ack = MultiMessage.newBuilder()
                .setMultiType(MultiMessage.MultiType.SERVER_ACK)
                .setServerAck(serverAck)
                .build();
        ByteBuf byteBuf = Unpooled.wrappedBuffer(ack.toByteArray());
        DatagramPacket packet = new DatagramPacket(byteBuf,
                InetUtils.toInetSocketAddress(addressString));
        channel.writeAndFlush(packet).addListener(f -> {
            if (f.isSuccess()) {
                log.debug("将id: {} 的地址 {} 发送给 {}@{}", oppositeId, PUBLIC_ADDR_MAP.get(oppositeId), localId, addressString);
            } else {
                log.error("向id: {} 发送地址失败", localId);
            }
        });
    }

    private void registerHandler(String addressString, String localId) {
        if (Objects.equals(addressString, PUBLIC_ADDR_MAP.get(localId))) {
            log.debug("id: {} 的地址未变化", localId);
            return;
        }
        log.debug("缓存id: {} 的通信地址 {}", localId, addressString);
        PUBLIC_ADDR_MAP.put(localId, addressString);
    }
}
