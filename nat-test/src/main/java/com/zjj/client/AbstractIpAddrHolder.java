package com.zjj.client;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public abstract class AbstractIpAddrHolder implements IpAddrHolder {
    private final Map<String, String> throughHolder;

    public AbstractIpAddrHolder() {
        this.throughHolder = new ConcurrentHashMap<>();
    }


    public Map<String, String> throughAddrMaps() {
        return Collections.unmodifiableMap(throughHolder);
    }

    @Override
    public Set<String> getThroughIds() {
        return Collections.unmodifiableSet(throughHolder.keySet());
    }

    @Override
    public void setThrough(String oppositeId, String addrStr) {
        throughHolder.put(oppositeId, addrStr);
    }

    @Override
    public String getThrough(String oppositeId) {
        return throughHolder.get(oppositeId);
    }

    @Override
    public boolean contains(String oppositeId) {
        return throughHolder.containsKey(oppositeId);
    }
}
