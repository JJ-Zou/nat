package com.zjj;

import com.zjj.service.AddrCacheService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;
import java.util.concurrent.CountDownLatch;

@SpringBootTest
@RunWith(SpringRunner.class)
public class TransmitServerTest {
    @Autowired
    private AddrCacheService addrCacheService;

    @Test
    public void testGetPublicAddr() {
        String ipAddr = addrCacheService.getPublicAddrStr("abcdefg");
        System.out.println("查询结果是: " + ipAddr);
    }

    @Test
    public void testGetPublicAddrConcurrency() throws InterruptedException {
        int threadNum = 1000;
        CountDownLatch countDownLatch = new CountDownLatch(1);
        CountDownLatch countDownLatch1 = new CountDownLatch(threadNum);
        for (int i = 0; i < threadNum; i++) {
            Thread t = new Thread(() -> {
                try {
                    countDownLatch.await();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                String ipAddr = addrCacheService.getPublicAddrStr("abcdefg");
                System.out.println(Thread.currentThread().getName() + " 的查询结果是: " + ipAddr);
                countDownLatch1.countDown();
            });
            t.start();
        }
        countDownLatch.countDown();
        countDownLatch1.await();
        System.out.println("over！");
    }


}
