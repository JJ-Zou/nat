package com.zjj.netty;

import com.google.protobuf.InvalidProtocolBufferException;
import com.zjj.service.AddrCacheService;
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
import java.util.concurrent.ConcurrentHashMap;

import static com.zjj.proto.CtrlMessage.*;

@Slf4j
@ChannelHandler.Sharable
@Component
public class UdpServerChannelHandler extends SimpleChannelInboundHandler<DatagramPacket> {

    private static final Map<String, String> PUBLIC_ADDR_MAP = new ConcurrentHashMap<>();
    private static final Map<String, String> PRIVATE_ADDR_MAP = new ConcurrentHashMap<>();

    @Resource
    private AddrCacheService addrCacheService;

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        InetSocketAddress localAddress = (InetSocketAddress) ctx.channel().localAddress();
        log.debug("服务端绑定成功！{}", InetUtils.toAddressString(localAddress));
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
                dealInetCommand(channel, sender, addressString, multiMessage);
                break;
            case REQ_ADDR:
                dealReqAddr(channel, sender, multiMessage);
                break;
            case REQ_REDIRECT:
                dealReqRedirect(channel, multiMessage);
                break;
            case PLOT_TRACE_REDIRECT:
                dealPlotTraceRedirect(channel, multiMessage);
                break;
            case TRACK_TRACE_REDIRECT:
                dealTrackTraceRedirect(channel, multiMessage);
                break;
            case BLACK_TRACE_REDIRECT:
                dealBlackTraceRedirect(channel, multiMessage);
                break;
            case UNRECOGNIZED:
                log.debug("UNRECOGNIZED: {}", multiMessage);
                break;
            default:
                break;
        }
    }

    private void dealBlackTraceRedirect(Channel channel, MultiMessage multiMessage) {
        BlackTraceRedirect blackTraceRedirect = multiMessage.getBlackTraceRedirect();
        String blackTraceRectTo = blackTraceRedirect.getTo();
        String blackRectToAddrStr = PUBLIC_ADDR_MAP.get(blackTraceRectTo);
        DatagramPacket blackRectToPacket = new DatagramPacket(Unpooled.wrappedBuffer(multiMessage.toByteArray()),
                InetUtils.toInetSocketAddress(blackRectToAddrStr));
        channel.writeAndFlush(blackRectToPacket).addListener(f -> {
            if (f.isSuccess()) {
                log.debug("转发黑软件航迹给id: {} 地址为 {}", blackTraceRectTo, blackRectToAddrStr);
            } else {
                log.error("转发航迹失败");
            }
        });
    }

    private void dealTrackTraceRedirect(Channel channel, MultiMessage multiMessage) {
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
    }

    private void dealPlotTraceRedirect(Channel channel, MultiMessage multiMessage) {
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
    }

    private void dealReqRedirect(Channel channel, MultiMessage multiMessage) {
        ReqRedirect reqRedirect = multiMessage.getReqRedirect();
        String to = reqRedirect.getTo();
        if (!PUBLIC_ADDR_MAP.containsKey(to)) {
            log.debug("id: {} 不存在", to);
            return;
        }
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
    }

    private void dealReqAddr(Channel channel, InetSocketAddress sender, MultiMessage multiMessage) {
        ReqAddr reqAddr = multiMessage.getReqAddr();
        String id = reqAddr.getId();
        if (!PRIVATE_ADDR_MAP.containsKey(id) || !PUBLIC_ADDR_MAP.containsKey(id)) {
            log.debug("id: {} 不存在", id);
            return;
        }
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
    }

    private void dealInetCommand(Channel channel, InetSocketAddress sender, String addressString, MultiMessage multiMessage) {
        InetCommand inetCommand = multiMessage.getInetCommand();
        switch (inetCommand.getInetType()) {
            case PRIVATE:
                String id = inetCommand.getClientId();
                String privateInetAddr = inetCommand.getHost() + ":" + inetCommand.getPort();
                log.debug("收到id: {} 的私网地址 {} 加入缓存", id, privateInetAddr);
                PRIVATE_ADDR_MAP.put(id, privateInetAddr);
                channel.eventLoop().parent().execute(() -> addrCacheService.addPrivateAddrStr(id, privateInetAddr));
                log.debug("收到id: {} 的公网地址 {} 加入缓存", id, addressString);
                PUBLIC_ADDR_MAP.put(id, addressString);
                channel.eventLoop().parent().execute(() -> addrCacheService.addPublicAddrStr(id, addressString));
                DatagramPacket packet = new DatagramPacket(Unpooled.wrappedBuffer(
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
    }
}
