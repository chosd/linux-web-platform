package com.example.linuxterminal.domains.container.dto;

import jakarta.validation.constraints.NotBlank;

public record ResolvedVolumeMount(
        @NotBlank String hostPath,
        @NotBlank String containerPath,
        VolumeMount.AccessMode accessMode
) {
    public ResolvedVolumeMount {
        if (accessMode == null) {
            accessMode = VolumeMount.AccessMode.READ_WRITE;
        }
    }
}
