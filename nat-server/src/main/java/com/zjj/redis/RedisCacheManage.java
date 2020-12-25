package com.zjj.redis;

import com.zjj.constant.Constants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class RedisCacheManage {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;


    public Boolean addPrivateAddrStr(String id, String addrStr) {
        String key = Constants.PRIVATE_ADDRESS_KEY + Constants.COLON + id;
        Boolean result = stringRedisTemplate.opsForValue().setIfAbsent(key, addrStr);
        if (result != null && result) {
            stringRedisTemplate.expire(key, 24, TimeUnit.HOURS);
        }
        return result;
    }

    public Boolean addPublicAddrStr(String id, String addrStr) {
        String key = Constants.PUBLIC_ADDRESS_KEY + Constants.COLON + id;
        Boolean result = stringRedisTemplate.opsForValue().setIfAbsent(key, addrStr);
        if (result != null && result) {
            stringRedisTemplate.expire(key, 24, TimeUnit.HOURS);
        }
        return result;
    }

    public String getPrivateAddrStr(String id) {
        String key = Constants.PRIVATE_ADDRESS_KEY + Constants.COLON + id;
        return stringRedisTemplate.opsForValue().get(key);
    }

    public String getPublicAddrStr(String id) {
        String key = Constants.PUBLIC_ADDRESS_KEY + Constants.COLON + id;
        return stringRedisTemplate.opsForValue().get(key);
    }

    public Boolean deletePrivateAddr(String id) {
        String key = Constants.PRIVATE_ADDRESS_KEY + Constants.COLON + id;
        if (stringRedisTemplate.opsForValue().get(key) == null) {
            return true;
        }
        return stringRedisTemplate.delete(key);
    }

    public Boolean deletePublicAddr(String id) {
        String key = Constants.PUBLIC_ADDRESS_KEY + Constants.COLON + id;
        if (stringRedisTemplate.opsForValue().get(key) == null) {
            return true;
        }
        return stringRedisTemplate.delete(key);
    }
}
