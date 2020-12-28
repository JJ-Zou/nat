package com.zjj.http;

import com.alibaba.fastjson.JSONObject;
import com.zjj.constant.Constants;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;

@Component
public class HttpReq {
    @Value("${rpc.url}")
    private String url;
    @Resource
    private RestTemplate restTemplate;

    public String addPrivateAddr(String id, String addr) {
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.APPLICATION_JSON);
        JSONObject jsonObject = new JSONObject();
        jsonObject.put(Constants.CLIENT_ID, id);
        jsonObject.put(Constants.CLIENT_PRIVATE_ADDRESS, addr);
        HttpEntity<JSONObject> httpEntity = new HttpEntity<>(jsonObject, httpHeaders);
        return restTemplate.postForObject(url + "addPriAddr", httpEntity, String.class);
    }

    public String addPublicAddr(String id, String addr) {
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.APPLICATION_JSON);
        JSONObject jsonObject = new JSONObject();
        jsonObject.put(Constants.CLIENT_ID, id);
        jsonObject.put(Constants.CLIENT_PUBLIC_ADDRESS, addr);
        HttpEntity<JSONObject> httpEntity = new HttpEntity<>(jsonObject, httpHeaders);
        return restTemplate.postForObject(url + "addPubAddr", httpEntity, String.class);
    }

    public String getPrivateAddr(String id) {
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.APPLICATION_JSON);
        JSONObject jsonObject = new JSONObject();
        jsonObject.put(Constants.CLIENT_ID, id);
        HttpEntity<JSONObject> httpEntity = new HttpEntity<>(jsonObject, httpHeaders);
        return restTemplate.postForObject(url + "getPriAddr", httpEntity, String.class);
    }

    public String getPublicAddr(String id) {
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.APPLICATION_JSON);
        JSONObject jsonObject = new JSONObject();
        jsonObject.put(Constants.CLIENT_ID, id);
        HttpEntity<JSONObject> httpEntity = new HttpEntity<>(jsonObject, httpHeaders);
        return restTemplate.postForObject(url + "getPubAddr", httpEntity, String.class);
    }

    public String delPrivateAddr(String id) {
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.APPLICATION_JSON);
        JSONObject jsonObject = new JSONObject();
        jsonObject.put(Constants.CLIENT_ID, id);
        HttpEntity<JSONObject> httpEntity = new HttpEntity<>(jsonObject, httpHeaders);
        return restTemplate.postForObject(url + "delPriAddr", httpEntity, String.class);
    }

    public String delPublicAddr(String id) {
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.APPLICATION_JSON);
        JSONObject jsonObject = new JSONObject();
        jsonObject.put(Constants.CLIENT_ID, id);
        HttpEntity<JSONObject> httpEntity = new HttpEntity<>(jsonObject, httpHeaders);
        return restTemplate.postForObject(url + "delPubAddr", httpEntity, String.class);
    }
}
