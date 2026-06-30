package com.example.linuxterminal.terminal.docker;

import java.time.Instant;

public record ContainerFileEntry(
        String name,
        FileType type,
        long size,
        Instant lastModified
) {
    public enum FileType {
        DIRECTORY,
        FILE
    }
}
