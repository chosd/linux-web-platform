package com.example.linuxterminal.terminal.docker;

import com.example.linuxterminal.terminal.config.TerminalProperties;
import java.security.SecureRandom;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

@Component
public class ContainerNameGenerator {

    private static final int CONTAINER_SUFFIX_BYTES = 6;

    private final TerminalProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public ContainerNameGenerator(TerminalProperties properties) {
        this.properties = properties;
    }

    public String create(String webSocketSessionId) {
        byte[] randomBytes = new byte[CONTAINER_SUFFIX_BYTES];
        secureRandom.nextBytes(randomBytes);
        String randomSuffix = HexFormat.of().formatHex(randomBytes);
        String sanitized = webSocketSessionId == null ? "session" : webSocketSessionId
                .toLowerCase()
                .replaceAll("[^a-z0-9_.-]", "-")
                .replaceAll("^[^a-z0-9]+", "")
                .replaceAll("[^a-z0-9]+$", "");
        if (sanitized.isBlank()) {
            sanitized = "session";
        }
        int maxSessionIdLength = properties.getDocker().getContainerNameSessionIdLength();
        if (sanitized.length() > maxSessionIdLength) {
            sanitized = sanitized.substring(0, maxSessionIdLength);
        }
        return properties.getDocker().getContainerNamePrefix() + "-" + sanitized + "-" + randomSuffix;
    }
}

