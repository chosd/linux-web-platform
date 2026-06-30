package com.example.linuxterminal.domains.sftp.config;

import com.example.linuxterminal.domains.container.application.port.in.ContainerService;
import com.example.linuxterminal.domains.sftp.adapter.out.docker.DockerContainerFileServiceImpl;
import com.example.linuxterminal.domains.sftp.application.port.in.ContainerFileService;
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
