package com.example.linuxterminal.domains.container.config;

import com.example.linuxterminal.domains.container.adapter.out.docker.ContainerCleanUpScheduler;
import com.example.linuxterminal.domains.container.adapter.out.docker.ContainerNameGenerator;
import com.example.linuxterminal.domains.container.adapter.out.docker.ContainerStatsService;
import com.example.linuxterminal.domains.container.adapter.out.docker.DockerContainerServiceImpl;
import com.example.linuxterminal.domains.container.adapter.out.persistence.FileContainerMetadataRepository;
import com.example.linuxterminal.domains.container.application.port.in.ContainerService;
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
            FileContainerMetadataRepository metadataRepository
    ) {
        return new DockerContainerServiceImpl(dockerCommandFactory, containerNameGenerator, metadataRepository);
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
