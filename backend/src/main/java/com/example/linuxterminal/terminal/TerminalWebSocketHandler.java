package com.example.linuxterminal.terminal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class TerminalWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(TerminalWebSocketHandler.class);

    private final TerminalSessionManager terminalSessionManager;

    public TerminalWebSocketHandler(TerminalSessionManager terminalSessionManager) {
        this.terminalSessionManager = terminalSessionManager;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        terminalSessionManager.create(session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        terminalSessionManager.write(session.getId(), message.getPayload());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("Terminal websocket transport error. sessionId={}", session.getId(), exception);
        terminalSessionManager.close(session.getId(), "transport error");
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        terminalSessionManager.close(session.getId(), "websocket closed: " + status);
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }
}
