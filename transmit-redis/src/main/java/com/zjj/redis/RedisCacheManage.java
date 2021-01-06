package com.zjj.redis;

import com.zjj.constant.Constants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class RedisCacheManage {
    @Resource
    private StringRedisTemplate stringRedisTemplate;


    public Boolean addPrivateAddrStr(String id, String addrStr) {
        String key = Constants.PRIVATE_ADDRESS_KEY + Constants.COLON + id;
        Boolean result = stringRedisTemplate.opsForValue().setIfAbsent(key, addrStr);
        if (result != null && result) {
            log.debug("插入{{}, {}} 成功", key, addrStr);
            stringRedisTemplate.expire(key, 1, TimeUnit.HOURS);
        } else {
            log.debug("插入{{}, {}} 失败", key, addrStr);
        }
        return result;
    }

    public Boolean addPublicAddrStr(String id, String addrStr) {
        String key = Constants.PUBLIC_ADDRESS_KEY + Constants.COLON + id;
        Boolean result = stringRedisTemplate.opsForValue().setIfAbsent(key, addrStr);
        if (result != null && result) {
            log.debug("插入{{}, {}} 成功", key, addrStr);
            stringRedisTemplate.expire(key, 1, TimeUnit.HOURS);
        } else {
            log.debug("插入{{}, {}} 失败", key, addrStr);
        }
        return result;
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
