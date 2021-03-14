package com.zjj;

import com.zjj.jrpc.config.spring.annotation.EnableJRpc;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableJRpc
public class NatServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(NatServerApplication.class, args);
    }
}

