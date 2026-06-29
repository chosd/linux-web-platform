package com.example.linuxterminal.terminal.docker;

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
