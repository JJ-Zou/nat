package com.zjj.client;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public abstract class AbstractIpAddrHolder implements IpAddrHolder {
    private Map<String, String> throughHolder;

    public AbstractIpAddrHolder() {
        this.throughHolder = new ConcurrentHashMap<>();
    }


    public Map<String, String> throughAddrMaps() {
        return Collections.unmodifiableMap(throughHolder);
    }

    @Override
    public void setThrough(String oppositeId, String addrStr) {
        throughHolder.put(oppositeId, addrStr);
    }

    @Override
    public String getThrough(String oppositeId) {
        return throughHolder.get(oppositeId);
    }
}
