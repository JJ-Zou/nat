package com.zjj;

import com.zjj.http.HttpReq;
import com.zjj.netty.IpAddrHolder;
import com.zjj.netty.NettyClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import javax.annotation.Resource;
import java.util.Scanner;
import java.util.Set;

@Slf4j
@SpringBootApplication
public class NatClientApplication implements CommandLineRunner {
    @Resource
    private HttpReq httpReq;
    @Resource(name = "natThroughProcessor")
    private IpAddrHolder ipAddrHolder;
    @Resource(name = "udpClient")
    private NettyClient nettyClient;
    @Resource(name = "threadPoolTaskExecutor")
    private ThreadPoolTaskExecutor executor;

    public static void main(String[] args) {
        SpringApplication.run(NatClientApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        nettyClient.doBind();
        log.info("本机ID: {}", nettyClient.getLocalId());
        Set<String> oppositeIds = ipAddrHolder.getThroughIds();
        for (String oppositeId : oppositeIds) {
            executor.execute(() -> natTo(oppositeId));
        }
        Scanner scanner = new Scanner(System.in);
        while (true) {
            String input = scanner.nextLine();
            String[] split = input.split(" +");
            if ("q!".equals(input)) {
                break;
            } else if ("nat".equals(split[0])) {
                String oppositeId = split[1].substring(1);
                executor.execute(() -> natTo(oppositeId));
            } else if ("list".equals(input)) {
                log.info("{}", ipAddrHolder.throughAddrMaps());
            } else if ("chat".equals(split[0])) {

            }
        }


        httpReq.delPrivateAddr(nettyClient.getLocalId());
        httpReq.delPublicAddr(nettyClient.getLocalId());
        nettyClient.doClose();
    }

    private void natTo(String oppositeId) {
        long timeMillis = System.currentTimeMillis();
        String oppositePriAddr = httpReq.getPrivateAddr(oppositeId);
        if (oppositePriAddr == null) {
            log.info("{} 未在线", oppositeId);
            return;
        }
        log.info("{} 的私网地址是 {}", oppositeId, oppositePriAddr);
        ipAddrHolder.setPriAddrStr(oppositeId, oppositePriAddr);
        log.debug("获取{}的私网地址用时{}ms", oppositeId, System.currentTimeMillis() - timeMillis);
        timeMillis = System.currentTimeMillis();
        String oppositePubAddr = httpReq.getPublicAddr(oppositeId);
        log.info("{} 的公网地址是 {}", oppositeId, oppositePubAddr);
        ipAddrHolder.setPubAddrStr(oppositeId, oppositePubAddr);
        log.debug("获取{}的公网地址用时{}ms", oppositeId, System.currentTimeMillis() - timeMillis);

        timeMillis = System.currentTimeMillis();
        log.debug("尝试与 {} 建立穿透", oppositeId);
        nettyClient.attemptNatConnect(oppositeId);
        log.debug("尝试与 {} 建立穿透用时 {}ms", oppositeId, System.currentTimeMillis() - timeMillis);
    }
}
