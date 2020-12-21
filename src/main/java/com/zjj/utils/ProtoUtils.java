package com.zjj.utils;

import static com.zjj.proto.CtrlMessage.*;

public class ProtoUtils {
    private ProtoUtils() {
    }


    /**
     * 创建InetCommand
     *
     * @param clientId
     * @param host
     * @param port
     * @param publicInet
     * @return
     */
    public static InetCommand createInetCommand(String clientId, String host, int port, boolean publicInet) {
        return InetCommand.newBuilder()
                .setClientId(clientId)
                .setHost(host)
                .setPort(port)
                .setInetType(publicInet ? InetCommand.InetType.PUBLIC : InetCommand.InetType.PRIVATE)
                .build();
    }

    /**
     * 为InetCommand包装
     *
     * @param inetCommand
     * @return
     */
    public static MultiMessage createMultiFromInetCommand(InetCommand inetCommand) {
        return MultiMessage.newBuilder()
                .setMultiType(MultiMessage.MultiType.INET_COMMAND)
                .setInetCommand(inetCommand)
                .build();
    }


    /**
     * 创建InetCommand类型的MultiMessage
     *
     * @param clientId
     * @param host
     * @param port
     * @param publicInet
     * @return
     */
    public static MultiMessage createMultiInetCommand(String clientId, String host, int port, boolean publicInet) {
        return createMultiFromInetCommand(createInetCommand(clientId, host, port, publicInet));
    }

    /**
     * 创建ReqAddr
     *
     * @param id
     * @return
     */
    public static ReqAddr createReqAddr(String id) {
        return ReqAddr.newBuilder()
                .setId(id)
                .build();
    }

    /**
     * 为ReqAddr包装
     *
     * @param reqAddr
     * @return
     */
    public static MultiMessage createMultiFromReqAddr(ReqAddr reqAddr) {
        return MultiMessage.newBuilder()
                .setMultiType(MultiMessage.MultiType.REQ_ADDR)
                .setReqAddr(reqAddr)
                .build();
    }

    /**
     * 创建ReqAddr类型的MultiMessage
     *
     * @param id
     * @return
     */
    public static MultiMessage createMultiReqAddr(String id) {
        return createMultiFromReqAddr(createReqAddr(id));
    }

    /**
     * 创建Req
     *
     * @param from
     * @param to
     * @return
     */
    public static Req createReq(String from, String to) {
        return Req.newBuilder()
                .setFrom(from)
                .setTo(to)
                .build();
    }

    /**
     * 创建Req的包装
     *
     * @param req
     * @return
     */
    public static MultiMessage createMultiFromReq(Req req) {
        return MultiMessage.newBuilder()
                .setMultiType(MultiMessage.MultiType.REQ)
                .setReq(req)
                .build();
    }

    /**
     * 创建Req类型的MultiMessage
     *
     * @param from
     * @param to
     * @return
     */
    public static MultiMessage createMultiReq(String from, String to) {
        return createMultiFromReq(createReq(from, to));
    }

    /**
     * 创建Ack
     *
     * @param from
     * @param to
     * @return
     */
    public static Ack createAck(String from, String to) {
        return Ack.newBuilder()
                .setFrom(from)
                .setTo(to)
                .build();
    }

    /**
     * 创建Ack的包装
     *
     * @param ack
     * @return
     */
    public static MultiMessage createMultiFromAck(Ack ack) {
        return MultiMessage.newBuilder()
                .setMultiType(MultiMessage.MultiType.ACK)
                .setAck(ack)
                .build();
    }

    /**
     * 创建Ack类型的MultiMessage
     *
     * @param from
     * @param to
     * @return
     */
    public static MultiMessage createMultiAck(String from, String to) {
        return createMultiFromAck(createAck(from, to));
    }
}
