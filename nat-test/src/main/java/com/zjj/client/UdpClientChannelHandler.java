package com.zjj.client;

import com.google.protobuf.InvalidProtocolBufferException;
import com.zjj.constant.Constants;
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
import java.util.Objects;
import java.util.Set;

import static com.zjj.proto.CtrlMessage.*;

@Slf4j
@ChannelHandler.Sharable
public class UdpClientChannelHandler extends SimpleChannelInboundHandler<DatagramPacket> {
    private final NettyClient nettyClient;
    private final IpAddrHolder ipAddrHolder;

    public UdpClientChannelHandler(NettyClient nettyClient, IpAddrHolder ipAddrHolder) {
        this.nettyClient = nettyClient;
        this.ipAddrHolder = ipAddrHolder;
    }


    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        log.debug("监听本地地址 {}",
                InetUtils.toAddressString((InetSocketAddress) ctx.channel().localAddress()));
        ipAddrHolder.setPriAddrStr(nettyClient.getLocalId(), InetUtils.toAddressString((InetSocketAddress) ctx.channel().localAddress()));
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket msg) throws Exception {
        Channel channel = ctx.channel();
        String oppositeAddrStr = InetUtils.toAddressString(msg.sender());
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
                        log.debug("收到来自id: {} 地址为 {} 的私网地址 {} 加入缓存", id, oppositeAddrStr, privateInetAddr);
                        ipAddrHolder.setPriAddrStr(id, privateInetAddr);
                        break;
                    case PUBLIC:
                        String id1 = inetCommand.getClientId();
                        String publicInetAddr = inetCommand.getHost() + ":" + inetCommand.getPort();
                        log.debug("收到来自id: {} 地址为 {} 的公网地址 {} 加入缓存", id1, oppositeAddrStr, publicInetAddr);
                        ipAddrHolder.setPubAddrStr(id1, publicInetAddr);
                        break;
                    case UNRECOGNIZED:
                    default:
                        break;
                }
                break;
            case SYN:
                Syn syn = multiMessage.getSyn();
                log.debug("收到来自id: {} 地址为 {} 对 {} 的连接请求", syn.getFrom(), oppositeAddrStr, syn.getTo());
                DatagramPacket synAckPacket
                        = new DatagramPacket(Unpooled.wrappedBuffer(ProtoUtils.createMultiSynAck(syn.getTo(), syn.getFrom()).toByteArray()),
                        InetUtils.toInetSocketAddress(oppositeAddrStr));
                channel.writeAndFlush(synAckPacket).addListener(f -> {
                    if (f.isSuccess()) {
                        log.debug("给id: {} 地址为 {} 返回 SYN_ACK", syn.getFrom(), oppositeAddrStr);
                    } else {
                        log.error("SYN_ACK 发送失败");
                    }
                });
                break;
            case SYN_ACK:
                SynAck synAck = multiMessage.getSynAck();
                String oppositeId = synAck.getFrom();
                log.debug("收到来自id: {} 地址为 {} 对id: {} 的连接请求回复", oppositeId, oppositeAddrStr, synAck.getTo());
                log.debug("{} 到 {} 穿透成功!", synAck.getTo(), oppositeId);
                log.debug("穿透成功的对方id: {} 的穿透地址为 {}", oppositeId, oppositeAddrStr);
                ipAddrHolder.setThrough(oppositeId, oppositeAddrStr);
                DatagramPacket ackPacket
                        = new DatagramPacket(Unpooled.wrappedBuffer(ProtoUtils.createMultiAck(synAck.getTo(), oppositeId).toByteArray()),
                        InetUtils.toInetSocketAddress(oppositeAddrStr));
                channel.writeAndFlush(ackPacket).addListener(f -> {
                    if (f.isSuccess()) {
                        log.debug("给id: {} 地址为 {} 返回 ACK", oppositeId, oppositeAddrStr);
                    } else {
                        log.error("ACK 发送失败");
                    }
                });
                break;
            case ACK:
                Ack ack = multiMessage.getAck();
                oppositeId = ack.getFrom();
                log.debug("收到id: {} 地址为 {} 的回复", oppositeId, oppositeAddrStr);
                if (!ipAddrHolder.contains(oppositeId) || Objects.equals(ipAddrHolder.getThrough(oppositeId), Constants.NONE)) {
                    log.debug("{} 到 {} 穿透成功!", oppositeId, ack.getTo());
                    log.debug("更新对方id: {} 的穿透地址, {} -> {}", oppositeId, ipAddrHolder.getThrough(oppositeId), oppositeAddrStr);
                    ipAddrHolder.setThrough(oppositeId, oppositeAddrStr);
                } else {
                    log.debug("在收到SYN_ACK时, {} 到 {} 穿透已成功!", oppositeId, ack.getTo());
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
                        log.debug("请求与id: {} 的地址 {} 建立连接", to,  peerAddrStr);
                    } else {
                        log.error("请求发送失败");
                    }
                });
                break;
            case PLOT_TRACE:
                PlotTrace plotTrace = multiMessage.getPlotTrace();
                log.info("\n{}", plotTrace);
                Set<String> throughIds = ipAddrHolder.getThroughIds();
                for (String throughId : throughIds) {
                    String throughIpAddrStr = ipAddrHolder.getThrough(throughId);
                    ByteBuf byteBuf = Unpooled.wrappedBuffer(
                            ProtoUtils.createMultiFromPlotTrace(plotTrace, throughId)
                                    .toByteArray());
                    if (!Objects.equals(throughId, Constants.NONE)) {
                        DatagramPacket plotTraceRedirectPacket = new DatagramPacket(byteBuf, InetUtils.toInetSocketAddress(throughIpAddrStr));
                        channel.writeAndFlush(plotTraceRedirectPacket).addListener(f -> {
                            if (f.isSuccess()) {
                                log.debug("给id: {} 的地址 {} 转发点迹", throughId, throughIpAddrStr);
                            } else {
                                log.error("转发失败");
                            }
                        });
                    } else {
                        DatagramPacket plotTraceRedirectPacket = new DatagramPacket(byteBuf, nettyClient.getServerAddress());
                        channel.writeAndFlush(plotTraceRedirectPacket).addListener(f -> {
                            if (f.isSuccess()) {
                                log.debug("请求服务器转发id: {} 的消息: 给id: {} 转发点迹", nettyClient.getLocalId(), throughId);
                            } else {
                                log.error("请求失败");
                            }
                        });
                    }
                }
                break;
            case PLOT_TRACE_REDIRECT:
                PlotTraceRedirect plotTraceRedirect = multiMessage.getPlotTraceRedirect();
                log.info("\n{}", plotTraceRedirect);
                break;
            case PSP_MESSAGE:
                processP2pMessage(multiMessage.getP2PMessage(), oppositeAddrStr, channel);
                break;
            case UNRECOGNIZED:
                break;
            default:
                break;
        }
    }

    private void processP2pMessage(P2PMessage p2PMessage, String addressString, Channel channel) {
        switch (p2PMessage.getType()) {
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


    public void sendPrivateAddr() {
        DatagramPacket packet
                = new DatagramPacket(Unpooled.wrappedBuffer(
                ProtoUtils.createMultiInetCommand(nettyClient.getLocalId(),
                        nettyClient.getLocalAddress().getHostString(),
                        nettyClient.getLocalAddress().getPort(),
                        false).toByteArray()),
                nettyClient.getServerAddress());
        nettyClient.getChannel().writeAndFlush(packet).addListener(f -> {
            if (f.isSuccess()) {
                if (log.isInfoEnabled()) {
                    log.info("给 {} 发送 {} 的私网地址 {}",
                            InetUtils.toAddressString(nettyClient.getServerAddress()),
                            nettyClient.getLocalId(),
                            InetUtils.toAddressString(nettyClient.getLocalAddress()));
                }
            } else {
                log.error("发送失败");
            }
        });
    }

}

