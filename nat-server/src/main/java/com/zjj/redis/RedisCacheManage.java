package com.zjj.redis;

import com.zjj.constant.Constants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisCacheManage {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;


    public void addPrivateAddrStr(String id, String addrStr) {
        stringRedisTemplate.opsForHash().put(Constants.PRIVATE_ADDRESS_KEY, id, addrStr);
    }

    public void addPublicAddrStr(String id, String addrStr) {
        stringRedisTemplate.opsForHash().put(Constants.PUBLIC_ADDRESS_KEY, id, addrStr);
    }

    public String getPrivateAddrStr(String id) {
        return (String) stringRedisTemplate.opsForHash().get(Constants.PRIVATE_ADDRESS_KEY, id);
    }

    public String getPublicAddrStr(String id) {
        return (String) stringRedisTemplate.opsForHash().get(Constants.PUBLIC_ADDRESS_KEY, id);
    }

    public Long deletePrivateAddr(String id) {
        return stringRedisTemplate.opsForHash().delete(Constants.PRIVATE_ADDRESS_KEY, id);
    }

    public Long deletePublicAddr(String id) {
        return stringRedisTemplate.opsForHash().delete(Constants.PUBLIC_ADDRESS_KEY, id);
    }
}
