package com.zjj.netty.client;

import com.google.protobuf.InvalidProtocolBufferException;
import com.zjj.constant.Constants;
import com.zjj.http.HttpReq;
import com.zjj.netty.IpAddrHolder;
import com.zjj.netty.NettyClient;
import com.zjj.utils.InetUtils;
import com.zjj.utils.ProtoUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.AttributeKey;
import io.netty.util.CharsetUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.locks.LockSupport;

import static com.zjj.proto.CtrlMessage.*;

@Slf4j
@Component
@ChannelHandler.Sharable
public class UdpClientChannelHandler extends SimpleChannelInboundHandler<DatagramPacket> {
    public static final String KEY_READ_TIMESTAMP = "READ_TIMESTAMP";
    public static final String KEY_WRITE_TIMESTAMP = "WRITE_TIMESTAMP";
    private static final AttributeKey<Long> KEY_WRITE = AttributeKey.valueOf(KEY_WRITE_TIMESTAMP);
    private static final AttributeKey<Long> KEY_READ = AttributeKey.valueOf(KEY_READ_TIMESTAMP);
    @Resource(name = "udpClient")
    private NettyClient nettyClient;
    @Resource(name = "natThroughProcessor")
    private IpAddrHolder ipAddrHolder;
    @Resource
    private HttpReq httpReq;
    @Resource(name = "threadPoolTaskExecutor")
    private ThreadPoolTaskExecutor executor;

