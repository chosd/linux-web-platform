package com.example.linuxterminal.domains.container.application.dto;

import java.time.Instant;

public record ContainerStatsSample(
        Instant timestamp,
        double cpuPercent,
        double memoryUsageMb,
        double memoryLimitMb,
        double networkRxMb,
        double networkTxMb,
        double blockReadMb,
        double blockWriteMb
) {
}
