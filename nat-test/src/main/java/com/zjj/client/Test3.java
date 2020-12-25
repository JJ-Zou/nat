package com.zjj.client;

import lombok.SneakyThrows;

public class Test3 {
    @SneakyThrows
    public static void main(String[] args) {
        ClientStarter starter = new ClientStarter();
        starter.startClient();
    }
}
