package com.zjj.controller;

import com.alibaba.fastjson.JSONObject;
import com.zjj.constant.Constants;
import com.zjj.service.RedisCacheManage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@Slf4j
@RestController
@RequestMapping("/addr")
public class AddrStrController {
    @Resource
    private RedisCacheManage redisCacheManage;

    @PostMapping("/addPriAddr")
    public String addPrivateAddr(@RequestBody JSONObject jsonObject) {
        String clientId = jsonObject.getString(Constants.CLIENT_ID);
        String clientAddr = jsonObject.getString(Constants.CLIENT_PRIVATE_ADDRESS);
        redisCacheManage.addPrivateAddrStr(clientId, clientAddr);
        return "OK";
    }

    @PostMapping("/addPubAddr")
    public String addPublicAddr(@RequestBody JSONObject jsonObject) {
        String clientId = jsonObject.getString(Constants.CLIENT_ID);
        String clientAddr = jsonObject.getString(Constants.CLIENT_PUBLIC_ADDRESS);
        redisCacheManage.addPublicAddrStr(clientId, clientAddr);
        return "OK";
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
        Boolean aBoolean = redisCacheManage.deletePrivateAddr(clientId);
        return aBoolean != null && aBoolean ? "OK" : "ERR";
    }

    @PostMapping("/delPubAddr")
    public String delPublicAddr(@RequestBody JSONObject jsonObject) {
        String clientId = jsonObject.getString(Constants.CLIENT_ID);
        Boolean aBoolean = redisCacheManage.deletePublicAddr(clientId);
        return aBoolean != null && aBoolean ? "OK" : "ERR";
    }
}
