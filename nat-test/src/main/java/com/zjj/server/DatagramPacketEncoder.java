package com.zjj.server;

import com.zjj.utils.InetUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.MessageToMessageEncoder;

import java.util.List;

import static com.zjj.proto.CtrlMessage.MultiMessage;

public class DatagramPacketEncoder extends MessageToMessageEncoder<MultiMessage> {
    @Override
    protected void encode(ChannelHandlerContext ctx, MultiMessage msg, List<Object> out) throws Exception {
        ByteBuf byteBuf = Unpooled.wrappedBuffer(msg.toByteArray());
        DatagramPacket packet = new DatagramPacket(byteBuf, InetUtils.toInetSocketAddress("127.0.0.1:10004"));
        out.add(packet);
    }
}
