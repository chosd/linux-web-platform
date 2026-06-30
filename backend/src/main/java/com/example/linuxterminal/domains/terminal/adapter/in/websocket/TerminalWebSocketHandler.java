package com.example.linuxterminal.domains.terminal.adapter.in.websocket;

import com.example.linuxterminal.domains.terminal.application.port.in.TerminalSessionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Slf4j
public class TerminalWebSocketHandler extends TextWebSocketHandler {

    private final TerminalSessionService terminalSessionService;

    public TerminalWebSocketHandler(TerminalSessionService terminalSessionService) {
        this.terminalSessionService = terminalSessionService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        terminalSessionService.create(session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        terminalSessionService.write(session.getId(), message.getPayload());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("Terminal websocket transport error. sessionId={}", session.getId(), exception);
        terminalSessionService.close(session.getId(), "transport error");
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        terminalSessionService.close(session.getId(), "websocket closed: " + status);
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }
}
