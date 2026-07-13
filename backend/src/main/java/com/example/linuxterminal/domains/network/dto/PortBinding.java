package com.example.linuxterminal.domains.network.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record PortBinding(
        @NotNull @Min(1024) @Max(65535) Integer hostPort,
        @NotNull @Min(1) @Max(65535) Integer containerPort,
        Protocol protocol
) {
    public PortBinding {
        protocol = protocol == null ? Protocol.TCP : protocol;
    }

    public enum Protocol {
        TCP,
        UDP
    }
}
