package com.zjj.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "nat")
public class NatProperties {

    private List<String> oppositeIds;

    public List<String> getOppositeIds() {
        return oppositeIds;
    }

    public void setOppositeIds(List<String> oppositeIds) {
        this.oppositeIds = oppositeIds;
    }
}
