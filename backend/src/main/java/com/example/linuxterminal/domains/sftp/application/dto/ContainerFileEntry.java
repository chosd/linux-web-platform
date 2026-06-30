package com.example.linuxterminal.domains.sftp.application.dto;

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
