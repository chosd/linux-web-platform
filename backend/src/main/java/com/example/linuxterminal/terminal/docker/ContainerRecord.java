package com.example.linuxterminal.terminal.docker;

import java.time.Instant;

public record ContainerRecord(
        String userId,
        String containerName,
        String displayName,
        Instant createdAt
) {
}
