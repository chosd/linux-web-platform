package com.example.linuxterminal.global.config;

import com.example.linuxterminal.global.docker.DockerCommandExecutor;
import com.example.linuxterminal.global.docker.DockerCommandFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DockerConfig {

    @Bean
    DockerCommandFactory dockerCommandFactory(TerminalProperties terminalProperties) {
        return new DockerCommandFactory(terminalProperties);
    }

    @Bean
    DockerCommandExecutor dockerCommandExecutor() {
        return new DockerCommandExecutor();
    }
}
