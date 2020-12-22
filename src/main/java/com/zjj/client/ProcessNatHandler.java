package com.zjj.client;

import com.zjj.utils.InetUtils;
import com.zjj.utils.ProtoUtils;
import io.netty.buffer.Unpooled;
import io.netty.channel.socket.DatagramPacket;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ProcessNatHandler {
    private String oppositeId;
    private NettyClient nettyClient;

    public ProcessNatHandler(NettyClient nettyClient, String oppositeId) {
        this.nettyClient = nettyClient;
        this.oppositeId = oppositeId;
    }

    public void sendPrivateAndGetPublicAddr() {
        DatagramPacket packet
                = new DatagramPacket(Unpooled.wrappedBuffer(ProtoUtils.createMultiInetCommand(nettyClient.getLocalId(), nettyClient.getLocalAddress().getHostString(), nettyClient.getLocalAddress().getPort(), false).toByteArray()),
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

    public void requestForOppositeAddr() {
        DatagramPacket packet
                = new DatagramPacket(Unpooled.wrappedBuffer(ProtoUtils.createMultiReqAddr(oppositeId).toByteArray()),
                nettyClient.getServerAddress());
        nettyClient.getChannel().writeAndFlush(packet).addListener(f -> {
            if (f.isSuccess()) {
                if (log.isInfoEnabled()) {
                    log.info("请求 {} 的地址", oppositeId);
                }
            } else {
                log.error("请求地址失败");
            }
        });
    }

    public void attemptPublicConnect() {
        sendSynToPeer(true);
        sendRedirectSynToServer(true);
    }

    public void attemptPrivateConnect() {
        sendSynToPeer(false);
        sendRedirectSynToServer(false);
    }

    private void sendRedirectSynToServer(boolean publicInet) {
        String inetAddrStr = publicInet
                ? UdpClientChannelHandler.PUBLIC_ADDR_MAP.get(nettyClient.getLocalId())
                : UdpClientChannelHandler.PRIVATE_ADDR_MAP.get(nettyClient.getLocalId());
        DatagramPacket packet
                = new DatagramPacket(Unpooled.wrappedBuffer(ProtoUtils.createMultiReqRedirect(nettyClient.getLocalId(), oppositeId, inetAddrStr).toByteArray()),
                nettyClient.getServerAddress());
        nettyClient.getChannel().writeAndFlush(packet).addListener(f -> {
            if (f.isSuccess()) {
                if (log.isInfoEnabled()) {
                    log.info("请求服务器转发消息让 {} 使用地址 {} 尝试与 {} 建立连接", oppositeId, inetAddrStr, nettyClient.getLocalId());
                }
            } else {
                log.error("请求发送失败");
            }
        });
    }

    private void sendSynToPeer(boolean publicInet) {
        String inetAddrStr = publicInet
                ? UdpClientChannelHandler.PUBLIC_ADDR_MAP.get(oppositeId)
                : UdpClientChannelHandler.PRIVATE_ADDR_MAP.get(oppositeId);
        DatagramPacket packet
                = new DatagramPacket(Unpooled.wrappedBuffer(ProtoUtils.createMultiSyn(nettyClient.getLocalId(), oppositeId).toByteArray()),
                InetUtils.toInetSocketAddress(inetAddrStr));
        nettyClient.getChannel().writeAndFlush(packet).addListener(f -> {
            if (f.isSuccess()) {
                if (log.isInfoEnabled()) {
                    log.info("请求与 {} 的地址 {} 建立连接", oppositeId, inetAddrStr);
                }
            } else {
                log.error("请求发送失败");
            }
        });
    }

}
