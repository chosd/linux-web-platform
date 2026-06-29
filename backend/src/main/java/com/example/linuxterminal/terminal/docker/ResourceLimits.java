package com.example.linuxterminal.terminal.docker;

public record ResourceLimits(
        Double cpuCores,
        Integer memoryMb
) {
}
