package com.zjj.service;

import com.zjj.constant.Constants;
import com.zjj.service.impl.AddrCacheService;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;


@Component
@Slf4j
@DubboService
public class AddrCacheServiceImpl implements AddrCacheService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;


    public void addPrivateAddrStr(String id, String addrStr) {
        String key = Constants.PRIVATE_ADDRESS_KEY + Constants.COLON + id;
        stringRedisTemplate.opsForValue().set(key, addrStr, 80L, TimeUnit.SECONDS);
        log.debug("插入{{}, {}} 成功", key, addrStr);
    }

    public void addPublicAddrStr(String id, String addrStr) {
        String key = Constants.PUBLIC_ADDRESS_KEY + Constants.COLON + id;
        stringRedisTemplate.opsForValue().set(key, addrStr, 80L, TimeUnit.SECONDS);
        log.debug("插入{{}, {}} 成功", key, addrStr);
    }

    public String getPrivateAddrStr(String id) {
        String key = Constants.PRIVATE_ADDRESS_KEY + Constants.COLON + id;
        String value = stringRedisTemplate.opsForValue().get(key);
        log.debug("读取key: {} 的value为: {}", key, value);
        return value;
    }

    public String getPublicAddrStr(String id) {
        String key = Constants.PUBLIC_ADDRESS_KEY + Constants.COLON + id;
        String value = stringRedisTemplate.opsForValue().get(key);
        log.debug("读取key: {} 的value为: {}", key, value);
        return value;
    }

    public Boolean deletePrivateAddr(String id) {
        String key = Constants.PRIVATE_ADDRESS_KEY + Constants.COLON + id;
        if (stringRedisTemplate.opsForValue().get(key) == null) {
            log.debug("key {} 不存在", key);
            return true;
        }
        log.debug("删除 key {}", key);
        return stringRedisTemplate.delete(key);
    }

    public Boolean deletePublicAddr(String id) {
        String key = Constants.PUBLIC_ADDRESS_KEY + Constants.COLON + id;
        if (stringRedisTemplate.opsForValue().get(key) == null) {
            log.debug("key {} 不存在", key);
            return true;
        }
        log.debug("删除 key {}", key);
        return stringRedisTemplate.delete(key);
    }
}
