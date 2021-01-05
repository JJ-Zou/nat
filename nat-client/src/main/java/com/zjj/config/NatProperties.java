package com.zjj.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

@Configuration
@ConfigurationProperties(prefix = "nat")
public class NatProperties {

    private Set<String> oppositeIds;

    public Set<String> getOppositeIds() {
        return oppositeIds;
    }

    public void setOppositeIds(Set<String> oppositeIds) {
        this.oppositeIds = oppositeIds;
    }
}
