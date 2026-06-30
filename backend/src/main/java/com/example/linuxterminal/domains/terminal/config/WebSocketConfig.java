package com.example.linuxterminal.domains.terminal.config;

import com.example.linuxterminal.domains.terminal.adapter.in.websocket.TerminalWebSocketHandler;
import com.example.linuxterminal.global.config.TerminalProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final TerminalWebSocketHandler terminalWebSocketHandler;
    private final TerminalProperties terminalProperties;

    public WebSocketConfig(TerminalWebSocketHandler terminalWebSocketHandler, TerminalProperties terminalProperties) {
        this.terminalWebSocketHandler = terminalWebSocketHandler;
        this.terminalProperties = terminalProperties;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(terminalWebSocketHandler, "/ws/terminal")
                .setAllowedOrigins(terminalProperties.getAllowedOrigins().toArray(String[]::new));
    }
}
