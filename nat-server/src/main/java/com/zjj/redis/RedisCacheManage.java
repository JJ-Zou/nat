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
        stringRedisTemplate.boundHashOps(Constants.PRIVATE_ADDRESS_KEY).put(id, addrStr);
    }

    public void addPublicAddrStr(String id, String addrStr) {
        stringRedisTemplate.boundHashOps(Constants.PUBLIC_ADDRESS_KEY).put(id, addrStr);
    }

    public String getPrivateAddrStr(String id) {
        return (String) stringRedisTemplate.boundHashOps(Constants.PRIVATE_ADDRESS_KEY).get(id);
    }

    public String getPublicAddrStr(String id) {
        return (String) stringRedisTemplate.boundHashOps(Constants.PUBLIC_ADDRESS_KEY).get(id);
    }

    public Long deletePrivateAddr(String id) {
        return stringRedisTemplate.boundHashOps(Constants.PRIVATE_ADDRESS_KEY).delete(id);
    }

    public Long deletePublicAddr(String id) {
        return stringRedisTemplate.boundHashOps(Constants.PUBLIC_ADDRESS_KEY).delete(id);
    }
}
