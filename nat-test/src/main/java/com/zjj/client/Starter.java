package com.zjj.client;

import com.zjj.http.HttpReq;
import com.zjj.utils.InetUtils;
import com.zjj.utils.ProtoUtils;
import io.netty.buffer.Unpooled;
import io.netty.channel.socket.DatagramPacket;
import lombok.extern.slf4j.Slf4j;

import java.net.ConnectException;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

@Slf4j
public class Starter {

    public void startClient() throws ConnectException, InterruptedException {
        HttpReq httpReq = new HttpReq();
        int retryTime = 0;
        NettyClient client = new UdpClientRemote();
        client.doBind();
        log.info("本机ID: {}", client.getLocalId());
        long l1 = System.currentTimeMillis();
        httpReq.addPrivateAddr(client.getLocalId(), InetUtils.toAddressString(client.getLocalAddress()));
        String publicAddr;
        do {
            sendPrivateAddr(client);
            TimeUnit.SECONDS.sleep(2);
        } while ((publicAddr = httpReq.getPublicAddr(client.getLocalId())) == null);
        log.debug("获取本机的公网地址用时{}ms", System.currentTimeMillis() - l1);
        UdpClientChannelHandler.PUBLIC_ADDR_MAP.put(client.getLocalId(), publicAddr);
        log.info("本机公网地址: {}", publicAddr);
        Scanner scanner = new Scanner(System.in);
        while (true) {
            String input = scanner.nextLine();
            String[] split = input.split(" +");
            if ("q!".equals(input)) {
                break;
            } else if ("nat".equals(split[0])) {
                String oppositeId = split[1].substring(1);
                ProcessNatHandler processNatHandler = new ProcessNatHandler(client, oppositeId);

                l1 = System.currentTimeMillis();
                String oppositePriAddr = httpReq.getPrivateAddr(oppositeId);
                log.info("{} 的私网地址是 {}", oppositeId, oppositePriAddr);
                UdpClientChannelHandler.PRIVATE_ADDR_MAP.put(oppositeId, oppositePriAddr);
                log.debug("获取{}的私网地址用时{}ms", oppositeId, System.currentTimeMillis() - l1);
                l1 = System.currentTimeMillis();
                String oppositePubAddr = httpReq.getPublicAddr(oppositeId);
                log.info("{} 的公网地址是 {}", oppositeId, oppositePubAddr);
                UdpClientChannelHandler.PUBLIC_ADDR_MAP.put(oppositeId, oppositePubAddr);
                log.debug("获取{}的公网地址用时{}ms", oppositeId, System.currentTimeMillis() - l1);

                l1 = System.currentTimeMillis();
                processNatHandler.attemptPrivateConnect();
                while (!client.getThrough()) {
                    if (System.currentTimeMillis() - l1 > TimeUnit.MILLISECONDS.toMillis(500)) {

                        break;
                    }
                    TimeUnit.MILLISECONDS.sleep(5);
                }
                log.debug("尝试使用私网穿透用时{}ms, 穿透{}", System.currentTimeMillis() - l1, client.getThrough() ? "成功" : "失败");

                if (!client.getThrough()) {
                    l1 = System.currentTimeMillis();
                    while (!client.getThrough()) {
                        if (retryTime > 5) {
                            break;
                        }
                        retryTime++;
                        processNatHandler.attemptPublicConnect();
                        TimeUnit.SECONDS.sleep(2);
                    }
                    log.debug("尝试使用公网穿透用时{}ms, 穿透{}", System.currentTimeMillis() - l1, client.getThrough() ? "成功" : "失败");
                }
                log.info("UDP穿透{}！", client.getThrough() ? "成功" : "失败");
            } else if ("chat".equals(split[0])) {
//                processNatHandler.sendMessage(split[1].substring(1), split[2]);
            }

        }

    }

    public void sendPrivateAddr(NettyClient nettyClient) {
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
}
