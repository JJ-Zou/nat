package com.zjj.client;

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
                processNatHandler.sendPrivateAndGetPublicAddr();
                while (!UdpClientChannelHandler.PUBLIC_ADDR_MAP.containsKey(client.getLocalId())) {
                    if (System.currentTimeMillis() - l1 > TimeUnit.MILLISECONDS.toMillis(200)) {
                        throw new ConnectException("与服务器连接不通畅");
                    }
                    TimeUnit.MILLISECONDS.sleep(5);
                }
                log.info("{}ms", System.currentTimeMillis() - l1);
                l1 = System.currentTimeMillis();
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
}
