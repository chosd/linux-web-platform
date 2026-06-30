package com.example.linuxterminal.domains.network.dto;

public record ContainerNetworkResponse(
        String name,
        String networkId,
        String ipAddress,
        String gateway,
        String macAddress
) {
}
