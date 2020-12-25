package com.zjj.client;

public interface NatThrough {

    void attemptNatConnect(NettyClient nettyClient, String oppositeId);

}
