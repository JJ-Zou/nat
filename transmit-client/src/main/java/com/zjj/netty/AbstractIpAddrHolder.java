package com.zjj.netty;

import com.zjj.constant.Constants;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

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
    public void setThrough(String oppositeId) {
        setThrough(oppositeId, Constants.NONE);
    }

    @Override
    public void setAllThrough(Set<String> oppositeIds) {
        Map<String, String> collect = oppositeIds.stream().filter(s -> s != null && !s.equals("")).collect(Collectors.toMap(oppositeId -> oppositeId, s -> Constants.NONE));
        throughHolder.putAll(collect);
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
