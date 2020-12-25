package com.zjj.client;

public interface IpAddrHolder extends NatThrough {

    String getPriAddrStr(String id);

    String getPubAddrStr(String id);

    void setPriAddrStr(String id, String addrStr);

    void setPubAddrStr(String id, String addrStr);

    void setThrough(String oppositeId, String addrStr);

    String getThrough(String oppositeId);
}
