package com.example.linuxterminal.domains.terminal.config;

import com.example.linuxterminal.domains.container.service.ContainerService;
import com.example.linuxterminal.domains.terminal.controller.TerminalWebSocketHandler;
import com.example.linuxterminal.domains.terminal.service.DockerTerminalRuntime;
import com.example.linuxterminal.domains.terminal.repository.InMemoryTerminalSessionRepository;
import com.example.linuxterminal.domains.terminal.repository.TerminalSessionRepository;
import com.example.linuxterminal.domains.terminal.service.TerminalProcessWatcher;
import com.example.linuxterminal.domains.terminal.service.TerminalStreamForwarder;
import com.example.linuxterminal.domains.terminal.service.TerminalSessionService;
import com.example.linuxterminal.domains.terminal.service.TerminalRuntime;
import com.example.linuxterminal.domains.terminal.service.TerminalWebSocketSender;
import com.example.linuxterminal.domains.terminal.service.WebSocketMessageSender;
import com.example.linuxterminal.domains.terminal.service.DefaultTerminalSessionService;
import com.example.linuxterminal.global.config.TerminalProperties;
import com.example.linuxterminal.global.docker.DockerCommandFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TerminalConfig {

    @Bean
    WebSocketMessageSender webSocketMessageSender() {
        return new TerminalWebSocketSender();
    }

    @Bean
    TerminalStreamForwarder terminalStreamForwarder(WebSocketMessageSender webSocketMessageSender) {
        return new TerminalStreamForwarder(webSocketMessageSender);
    }

    @Bean
    TerminalProcessWatcher terminalProcessWatcher() {
        return new TerminalProcessWatcher();
    }

    @Bean
    TerminalSessionRepository terminalSessionRepository() {
        return new InMemoryTerminalSessionRepository();
    }

    @Bean
    TerminalRuntime terminalRuntime(
            DockerCommandFactory dockerCommandFactory,
            ContainerService containerService,
            TerminalStreamForwarder terminalStreamForwarder,
            TerminalProcessWatcher terminalProcessWatcher
    ) {
        return new DockerTerminalRuntime(
                dockerCommandFactory,
                containerService,
                terminalStreamForwarder,
                terminalProcessWatcher);
    }

    @Bean
    TerminalSessionService terminalSessionService(
            TerminalSessionRepository terminalSessionRepository,
            TerminalRuntime terminalRuntime,
            TerminalProperties terminalProperties,
            ContainerService containerService
    ) {
        return new DefaultTerminalSessionService(
                terminalSessionRepository,
                terminalRuntime,
                terminalProperties,
                containerService);
    }

    @Bean
    TerminalWebSocketHandler terminalWebSocketHandler(TerminalSessionService terminalSessionService) {
        return new TerminalWebSocketHandler(terminalSessionService);
    }
}
