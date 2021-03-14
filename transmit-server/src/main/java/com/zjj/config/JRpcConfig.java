package com.zjj.config;

import com.zjj.jrpc.config.ProtocolConfig;
import com.zjj.jrpc.config.RegistryConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JRpcConfig {

    @Bean("zookeeper")
    public RegistryConfig registryConfig() {
        RegistryConfig registryConfig = new RegistryConfig();
        registryConfig.setRegisterProtocol("zookeeper");
        registryConfig.setAddress("39.105.65.104");
        registryConfig.setPort(2181);
        return registryConfig;
    }

    @Bean("jrpc")
    public ProtocolConfig protocolConfig() {
        ProtocolConfig protocolConfig = new ProtocolConfig();
        protocolConfig.setProtocolName("jrpcc");
        return protocolConfig;
    }
}
