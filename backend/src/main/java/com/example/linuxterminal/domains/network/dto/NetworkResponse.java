package com.example.linuxterminal.domains.network.dto;

public record NetworkResponse(
        String name,
        String id,
        String driver,
        String scope
) {
}
