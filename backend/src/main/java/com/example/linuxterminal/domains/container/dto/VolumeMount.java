package com.example.linuxterminal.domains.container.dto;

import jakarta.validation.constraints.NotBlank;

public record VolumeMount(
        @NotBlank String hostPath,
        @NotBlank String containerPath
) {
}
