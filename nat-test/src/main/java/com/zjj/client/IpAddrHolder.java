package com.zjj.client;

import java.util.Set;

public interface IpAddrHolder extends NatThrough {

    String getPriAddrStr(String id);

    String getPubAddrStr(String id);

    void setPriAddrStr(String id, String addrStr);

    void setPubAddrStr(String id, String addrStr);

    void setThrough(String oppositeId, String addrStr);

    String getThrough(String oppositeId);

    boolean contains(String oppositeId);

    Set<String> getThroughIds();
}