    private Thread sentThread;
    private volatile boolean receivedAdrr = false;

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        log.debug("监听本地地址 {}",
                InetUtils.toAddressString((InetSocketAddress) ctx.channel().localAddress()));
        ipAddrHolder.setPriAddrStr(nettyClient.getLocalId(), InetUtils.toAddressString((InetSocketAddress) ctx.channel().localAddress()));
        executor.execute(this::sendPrivateAddr);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket msg) throws Exception {
        Channel channel = ctx.channel();
        String oppositeAddrStr = InetUtils.toAddressString(msg.sender());
        ByteBuf content = msg.content();
        if (content.getUnsignedShortLE(0) == 0xaaaa) {
            byte[] data = new byte[content.readableBytes()];
            content.readBytes(data);
            Set<String> throughIds = ipAddrHolder.getThroughIds();
            for (String throughId : throughIds) {
                String throughIpAddrStr = ipAddrHolder.getThrough(throughId);
                ByteBuf byteBuf = Unpooled.wrappedBuffer(
                        ProtoUtils.createMultiFromBlackTrace(nettyClient.getLocalId(), throughId, data)
                                .toByteArray());
                if (!Objects.equals(throughIpAddrStr, Constants.NONE)) {
                    DatagramPacket redirectPacket = new DatagramPacket(byteBuf,
                            InetUtils.toInetSocketAddress(throughIpAddrStr));
                    channel.writeAndFlush(redirectPacket).addListener(f -> {
                        if (f.isSuccess()) {
                            log.debug("给id: {} 的地址 {} 转发黑软件航迹", throughId, throughIpAddrStr);
                        } else {
                            log.error("转发失败");
                        }
                    });
                } else {
                    DatagramPacket redirectPacket = new DatagramPacket(byteBuf,
                            nettyClient.getServerAddress());
                    channel.writeAndFlush(redirectPacket).addListener(f -> {
                        if (f.isSuccess()) {
                            log.debug("请求服务器转发id: {} 的消息: 给id: {} 转发黑软件航迹", nettyClient.getLocalId(), throughId);
                        } else {
                            log.error("请求失败");
                        }
                    });
                }
            }
            return;
        }
        MultiMessage multiMessage;
        try {
            multiMessage = MultiMessage.parseFrom(content.nioBuffer());
        } catch (InvalidProtocolBufferException e) {
            log.info("非protoBuf消息: {}", content.toString(CharsetUtil.UTF_8));
            return;
        }
        switch (multiMessage.getMultiType()) {
            case INET_COMMAND:
                InetCommand inetCommand = multiMessage.getInetCommand();
                switch (inetCommand.getInetType()) {
                    case PRIVATE:
                        log.debug("{}:{}", inetCommand.getHost(), inetCommand.getPort());
                        break;
                    case PUBLIC:
                        String id = inetCommand.getClientId();
                        String publicInetAddr = inetCommand.getHost() + ":" + inetCommand.getPort();
                        log.debug("收到id: {} 的公网地址为 {}", id, publicInetAddr);
                        if (Objects.equals(id, nettyClient.getLocalId())) {
                            ipAddrHolder.setPubAddrStr(id, publicInetAddr);
                            receivedAdrr = true;
                            LockSupport.unpark(sentThread);
                        } else {
                            log.debug("id: {} 的 心跳包, 公网地址 {} ,  Nat地址{} ", id, publicInetAddr, oppositeAddrStr);
                        }
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
                LockSupport.unpark(nettyClient.getThread(oppositeId));
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
                    LockSupport.unpark(nettyClient.getThread(oppositeId));
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
                        log.debug("请求与id: {} 的地址 {} 建立连接", from, peerAddrStr);
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
                    if (!Objects.equals(throughIpAddrStr, Constants.NONE)) {
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
            case TRACK_TRACE:
                TrackTrace trackTrace = multiMessage.getTrackTrace();
                log.info("\n{}", trackTrace);
                throughIds = ipAddrHolder.getThroughIds();
                for (String throughId : throughIds) {
                    String throughIpAddrStr = ipAddrHolder.getThrough(throughId);
                    ByteBuf byteBuf = Unpooled.wrappedBuffer(
                            ProtoUtils.createMultiFromTrackTrace(trackTrace, throughId)
                                    .toByteArray());
                    if (!Objects.equals(throughIpAddrStr, Constants.NONE)) {
                        DatagramPacket plotTraceRedirectPacket = new DatagramPacket(byteBuf, InetUtils.toInetSocketAddress(throughIpAddrStr));
                        channel.writeAndFlush(plotTraceRedirectPacket).addListener(f -> {
                            if (f.isSuccess()) {
                                log.debug("给id: {} 的地址 {} 转发航迹", throughId, throughIpAddrStr);
                            } else {
                                log.error("转发失败");
                            }
                        });
                    } else {
                        DatagramPacket plotTraceRedirectPacket = new DatagramPacket(byteBuf, nettyClient.getServerAddress());
                        channel.writeAndFlush(plotTraceRedirectPacket).addListener(f -> {
                            if (f.isSuccess()) {
                                log.debug("请求服务器转发id: {} 的消息: 给id: {} 转发航迹", nettyClient.getLocalId(), throughId);
                            } else {
                                log.error("请求失败");
                            }
                        });
                    }
                }
                break;
            case TRACK_TRACE_REDIRECT:
                TrackTraceRedirect trackTraceRedirect = multiMessage.getTrackTraceRedirect();
                log.info("\n{}", trackTraceRedirect);
                break;
            case BLACK_TRACE_REDIRECT:
                BlackTraceRedirect blackTraceRedirect = multiMessage.getBlackTraceRedirect();
                ByteBuf byteBuf = Unpooled.wrappedBuffer(blackTraceRedirect.getData().toByteArray());
                InetSocketAddress blackAddress = new InetSocketAddress(nettyClient.getLocalAddress().getHostString(), 6000);
                DatagramPacket packet = new DatagramPacket(byteBuf, blackAddress);
                channel.writeAndFlush(packet).addListener(f -> {
                    if (f.isSuccess()) {
                        log.debug("给黑软件地址 {} 转发航迹", blackAddress);
                    } else {
                        log.error("转发失败");
                    }
                });
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


    @Scheduled(initialDelay = 15000, fixedRate = 15000)
    public void sendPrivateAddr() {
        receivedAdrr = false;
        DatagramPacket packet
                = new DatagramPacket(Unpooled.wrappedBuffer(
                ProtoUtils.createMultiInetCommand(nettyClient.getLocalId(),
                        nettyClient.getLocalAddress().getHostString(),
                        nettyClient.getLocalAddress().getPort(),
                        false).toByteArray()),
                nettyClient.getServerAddress());
        nettyClient.getChannel().writeAndFlush(packet).addListener(f -> {
            if (f.isSuccess()) {
                log.debug("给 {} 发送 {} 的私网地址 {}",
                        InetUtils.toAddressString(nettyClient.getServerAddress()),
                        nettyClient.getLocalId(),
                        InetUtils.toAddressString(nettyClient.getLocalAddress()));
            } else {
                log.error("发送失败");
            }
        });
        sentThread = Thread.currentThread();
        LockSupport.parkNanos(3_000_000_000L);
        if (!receivedAdrr) {
            sendPrivateAddr();
        }
    }


    @Scheduled(initialDelay = 15000, fixedRate = 15000)
    public void sendHeartBeatToPeer() {
        if (ipAddrHolder.getPubAddrStr(nettyClient.getLocalId()) == null || ipAddrHolder.throughAddrMaps().isEmpty()) {
            return;
        }
        Map<String, String> map = ipAddrHolder.throughAddrMaps();
        InetSocketAddress socketAddress = InetUtils.toInetSocketAddress(ipAddrHolder.getPubAddrStr(nettyClient.getLocalId()));
        ByteBuf byteBuf = Unpooled.wrappedBuffer(
                ProtoUtils.createMultiInetCommand(nettyClient.getLocalId(),
                        socketAddress.getHostString(),
                        socketAddress.getPort(),
                        true).toByteArray());
        for (Map.Entry<String, String> entry : map.entrySet()) {
            if (!Objects.equals(entry.getValue(), Constants.NONE)) {
                nettyClient.getChannel().writeAndFlush(new DatagramPacket(byteBuf, InetUtils.toInetSocketAddress(entry.getValue()))).addListener(f -> {
                    if (f.isSuccess()) {
                        log.debug("给id:{} 的地址 {} 发送id: {} 的公网地址 {}",
                                entry.getKey(),
                                entry.getValue(),
                                nettyClient.getLocalId(),
                                InetUtils.toAddressString(socketAddress));
                    } else {
                        log.error("发送失败");
                    }
                });
            }
        }
    }
}

