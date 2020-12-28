package com.zjj;

import com.zjj.constant.Constants;
import com.zjj.http.HttpReq;
import com.zjj.netty.IpAddrHolder;
import com.zjj.netty.NettyClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import javax.annotation.Resource;
import java.util.Objects;
import java.util.Scanner;

@Slf4j
@SpringBootApplication
public class NatClientApplication implements CommandLineRunner {
    public static void main(String[] args) {
        SpringApplication.run(NatClientApplication.class, args);
    }


    @Resource
    private HttpReq httpReq;

    @Resource(name = "natThroughProcessor")
    private IpAddrHolder ipAddrHolder;

    @Resource(name = "udpClient")
    private NettyClient nettyClient;

    @Override
    public void run(String... args) throws Exception {
        nettyClient.doBind();
        log.info("本机ID: {}", nettyClient.getLocalId());
        long l1;
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
                ipAddrHolder.setPriAddrStr(oppositeId, oppositePriAddr);
                log.debug("获取{}的私网地址用时{}ms", oppositeId, System.currentTimeMillis() - l1);
                l1 = System.currentTimeMillis();
                String oppositePubAddr = httpReq.getPublicAddr(oppositeId);
                log.info("{} 的公网地址是 {}", oppositeId, oppositePubAddr);
                ipAddrHolder.setPubAddrStr(oppositeId, oppositePubAddr);
                log.debug("获取{}的公网地址用时{}ms", oppositeId, System.currentTimeMillis() - l1);

                l1 = System.currentTimeMillis();
                log.debug("尝试与 {} 建立穿透", oppositeId);
                nettyClient.attemptNatConnect(oppositeId);
                log.debug("尝试与 {} 建立穿透用时 {}ms", oppositeId, System.currentTimeMillis() - l1);
                log.info("UDP穿透{}！",
                        Objects.equals(ipAddrHolder.getThrough(oppositeId),
                                Constants.NONE) ? "失败" : "成功");
            } else if ("list".equals(input)) {
                log.info("{}", ipAddrHolder.throughAddrMaps());
            } else if ("chat".equals(split[0])) {

            }
        }


        httpReq.delPrivateAddr(nettyClient.getLocalId());
        httpReq.delPublicAddr(nettyClient.getLocalId());
        nettyClient.doClose();
    }
}
