package com.example.linuxterminal.domains.network.dto;

public record ContainerNetworkOptions(
        String networkName,
        String networkAlias
) {
    public boolean hasNetwork() {
        return networkName != null && !networkName.isBlank();
    }
}
