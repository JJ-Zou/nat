package com.zjj.client;

import com.zjj.constant.Constants;
import com.zjj.http.HttpReq;
import com.zjj.utils.InetUtils;
import lombok.extern.slf4j.Slf4j;

import java.net.ConnectException;
import java.util.Objects;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

@Slf4j
public class ClientStarter {

    public void startClient() throws ConnectException, InterruptedException {
        HttpReq httpReq = new HttpReq();
        NatThroughProcessor natThroughProcessor = new NatThroughProcessor();
        UdpClient client = new UdpClient(natThroughProcessor);
        client.doBind();
        log.info("本机ID: {}", client.getLocalId());
        long l1 = System.currentTimeMillis();
        httpReq.addPrivateAddr(client.getLocalId(), InetUtils.toAddressString(client.getLocalAddress()));
        String publicAddr;
        do {
            client.sendPrivateAddr();
            TimeUnit.SECONDS.sleep(2);
        } while ((publicAddr = httpReq.getPublicAddr(client.getLocalId())) == null);
        log.debug("获取本机的公网地址用时{}ms", System.currentTimeMillis() - l1);
        natThroughProcessor.setPubAddrStr(client.getLocalId(), publicAddr);
        log.info("本机公网地址: {}", publicAddr);
        Scanner scanner = new Scanner(System.in);
        while (true) {
            String input = scanner.nextLine();
            String[] split = input.split(" +");
            if ("q!".equals(input)) {
                break;
            } else if ("nat".equals(split[0])) {
                String oppositeId = split[1].substring(1);

                l1 = System.currentTimeMillis();
                String oppositePriAddr = httpReq.getPrivateAddr(oppositeId);
                log.info("{} 的私网地址是 {}", oppositeId, oppositePriAddr);
                natThroughProcessor.setPriAddrStr(oppositeId, oppositePriAddr);
                log.debug("获取{}的私网地址用时{}ms", oppositeId, System.currentTimeMillis() - l1);
                l1 = System.currentTimeMillis();
                String oppositePubAddr = httpReq.getPublicAddr(oppositeId);
                log.info("{} 的公网地址是 {}", oppositeId, oppositePubAddr);
                natThroughProcessor.setPubAddrStr(oppositeId, oppositePubAddr);
                log.debug("获取{}的公网地址用时{}ms", oppositeId, System.currentTimeMillis() - l1);

                l1 = System.currentTimeMillis();
                log.debug("尝试与 {} 建立穿透", oppositeId);
                client.attemptNatConnect(oppositeId);
                log.debug("尝试与 {} 建立穿透用时 {}ms", oppositeId, System.currentTimeMillis() - l1);
                log.info("UDP穿透{}！",
                        Objects.equals(natThroughProcessor.getThrough(oppositeId), Constants.NONE) ? "失败" : "成功");
            } else if ("list".equals(input)) {
                log.info("{}", natThroughProcessor.throughAddrMaps());
            } else if ("chat".equals(split[0])) {
            }
        }
        httpReq.delPrivateAddr(client.getLocalId());
        httpReq.delPublicAddr(client.getLocalId());
        client.doClose();
    }


}
