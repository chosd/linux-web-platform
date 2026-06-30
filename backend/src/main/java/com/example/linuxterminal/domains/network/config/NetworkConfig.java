package com.example.linuxterminal.domains.network.config;

import com.example.linuxterminal.domains.network.service.DockerNetworkService;
import com.example.linuxterminal.domains.network.service.DockerNetworkServiceImpl;
import com.example.linuxterminal.global.docker.DockerCommandFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NetworkConfig {

    @Bean
    DockerNetworkService dockerNetworkService(DockerCommandFactory dockerCommandFactory, ObjectMapper objectMapper) {
        return new DockerNetworkServiceImpl(dockerCommandFactory, objectMapper);
    }
}
