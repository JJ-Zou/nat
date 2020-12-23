package com.zjj.http;

import cn.hutool.json.JSONObject;
import com.zjj.constant.Constants;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

public class HttpReq {
    private static final String URL = "http://39.105.65.104:20080/addr/";
    private static RestTemplate restTemplate = new RestTemplate();

    public String addPrivateAddr(String id, String addr) {
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.APPLICATION_JSON);
        JSONObject jsonObject = new JSONObject();
        jsonObject.set(Constants.CLIENT_ID, id);
        jsonObject.set(Constants.CLIENT_PRIVATE_ADDRESS, addr);
        HttpEntity<JSONObject> httpEntity = new HttpEntity<>(jsonObject, httpHeaders);
        return restTemplate.postForObject(URL + "addPriAddr", httpEntity, String.class);
    }

    public String addPublicAddr(String id, String addr) {
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.APPLICATION_JSON);
        JSONObject jsonObject = new JSONObject();
        jsonObject.set(Constants.CLIENT_ID, id);
        jsonObject.set(Constants.CLIENT_PUBLIC_ADDRESS, addr);
        HttpEntity<JSONObject> httpEntity = new HttpEntity<>(jsonObject, httpHeaders);
        return restTemplate.postForObject(URL + "addPubAddr", httpEntity, String.class);
    }

    public String getPrivateAddr(String id) {
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.APPLICATION_JSON);
        JSONObject jsonObject = new JSONObject();
        jsonObject.set(Constants.CLIENT_ID, id);
        HttpEntity<JSONObject> httpEntity = new HttpEntity<>(jsonObject, httpHeaders);
        return restTemplate.postForObject(URL + "getPriAddr", httpEntity, String.class);
    }

    public String getPublicAddr(String id) {
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.APPLICATION_JSON);
        JSONObject jsonObject = new JSONObject();
        jsonObject.set(Constants.CLIENT_ID, id);
        HttpEntity<JSONObject> httpEntity = new HttpEntity<>(jsonObject, httpHeaders);
        return restTemplate.postForObject(URL + "getPubAddr", httpEntity, String.class);
    }

    public String delPrivateAddr(String id) {
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.APPLICATION_JSON);
        JSONObject jsonObject = new JSONObject();
        jsonObject.set(Constants.CLIENT_ID, id);
        HttpEntity<JSONObject> httpEntity = new HttpEntity<>(jsonObject, httpHeaders);
        return restTemplate.postForObject(URL + "delPriAddr", httpEntity, String.class);
    }

    public String delPublicAddr(String id) {
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.APPLICATION_JSON);
        JSONObject jsonObject = new JSONObject();
        jsonObject.set(Constants.CLIENT_ID, id);
        HttpEntity<JSONObject> httpEntity = new HttpEntity<>(jsonObject, httpHeaders);
        return restTemplate.postForObject(URL + "delPubAddr", httpEntity, String.class);
    }
}
