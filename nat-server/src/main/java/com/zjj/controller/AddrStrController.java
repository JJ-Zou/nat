package com.zjj.controller;

import com.alibaba.fastjson.JSONObject;
import com.zjj.constant.Constants;
import com.zjj.redis.RedisCacheManage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Objects;

@Slf4j
@RestController
@RequestMapping("/addr")
public class AddrStrController {
    @Autowired
    private RedisCacheManage redisCacheManage;

    @PostMapping("/addPriAddr")
    public String addPrivateAddr(@RequestBody JSONObject jsonObject) {
        String clientId = jsonObject.getString(Constants.CLIENT_ID);
        String clientAddr = jsonObject.getString(Constants.CLIENT_PRIVATE_ADDRESS);
        redisCacheManage.addPrivateAddrStr(clientId, clientAddr);
        String insertAddr = redisCacheManage.getPrivateAddrStr(clientId);
        return Objects.equals(clientAddr, insertAddr) ? "OK" : "ERR";
    }

    @PostMapping("/addPubAddr")
    public String addPublicAddr(@RequestBody JSONObject jsonObject) {
        String clientId = jsonObject.getString(Constants.CLIENT_ID);
        String clientAddr = jsonObject.getString(Constants.CLIENT_PUBLIC_ADDRESS);
        redisCacheManage.addPublicAddrStr(clientId, clientAddr);
        String insertAddr = redisCacheManage.getPublicAddrStr(clientId);
        return Objects.equals(clientAddr, insertAddr) ? "OK" : "ERR";
    }

    @PostMapping("getPriAddr")
    public String getPrivateAddr(@RequestBody JSONObject jsonObject) {
        String clientId = jsonObject.getString(Constants.CLIENT_ID);
        return redisCacheManage.getPrivateAddrStr(clientId);
    }

    @PostMapping("getPubAddr")
    public String getPublicAddr(@RequestBody JSONObject jsonObject) {
        String clientId = jsonObject.getString(Constants.CLIENT_ID);
        return redisCacheManage.getPublicAddrStr(clientId);
    }

    @PostMapping("/delPriAddr")
    public String delPrivateAddr(@RequestBody JSONObject jsonObject) {
        String clientId = jsonObject.getString(Constants.CLIENT_ID);
        return redisCacheManage.deletePrivateAddr(clientId) > 0 ? "OK" : "ERR";
    }

    @PostMapping("/delPubAddr")
    public String delPublicAddr(@RequestBody JSONObject jsonObject) {
        String clientId = jsonObject.getString(Constants.CLIENT_ID);
        return redisCacheManage.deletePublicAddr(clientId) > 0 ? "OK" : "ERR";
    }
}
