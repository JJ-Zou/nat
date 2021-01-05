package com.zjj.netty.client;

import com.zjj.config.NatProperties;
import com.zjj.netty.AbstractIpAddrHolder;
import com.zjj.netty.NettyClient;
import com.zjj.utils.InetUtils;
import com.zjj.utils.ProtoUtils;
import io.netty.buffer.Unpooled;
import io.netty.channel.socket.DatagramPacket;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class NatThroughProcessor extends AbstractIpAddrHolder {

    private static final Map<String, String> PUBLIC_ADDR_MAP = new ConcurrentHashMap<>();
    private static final Map<String, String> PRIVATE_ADDR_MAP = new ConcurrentHashMap<>();

    @Autowired
    public NatThroughProcessor(NatProperties natProperties) {
        setAllThrough(natProperties.getOppositeIds());
    }

    @Override
    public String getPriAddrStr(String id) {
        return PRIVATE_ADDR_MAP.get(id);
    }

    @Override
    public String getPubAddrStr(String id) {
        return PUBLIC_ADDR_MAP.get(id);
    }

    @Override
    public void setPriAddrStr(String id, String addrStr) {
        if (addrStr == null) {
            return;
        }
        PRIVATE_ADDR_MAP.put(id, addrStr);
    }

    @Override
    public void setPubAddrStr(String id, String addrStr) {
        if (addrStr == null) {
            return;
        }
        PUBLIC_ADDR_MAP.put(id, addrStr);
    }

    @Override
    public void attemptNatConnect(NettyClient nettyClient, String oppositeId) {
        if (PRIVATE_ADDR_MAP.containsKey(oppositeId)) {
            attemptPrivateConnect(nettyClient, oppositeId);
        }
        if (PUBLIC_ADDR_MAP.containsKey(oppositeId)) {
            attemptPublicConnect(nettyClient, oppositeId);
        }
    }

    public void attemptPrivateConnect(NettyClient nettyClient, String oppositeId) {
        sendSynToPeer(nettyClient, oppositeId, false);
        sendRedirectSynToServer(nettyClient, oppositeId, false);
    }

    public void attemptPublicConnect(NettyClient nettyClient, String oppositeId) {
        sendSynToPeer(nettyClient, oppositeId, true);
        sendRedirectSynToServer(nettyClient, oppositeId, true);
    }


    private void sendRedirectSynToServer(NettyClient nettyClient, String oppositeId, boolean publicInet) {
        String inetAddrStr = publicInet
                ? PUBLIC_ADDR_MAP.get(nettyClient.getLocalId())
                : PRIVATE_ADDR_MAP.get(nettyClient.getLocalId());
        DatagramPacket packet
                = new DatagramPacket(Unpooled.wrappedBuffer(ProtoUtils.createMultiReqRedirect(nettyClient.getLocalId(), oppositeId, inetAddrStr).toByteArray()),
                nettyClient.getServerAddress());
        nettyClient.getChannel().writeAndFlush(packet).addListener(f -> {
            if (f.isSuccess()) {
                log.debug("请求服务器转发消息让id: {} 使用地址 {} 尝试与id: {} 建立连接", oppositeId, inetAddrStr, nettyClient.getLocalId());
            } else {
                log.error("请求发送失败");
            }
        });
    }

    private void sendSynToPeer(NettyClient nettyClient, String oppositeId, boolean publicInet) {
        String inetAddrStr = publicInet
                ? PUBLIC_ADDR_MAP.get(oppositeId)
                : PRIVATE_ADDR_MAP.get(oppositeId);
        DatagramPacket packet
                = new DatagramPacket(Unpooled.wrappedBuffer(ProtoUtils.createMultiSyn(nettyClient.getLocalId(), oppositeId).toByteArray()),
                InetUtils.toInetSocketAddress(inetAddrStr));
        nettyClient.getChannel().writeAndFlush(packet).addListener(f -> {
            if (f.isSuccess()) {
                log.debug("请求与id: {} 的地址 {} 建立连接", oppositeId, inetAddrStr);
            } else {
                log.error("请求发送失败");
            }
        });
    }
}
