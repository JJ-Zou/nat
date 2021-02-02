package com.zjj.config;

import com.zjj.utils.InetUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "netty")
public class NettyProperties {

    @Autowired
    private ServerProperties serverProperties;

    @Autowired
    private ClientProperties clientProperties;

    public ServerProperties getServerProperties() {
        return serverProperties;
    }

    public void setServerProperties(ServerProperties serverProperties) {
        this.serverProperties = serverProperties;
    }

    public ClientProperties getClientProperties() {
        return clientProperties;
    }

    public void setClientProperties(ClientProperties clientProperties) {
        this.clientProperties = clientProperties;
    }

    @Configuration
    @ConfigurationProperties(prefix = "netty.server")
    public static class ServerProperties {
        private String ip;
        private int port;

        public String getIp() {
            return ip;
        }

        public void setIp(String ip) {
            this.ip = ip;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }
    }

    @Configuration
    @ConfigurationProperties(prefix = "netty.client")
    public static class ClientProperties {
        private String id;
        private String ip;
        private int port;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getIp() {
            if ("localhost".equals(ip)) {
                ip = InetUtils.getLocalAddress();
            }
            return ip;
        }

        public void setIp(String ip) {
            this.ip = ip;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }
    }

}
