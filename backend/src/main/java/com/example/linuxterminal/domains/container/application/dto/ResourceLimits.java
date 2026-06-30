package com.example.linuxterminal.domains.container.application.dto;

public record ResourceLimits(
        Double cpuCores,
        Integer memoryMb
) {
}
