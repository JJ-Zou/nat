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
                .setInetType(publicInet ? InetType.PUBLIC : InetType.PRIVATE)
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
     * 创建Syn
     *
     * @param from
     * @param to
     * @return
     */
    public static Syn createSyn(String from, String to) {
        return Syn.newBuilder()
                .setFrom(from)
                .setTo(to)
                .build();
    }

    /**
     * 创建Syn的包装
     *
     * @param syn
     * @return
     */
    public static MultiMessage createMultiFromSyn(Syn syn) {
        return MultiMessage.newBuilder()
                .setMultiType(MultiMessage.MultiType.SYN)
                .setSyn(syn)
                .build();
    }

    /**
     * 创建Syn类型的MultiMessage
     *
     * @param from
     * @param to
     * @return
     */
    public static MultiMessage createMultiSyn(String from, String to) {
        return createMultiFromSyn(createSyn(from, to));
    }


    /**
     * 创建SynAck
     *
     * @param from
     * @param to
     * @return
     */
    public static SynAck createSynAck(String from, String to) {
        return SynAck.newBuilder()
                .setFrom(from)
                .setTo(to)
                .build();
    }

    /**
     * 创建SynAck的包装
     *
     * @param synAck
     * @return
     */
    public static MultiMessage createMultiFromSynAck(SynAck synAck) {
        return MultiMessage.newBuilder()
                .setMultiType(MultiMessage.MultiType.SYN_ACK)
                .setSynAck(synAck)
                .build();
    }

    /**
     * 创建SynAck类型的MultiMessage
     *
     * @param from
     * @param to
     * @return
     */
    public static MultiMessage createMultiSynAck(String from, String to) {
        return createMultiFromSynAck(createSynAck(from, to));
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

    /**
     * 创建ReqRedirect
     *
     * @param from
     * @param to
     * @param fromAddr
     * @return
     */
    public static ReqRedirect createReqRedirect(String from, String to, String fromAddr) {
        return ReqRedirect.newBuilder()
                .setFrom(from)
                .setTo(to)
                .setFromAddr(fromAddr)
                .build();
    }

    /**
     * 创建ReqRedirect的包装
     *
     * @param reqRedirect
     * @return
     */
    public static MultiMessage createMultiFromReqRedirect(ReqRedirect reqRedirect) {
        return MultiMessage.newBuilder()
                .setMultiType(MultiMessage.MultiType.REQ_REDIRECT)
                .setReqRedirect(reqRedirect)
                .build();
    }

    /**
     * 创建MultiMessaged类型的ReqRedirect
     *
     * @param from
     * @param to
     * @param fromAddr
     * @return
     */
    public static MultiMessage createMultiReqRedirect(String from, String to, String fromAddr) {
        return createMultiFromReqRedirect(createReqRedirect(from, to, fromAddr));
    }
}
