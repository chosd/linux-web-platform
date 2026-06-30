package com.example.linuxterminal.domains.network.dto;

import java.util.List;

public record ContainerNetworkDashboardResponse(
        String containerName,
        List<ContainerNetworkResponse> networks,
        List<PortMappingResponse> ports
) {
}
