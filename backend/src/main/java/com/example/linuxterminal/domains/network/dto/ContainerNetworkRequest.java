package com.example.linuxterminal.domains.network.dto;

import jakarta.validation.constraints.NotBlank;

public record ContainerNetworkRequest(
        @NotBlank String networkName
) {
}
