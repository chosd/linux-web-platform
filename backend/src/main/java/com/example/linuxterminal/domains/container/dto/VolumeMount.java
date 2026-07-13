package com.example.linuxterminal.domains.container.dto;

import jakarta.validation.constraints.NotBlank;

public record VolumeMount(
        String hostPath,
        String volumeName,
        @NotBlank String containerPath,
        AccessMode accessMode
) {
    public VolumeMount {
        if (accessMode == null) {
            accessMode = AccessMode.READ_WRITE;
        }
    }

    public String sourcePath() {
        if (hostPath != null && !hostPath.isBlank()) {
            return hostPath;
        }
        return volumeName;
    }

    public enum AccessMode {
        READ_WRITE,
        READ_ONLY
    }
}
