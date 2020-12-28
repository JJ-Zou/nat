package com.zjj.netty;

import java.util.Map;
import java.util.Set;

public interface IpAddrHolder extends NatThrough {

    Map<String, String> throughAddrMaps();

    String getPriAddrStr(String id);

    String getPubAddrStr(String id);

    void setPriAddrStr(String id, String addrStr);

    void setPubAddrStr(String id, String addrStr);

    void setThrough(String oppositeId, String addrStr);

    String getThrough(String oppositeId);

    boolean contains(String oppositeId);

    Set<String> getThroughIds();
}
