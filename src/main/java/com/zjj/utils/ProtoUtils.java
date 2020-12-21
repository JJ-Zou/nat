package com.zjj.utils;

import static com.zjj.proto.CtrlMessage.InetCommand;
import static com.zjj.proto.CtrlMessage.MultiMessage;

public class ProtoUtils {
    private ProtoUtils() {
    }

    public static MultiMessage createMultiMessageFromInetCommand(InetCommand inetCommand) {
        return MultiMessage.newBuilder()
                .setMultiType(MultiMessage.MultiType.INET_COMMAND)
                .setInetCommand(inetCommand)
                .build();
    }

    public static InetCommand createInetCommand(String clientId, String host, int port, boolean publicInet) {
        return InetCommand.newBuilder()
                .setClientId(clientId)
                .setHost(host)
                .setPort(port)
                .setInetType(publicInet ? InetCommand.InetType.PUBLIC : InetCommand.InetType.PRIVATE)
                .build();
    }

    public static MultiMessage createMultiInetCommand(String clientId, String host, int port, boolean publicInet) {
        return createMultiMessageFromInetCommand(
                InetCommand.newBuilder()
                        .setClientId(clientId)
                        .setHost(host)
                        .setPort(port)
                        .setInetType(publicInet ? InetCommand.InetType.PUBLIC : InetCommand.InetType.PRIVATE)
                        .build());
    }
}
