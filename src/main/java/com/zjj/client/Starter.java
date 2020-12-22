package com.zjj.client;

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
    private Starter() {
    }

    public static void startClient() throws ConnectException, InterruptedException {
        NettyClient client = new UdpClientRemote();
        client.doBind();
        sendPrivateAndGetPublicAddr(client);
        log.info("本机ID: {}", client.getLocalId());
        Scanner scanner = new Scanner(System.in);
        while (true) {
            String input = scanner.nextLine();
            String[] split = input.split(" +");
            if ("q!".equals(input)) {
                break;
            } else if ("nat".equals(split[0])) {
                String oppositeId = split[1].substring(1);
                ProcessNatHandler processNatHandler = new ProcessNatHandler(client, oppositeId);
                long l1 = System.currentTimeMillis();
                processNatHandler.requestForOppositeAddr();
                while (!UdpClientChannelHandler.PRIVATE_ADDR_MAP.containsKey(oppositeId)) {
                    if (System.currentTimeMillis() - l1 > TimeUnit.MILLISECONDS.toMillis(200)) {
                        throw new ConnectException("与服务器连接不通畅");
                    }
                    TimeUnit.MILLISECONDS.sleep(5);
                }
                log.info("{}ms", System.currentTimeMillis() - l1);
                processNatHandler.attemptPrivateConnect();
                l1 = System.currentTimeMillis();
                while (!client.getThrough()) {
                    if (System.currentTimeMillis() - l1 > TimeUnit.MILLISECONDS.toMillis(200)) {

                        break;
                    }
                    TimeUnit.MILLISECONDS.sleep(5);
                }
                log.info("{}ms", System.currentTimeMillis() - l1);
                if (UdpClientChannelHandler.PUBLIC_ADDR_MAP.containsKey(client.getLocalId())) {
                    if (!client.getThrough()) {
                        processNatHandler.attemptPublicConnect();
                    }
                }
            } else if ("chat".equals(split[0])) {
//                processNatHandler.sendMessage(split[1].substring(1), split[2]);
            }
        }
    }

    public static void sendPrivateAndGetPublicAddr(NettyClient nettyClient) {
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
