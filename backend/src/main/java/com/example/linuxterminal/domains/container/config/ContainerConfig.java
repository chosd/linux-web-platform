package com.example.linuxterminal.domains.container.config;

import com.example.linuxterminal.domains.container.service.ContainerCleanUpScheduler;
import com.example.linuxterminal.domains.container.service.ContainerNameGenerator;
import com.example.linuxterminal.domains.container.service.ContainerStatsService;
import com.example.linuxterminal.domains.container.service.DockerContainerServiceImpl;
import com.example.linuxterminal.domains.container.repository.FileContainerMetadataRepository;
import com.example.linuxterminal.domains.container.service.ContainerService;
import com.example.linuxterminal.domains.network.service.DockerNetworkService;
import com.example.linuxterminal.global.config.TerminalProperties;
import com.example.linuxterminal.global.docker.DockerCommandFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ContainerConfig {

    @Bean
    FileContainerMetadataRepository fileContainerMetadataRepository(ObjectMapper objectMapper) {
        return new FileContainerMetadataRepository(objectMapper);
    }

    @Bean
    ContainerNameGenerator containerNameGenerator(TerminalProperties terminalProperties) {
        return new ContainerNameGenerator(terminalProperties);
    }

    @Bean
    ContainerService containerService(
            DockerCommandFactory dockerCommandFactory,
            ContainerNameGenerator containerNameGenerator,
            FileContainerMetadataRepository metadataRepository,
            DockerNetworkService dockerNetworkService
    ) {
        return new DockerContainerServiceImpl(
                dockerCommandFactory,
                containerNameGenerator,
                metadataRepository,
                dockerNetworkService);
    }

    @Bean
    ContainerStatsService containerStatsService(
            DockerCommandFactory dockerCommandFactory,
            ContainerService containerService,
            ObjectMapper objectMapper
    ) {
        return new ContainerStatsService(dockerCommandFactory, containerService, objectMapper);
    }

    @Bean
    ContainerCleanUpScheduler containerCleanUpScheduler(
            ContainerService containerService,
            TerminalProperties terminalProperties
    ) {
        return new ContainerCleanUpScheduler(containerService, terminalProperties);
    }
}
