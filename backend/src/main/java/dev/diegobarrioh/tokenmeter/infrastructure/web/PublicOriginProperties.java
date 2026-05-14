package dev.diegobarrioh.tokenmeter.infrastructure.web;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tokenmeter")
public record PublicOriginProperties(String publicOrigin) {}
