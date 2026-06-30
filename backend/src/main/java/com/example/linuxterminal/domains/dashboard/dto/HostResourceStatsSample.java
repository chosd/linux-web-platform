package com.example.linuxterminal.domains.dashboard.dto;

import java.time.Instant;

public record HostResourceStatsSample(
        Instant timestamp,
        double cpuPercent,
        double memoryUsageMb,
        double memoryTotalMb,
        double memoryPercent
) {
}
