package com.example.linuxterminal.domains.sftp.config;

import com.example.linuxterminal.domains.container.service.ContainerService;
import com.example.linuxterminal.domains.sftp.service.DockerContainerFileServiceImpl;
import com.example.linuxterminal.domains.sftp.service.ContainerFileService;
import com.example.linuxterminal.global.docker.DockerCommandFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SftpConfig {

    @Bean
    ContainerFileService containerFileService(
            DockerCommandFactory dockerCommandFactory,
            ContainerService containerService
    ) {
        return new DockerContainerFileServiceImpl(dockerCommandFactory, containerService);
    }
}
