package com.example.linuxterminal.domains.terminal.service;

import com.example.linuxterminal.domains.terminal.service.WebSocketMessageSender;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

@Slf4j
public class TerminalWebSocketSender implements WebSocketMessageSender {

    @Override
    public boolean sendText(WebSocketSession session, String payload) {
        synchronized (session) {
            if (!session.isOpen()) {
                return false;
            }
            try {
                session.sendMessage(new TextMessage(payload));
                return true;
            } catch (IOException exception) {
                log.warn("Failed to send terminal websocket message. sessionId={}", session.getId(), exception);
                return false;
            }
        }
    }
}
