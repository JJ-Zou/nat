package com.zjj.old;

import com.zjj.utils.InetUtils;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.protobuf.ProtobufDecoder;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.zjj.proto.CtrlMessage.MultiMessage;

@Slf4j
@ChannelHandler.Sharable
public class DatagramPacketDecoder extends ChannelDuplexHandler {
    private static final Map<Integer, Channel> CHANNEL_MAP = new ConcurrentHashMap<>();

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        InetSocketAddress localAddress = (InetSocketAddress) ctx.channel().localAddress();
        if (log.isInfoEnabled()) {
            log.info("服务端绑定成功！{}", InetUtils.toAddressString(localAddress));
        }
        CHANNEL_MAP.put(localAddress.getPort(), ctx.channel());
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (!(msg instanceof DatagramPacket)) {
            log.error("非UDP包");
            if (log.isInfoEnabled()) {
                log.info("msg: {}", msg);
            }
            return;
        }
        DatagramPacket packet = (DatagramPacket) msg;
        String addressString = InetUtils.toAddressString(packet.sender());
        Channel channel = ctx.channel();
        channel.pipeline()
                .addLast(new ProtobufDecoder(MultiMessage.getDefaultInstance()))
                .addLast(new MultiMessageHandler(addressString))
        ;
        ctx.fireChannelRead(packet.content());
    }

}
