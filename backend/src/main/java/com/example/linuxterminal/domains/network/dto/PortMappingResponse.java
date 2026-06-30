package com.example.linuxterminal.domains.network.dto;

public record PortMappingResponse(
        Integer hostPort,
        Integer containerPort,
        PortBinding.Protocol protocol,
        String hostIp,
        String url
) {
}
