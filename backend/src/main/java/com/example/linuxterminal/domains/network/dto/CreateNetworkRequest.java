package com.example.linuxterminal.domains.network.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateNetworkRequest(
        @NotBlank String name
) {
}
