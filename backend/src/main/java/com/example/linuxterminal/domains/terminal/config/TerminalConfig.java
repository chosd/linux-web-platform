package com.example.linuxterminal.domains.terminal.config;

import com.example.linuxterminal.domains.container.application.port.in.ContainerService;
import com.example.linuxterminal.domains.terminal.adapter.in.websocket.TerminalWebSocketHandler;
import com.example.linuxterminal.domains.terminal.adapter.in.websocket.TerminalWebSocketSender;
import com.example.linuxterminal.domains.terminal.adapter.out.docker.DockerTerminalRuntime;
import com.example.linuxterminal.domains.terminal.adapter.out.memory.InMemoryTerminalSessionRepository;
import com.example.linuxterminal.domains.terminal.adapter.out.process.TerminalProcessWatcher;
import com.example.linuxterminal.domains.terminal.adapter.out.process.TerminalStreamForwarder;
import com.example.linuxterminal.domains.terminal.application.port.in.TerminalSessionService;
import com.example.linuxterminal.domains.terminal.application.port.out.TerminalRuntime;
import com.example.linuxterminal.domains.terminal.application.port.out.TerminalSessionRepository;
import com.example.linuxterminal.domains.terminal.application.port.out.WebSocketMessageSender;
import com.example.linuxterminal.domains.terminal.application.service.DefaultTerminalSessionService;
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
