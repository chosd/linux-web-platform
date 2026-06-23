package com.example.linuxterminal.terminal.docker;

import com.example.linuxterminal.terminal.config.TerminalProperties;
import org.springframework.stereotype.Component;

@Component
public class ContainerNameGenerator {

    private final TerminalProperties properties;

    public ContainerNameGenerator(TerminalProperties properties) {
        this.properties = properties;
    }

    public String forUser(String userId) {
        String sanitized = sanitize(userId, "anonymous");
        int maxUserIdLength = properties.getDocker().getContainerNameSessionIdLength();
        if (sanitized.length() > maxUserIdLength) {
            sanitized = sanitized.substring(0, maxUserIdLength);
        }
        return sanitized + "_container";
    }

    private String sanitize(String value, String fallback) {
        String sanitized = value == null ? fallback : value
                .toLowerCase()
                .replaceAll("[^a-z0-9_.-]", "-")
                .replaceAll("^[^a-z0-9]+", "")
                .replaceAll("[^a-z0-9]+$", "");
        if (sanitized.isBlank()) {
            return fallback;
        }
        return sanitized;
    }
}
