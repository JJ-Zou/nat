package com.zjj.client;

import com.zjj.utils.InetUtils;
import com.zjj.utils.ProtoUtils;
import io.netty.buffer.Unpooled;
import io.netty.channel.socket.DatagramPacket;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ProcessNatHandler {
    private final NettyClient client;

    public ProcessNatHandler(NettyClient client) {
        this.client = client;
    }

    private void sendPrivateAndGetPublicAddr() {
        DatagramPacket packet
                = new DatagramPacket(Unpooled.wrappedBuffer(ProtoUtils.createMultiInetCommand(localId, LOCAL_HOST_NAME, LOCAL_PORT, false).toByteArray()),
                SERVER_ADDRESS);
        channel.writeAndFlush(packet).addListener(f -> {
            if (f.isSuccess()) {
                if (log.isInfoEnabled()) {
                    log.info("给 {} 发送 {} 的私网地址 {}",
                            InetUtils.toAddressString(SERVER_ADDRESS),
                            localId,
                            InetUtils.toAddressString(LOCAL_ADDRESS));
                }
            } else {
                log.error("发送失败");
            }
        });
    }

    private void requestForOppositeAddr() {
        DatagramPacket packet
                = new DatagramPacket(Unpooled.wrappedBuffer(ProtoUtils.createMultiReqAddr(oppositeId).toByteArray()),
                SERVER_ADDRESS);
        channel.writeAndFlush(packet).addListener(f -> {
            if (f.isSuccess()) {
                if (log.isInfoEnabled()) {
                    log.info("请求 {} 的私网地址", oppositeId);
                }
            } else {
                log.error("请求地址失败");
            }
        });
    }

    private void attemptPublicConnect() {
        sendSynToPeer(true);
        sendRedirectSynToServer(true);
    }

    private void attemptPrivateConnect() {
        sendSynToPeer(false);
        sendRedirectSynToServer(false);
    }

    private void sendRedirectSynToServer(boolean publicInet) {
        String inetAddrStr = publicInet
                ? UdpClientChannelHandler.PUBLIC_ADDR_MAP.get(localId)
                : UdpClientChannelHandler.PRIVATE_ADDR_MAP.get(localId);
        DatagramPacket packet
                = new DatagramPacket(Unpooled.wrappedBuffer(ProtoUtils.createMultiReqRedirect(localId, oppositeId, inetAddrStr).toByteArray()),
                SERVER_ADDRESS);
        channel.writeAndFlush(packet).addListener(f -> {
            if (f.isSuccess()) {
                if (log.isInfoEnabled()) {
                    log.info("请求服务器转发消息让 {} 使用地址 {} 尝试与 {} 建立连接", oppositeId, inetAddrStr, localId);
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
                = new DatagramPacket(Unpooled.wrappedBuffer(ProtoUtils.createMultiSyn(localId, oppositeId).toByteArray()),
                InetUtils.toInetSocketAddress(inetAddrStr));
        channel.writeAndFlush(packet).addListener(f -> {
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
