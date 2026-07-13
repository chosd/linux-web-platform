package com.example.linuxterminal.domains.container.domain;

import java.time.Instant;

public record ContainerRecord(
        String userId,
        String containerName,
        String displayName,
        Instant createdAt,
        Double cpuCores,
        Integer memoryMb,
        String imageName
) {
}
