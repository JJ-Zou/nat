package com.zjj.netty;

public interface NatThrough {

    void attemptNatConnect(NettyClient nettyClient, String oppositeId);

}
