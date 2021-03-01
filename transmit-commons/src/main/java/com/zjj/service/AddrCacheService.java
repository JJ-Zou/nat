package com.zjj.service;


public interface AddrCacheService {
    void addPrivateAddrStr(String id, String addrStr);

    void addPublicAddrStr(String id, String addrStr);

    String getPrivateAddrStr(String id);

    String getPublicAddrStr(String id);

    Boolean deletePrivateAddr(String id);

    Boolean deletePublicAddr(String id);
}
